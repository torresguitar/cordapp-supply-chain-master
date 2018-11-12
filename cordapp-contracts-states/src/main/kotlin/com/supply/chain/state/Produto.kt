package com.supply.chain.state

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
data class Produto(val tipoProduto: TipoProduto,
                   val descricao: String,
                   val produtor: Party,
                   val dono: Party = produtor,
                   override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {
    override val participants: List<AbstractParty> = setOf(produtor, dono).toList()
}