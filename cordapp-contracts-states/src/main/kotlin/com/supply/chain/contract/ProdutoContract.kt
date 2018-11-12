package com.supply.chain.contract

import com.supply.chain.state.Produto
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class ProdutoContract : Contract {

    override fun verify(tx: LedgerTransaction) {
        if (tx.commandsOfType<Commands>().isNotEmpty()) {
            val comando = tx.commandsOfType<Commands>().single()
            when (comando.value) {
                is Commands.Create -> verifyCreate(tx)
                is Commands.Vender -> verifyVender(tx)
            }
        }
    }

    fun verifyCreate(tx: LedgerTransaction){
        requireThat {
            "Deve haver apenas um output." using (tx.outputs.size == 1)
            "Não deve haver input." using (tx.inputStates.isEmpty())

            "O objeto transacionado deve ser um Produto." using (
                    tx.outputs.single().data is Produto)


            val out = tx.outputsOfType<Produto>().single()

            "O dono deve ser o produtor." using (out.dono == out.produtor)
        }
    }

    fun verifyVender(tx: LedgerTransaction) {
        requireThat {
            "Não deve haver output." using (tx.outputs.isEmpty())
            "Deve haver um input." using (tx.inputs.size == 1)
        }
    }

    interface Commands: CommandData {
        class Create : Commands
        class Vender : Commands
    }

}