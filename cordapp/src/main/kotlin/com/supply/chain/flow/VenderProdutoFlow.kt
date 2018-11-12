package com.supply.chain.flow

import co.paralleluniverse.fibers.Suspendable
import com.supply.chain.contract.ProdutoContract
import com.supply.chain.state.Produto
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

object VenderProdutoFlow {

    @StartableByRPC
    @InitiatingFlow
    class Initiator(val idProduto: UniqueIdentifier): FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val produto = serviceHub.vaultService.queryBy<Produto>(
                    QueryCriteria.LinearStateQueryCriteria(linearId = listOf(idProduto))
            ).states.single()

            val notary = produto.state.notary

            val produtor = produto.state.data.produtor

            val comando = Command(ProdutoContract.Commands.Vender(),
                    listOf(ourIdentity.owningKey, produtor.owningKey))

            val builder = TransactionBuilder(notary)
                    .addCommand(comando)
                    .addInputState(produto)

            builder.verify(serviceHub)

            val partSignedTx = serviceHub.signInitialTransaction(builder)

            val signedTx = subFlow(CollectSignaturesFlow(
                    partSignedTx,
                    listOf(initiateFlow(produtor))))

            return subFlow(FinalityFlow(signedTx))
        }

    }

    @InitiatedBy(Initiator::class)
    class Acceptor(val otherParty: FlowSession): FlowLogic<SignedTransaction>(){

        @Suspendable
        override fun call(): SignedTransaction {
            val signFlow = object : SignTransactionFlow(otherParty) {
                override fun checkTransaction(stx: SignedTransaction) {
                // do nothing
                }
            }

            return subFlow(signFlow)
        }

    }

}