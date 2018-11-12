package com.supply.chain.state

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class Pedido(val solicitante: Party,
                  val dono: Party,
                  val tipoProduto: TipoProduto,
                  val quantidadeProduto: Int,
                  val statusPedido: StatusPedido = StatusPedido.Solicitado,
                  val transportador: Party? = null,
                  override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {
    override val participants: List<AbstractParty> = listOf(solicitante, dono, transportador).mapNotNull { it }
}