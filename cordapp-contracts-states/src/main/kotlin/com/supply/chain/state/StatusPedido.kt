package com.supply.chain.state

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class StatusPedido {
    Solicitado,
    EmTransito,
    Entregue
}