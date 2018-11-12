package com.supply.chain.contract

import com.supply.chain.state.Pedido
import com.supply.chain.state.Produto
import com.supply.chain.state.StatusPedido
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

class PedidoContract: Contract {

    override fun verify(tx: LedgerTransaction) {
        val comando = tx.commandsOfType<Commands>().single()
        when(comando.value) {
            is Commands.Solicitar -> verifySolicitar(tx)
            is Commands.Enviar -> verifyEnviar(tx)
            is Commands.Entregar -> verifyEntregar(tx)
        }
    }

    fun verifySolicitar(tx: LedgerTransaction) {
        requireThat {
            "Deve haver apenas um output." using (tx.outputs.size == 1)
            "Não deve haver input." using (tx.inputStates.isEmpty())

            val out = tx.outputsOfType<Pedido>().single()

            "A quantidade de produto deve ser maior zero." using (
                    out.quantidadeProduto > 0)
            "O Status do pedido deve ser Solicitado." using (
                    out.statusPedido == StatusPedido.Solicitado )
        }
    }

    fun verificarMovimentacao(tx: LedgerTransaction, statusAnterior: StatusPedido, statusAtual: StatusPedido) {
        requireThat {
            "Deve haver apenas um input do tipo Pedido.." using (
                    tx.inputsOfType<Pedido>().size == 1)
            "Deve haver apenas um output do tipo Pedido." using (
                    tx.outputsOfType<Pedido>().size == 1)

            val inputPedido = tx.inputsOfType<Pedido>().single()
            val outputPedido = tx.outputsOfType<Pedido>().single()

            "O Status do pedido deveria ser $statusAnterior." using (inputPedido.statusPedido == statusAnterior)
            "O Status do pedido deve ser $statusAtual." using (outputPedido.statusPedido == statusAtual)

            "O Tipo dos produtos devem ser iguais ao Tipo do Produto no Pedido." using (
                    tx.outputsOfType<Produto>().all {
                        it.tipoProduto == outputPedido.tipoProduto }
                    )
            "A quantidade de produtos deve ser igual ao especificado no Pedido." using (
                    tx.outputsOfType<Produto>().size == outputPedido.quantidadeProduto
                    )
        }
    }

    fun verifyEnviar(tx: LedgerTransaction) {
        verificarMovimentacao(tx, StatusPedido.Solicitado, StatusPedido.EmTransito)
    }

    fun verifyEntregar(tx: LedgerTransaction) {
        verificarMovimentacao(tx, StatusPedido.EmTransito, StatusPedido.Entregue)
    }

    interface Commands: CommandData {
        class Solicitar: Commands
        class Enviar: Commands
        class Entregar: Commands
    }

}