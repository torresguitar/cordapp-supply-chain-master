package com.supply.chain.flow

import com.supply.chain.state.Pedido
import com.supply.chain.state.TipoProduto
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class SolicitarPedidoFlowTest {

    private val network = MockNetwork(listOf("com.supply.chain"))
    private val produtor = network.createNode(MockNodeParameters(
            legalName = CordaX500Name.parse("O=Produtor,L=London,C=GB")))
    private val distribuidor = network.createNode(MockNodeParameters(
            legalName = CordaX500Name.parse("O=Distribuidor,L=London,C=GB")))
    private val transportador = network.createNode(MockNodeParameters(
            legalName = CordaX500Name.parse("O=Transportadora,L=London,C=GB")))
    private val loja = network.createNode(MockNodeParameters(
            legalName = CordaX500Name.parse("O=Loja,L=London,C=GB")))

    init {
        listOf(produtor, distribuidor, transportador, loja).forEach {
            it.registerInitiatedFlow(SolicitarPedidoFlow.Acceptor::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    fun solicitarEValidarSolicitacao(solicitante: StartedMockNode, receptor: StartedMockNode){
        val quantidadeProduto = 100
        val tipoProduto = TipoProduto.CELULAR
        val flow = SolicitarPedidoFlow.Initiator(
                receptor.info.legalIdentities.first(),
                tipoProduto, quantidadeProduto)


        val future = solicitante.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        listOf(solicitante, receptor).forEach{
            it.transaction {
                val pedidoBase = it.services.vaultService.queryBy<Pedido>().states.single().state.data

                assertEquals(tipoProduto, pedidoBase.tipoProduto)
                assertEquals(quantidadeProduto, pedidoBase.quantidadeProduto)
            }

        }
    }

    @Test
    fun `deve ser possivel que o produtor receba um pedido do distribuidor`() {
        solicitarEValidarSolicitacao(distribuidor, produtor)
    }

    @Test
    fun `deve ser possivel que o distribuidor receba um pedido da loja`() {
        solicitarEValidarSolicitacao(loja, distribuidor)
    }

}