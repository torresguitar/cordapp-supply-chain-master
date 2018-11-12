package com.supply.chain.flow

import co.paralleluniverse.fibers.Suspendable
import com.supply.chain.contract.ProdutoContract
import com.supply.chain.state.Produto
import com.supply.chain.state.TipoProduto
import net.corda.core.contracts.Command
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder

object CriarProdutoFlow {

    @StartableByRPC
    @InitiatingFlow
    class Initiator(val descricaoProduto: String, val tipoProduto: TipoProduto): FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            val comando = Command(ProdutoContract.Commands.Create(), ourIdentity.owningKey)
            val outputState = Produto(tipoProduto, descricaoProduto, ourIdentity)

            val builder = TransactionBuilder(notary)
                    .addCommand(comando)
                    .addOutputState(outputState, ProdutoContract::class.java.canonicalName)

            builder.verify(serviceHub)

            val signedTx = serviceHub.signInitialTransaction(builder)

            return subFlow(FinalityFlow(signedTx))
        }

    }

}