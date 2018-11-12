package com.supply.chain.api

import net.corda.core.identity.CordaX500Name
import java.util.*

data class PedidoDTO(val idPedido: UUID, val produtos: List<UUID>, val alvo: CordaX500Name)