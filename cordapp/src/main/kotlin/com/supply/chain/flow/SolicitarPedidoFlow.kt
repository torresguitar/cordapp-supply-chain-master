package com.supply.chain.flow

import co.paralleluniverse.fibers.Suspendable
import com.supply.chain.contract.PedidoContract
import com.supply.chain.contract.ProdutoContract
import com.supply.chain.state.Pedido
import com.supply.chain.state.Produto
import com.supply.chain.state.TipoProduto
import net.corda.core.contracts.Command
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

object SolicitarPedidoFlow {

    @StartableByRPC
    @InitiatingFlow
    class Initiator(val produtor: Party,
                    val tipoProduto: TipoProduto,
                    val quantidadeProduto: Int): FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val outputState = Pedido(ourIdentity, produtor, tipoProduto, quantidadeProduto)
            val comando = Command(PedidoContract.Commands.Solicitar(),
                    outputState.participants.map { it.owningKey })

            val builder = TransactionBuilder(notary)
                    .addCommand(comando)
                    .addOutputState(outputState, PedidoContract::class.java.canonicalName)

            builder.verify(serviceHub)

            val partSignedTx = serviceHub.signInitialTransaction(builder)

            val signedTx = subFlow(CollectSignaturesFlow(partSignedTx, listOf(initiateFlow(produtor))))

            return subFlow(FinalityFlow(signedTx))
        }

    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherParty: FlowSession): FlowLogic<SignedTransaction>(){

        companion object {
            fun getSignTransactionFlow(otherParty: FlowSession): FlowLogic<SignedTransaction> {
                return object : SignTransactionFlow(otherParty) {

                    fun validarDistribuidor() {
                        requireThat {
                            "Apenas a Loja pode fazer o pedidos para o Distribuidor." using
                                    (otherParty.counterparty.name.organisation == "Loja")
                        }
                    }

                    fun validarProdutor() {
                        requireThat {
                            "Apenas o Distribuidor pode fazer o pedidos para o Produtor." using
                                    (otherParty.counterparty.name.organisation == "Distribuidor")
                        }
                    }

                    override fun checkTransaction(stx: SignedTransaction) {
                        when(ourIdentity.name.organisation) {
                            "Produtor" -> validarProdutor()
                            "Distribuidor" -> validarDistribuidor()
                            else -> throw FlowException()
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