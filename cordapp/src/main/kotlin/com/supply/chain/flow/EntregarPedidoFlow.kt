package com.supply.chain.flow

import co.paralleluniverse.fibers.Suspendable
import com.supply.chain.contract.PedidoContract
import com.supply.chain.contract.ProdutoContract
import com.supply.chain.state.Pedido
import com.supply.chain.state.Produto
import com.supply.chain.state.StatusPedido
import net.corda.core.contracts.Command
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

object EntregarPedidoFlow {

    @StartableByRPC
    @InitiatingFlow
    class Initiator(val idPedido: UniqueIdentifier,
                    val idsProduto: List<UniqueIdentifier>,
                    val destino: Party) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {

            val pedido = serviceHub.vaultService.queryBy<Pedido>(
                    QueryCriteria.LinearStateQueryCriteria(linearId = listOf(idPedido))
            ).states.single()

            val produtos = serviceHub.vaultService.queryBy<Produto>(
                    QueryCriteria.LinearStateQueryCriteria(linearId = idsProduto)
            ).states

            requireThat {
                "Todos os States devem estar no mesmo notary." using (
                        produtos.all { it.state.notary == pedido.state.notary }
                        )
                "A quantidade de produtos deve ser igual ao especificado no Pedido." using (
                        pedido.state.data.quantidadeProduto == produtos.size
                        )
                "O tipo dos produtos devem ser iguais ao especificado no Pedido." using (
                        produtos.all { it.state.data.tipoProduto == pedido.state.data.tipoProduto }
                        )
            }

            val notary = pedido.state.notary

            val outrosParticipantes = pedido.state.data.participants
                    .filter { it.owningKey != ourIdentity.owningKey }
                    .mapNotNull {
                        serviceHub
                                .identityService
                                .wellKnownPartyFromAnonymous(it)
                    }

            val produtor = produtos.first().state.data.produtor

            val comando = Command(PedidoContract.Commands.Entregar(),
                    outrosParticipantes.map { it.owningKey } + produtor.owningKey)

            val outputPedido = pedido.state.data.copy(statusPedido = StatusPedido.Entregue)
            val outputsProduto = produtos.map { it.state.data.copy(dono = destino) }

            val builder = TransactionBuilder(notary)
                    .addCommand(comando)
                    .addInputState(pedido)
                    .addOutputState(outputPedido, PedidoContract::class.java.canonicalName)

            produtos.forEach {
                builder.addInputState(it)
            }

            outputsProduto.forEach {
                builder.addOutputState(it,
                        ProdutoContract::class.java.canonicalName)
            }

            builder.verify(serviceHub)

            val partSignedTx = serviceHub.signInitialTransaction(builder)

            val sessoes = (outrosParticipantes + produtor).toSet()
                    .map { initiateFlow(it) }

            val signedTx = subFlow(CollectSignaturesFlow(partSignedTx, sessoes))

            return subFlow(FinalityFlow(signedTx))
        }

    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherParty: FlowSession) : FlowLogic<SignedTransaction>() {
        companion object {
            fun getSignTransactionFlow(otherParty: FlowSession): FlowLogic<SignedTransaction> {
                return object : SignTransactionFlow(otherParty) {
                    override fun checkTransaction(stx: SignedTransaction) {
                        requireThat {
                            if (stx.coreTransaction.outputsOfType<Produto>().any {  it.dono != ourIdentity }) {
                                "Apenas a Transportadora pode realizar envios." using (
                                        otherParty.counterparty.name.organisation == "Transportadora"
                                        )
                            }
                        }
                    }
                }
            }
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val signFlow = getSignTransactionFlow(otherParty)

            return subFlow(signFlow)
        }
    }
}