package com.supply.chain.plugin

import com.supply.chain.api.SupplyChainApi
import net.corda.core.messaging.CordaRPCOps
import net.corda.webserver.services.WebServerPluginRegistry
import java.util.function.Function

class SupplyChainWebServerPluginRegistry : WebServerPluginRegistry {

    override val webApis: List<Function<CordaRPCOps, out Any>> = listOf(Function(::SupplyChainApi))


    override val staticServeDirs: Map<String, String> = mapOf(
    )
}