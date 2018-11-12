package com.supply.chain.flow

import com.supply.chain.state.Produto
import com.supply.chain.state.TipoProduto
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class CriarProdutoFlowTest {

    private val network = MockNetwork(listOf("com.supply.chain"))
    private val a = network.createNode()

    init {
        //listOf(a, b).forEach {
            //it.registerInitiatedFlow(Responder::class.java)
        //}
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `deve criar o state e armazenar`() {
        val descricaoProduto = "produto 1"
        val tipoProduto = TipoProduto.CELULAR

        val flow = CriarProdutoFlow.Initiator(
                descricaoProduto, tipoProduto)
        val future = a.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        a.transaction {
            val produtoBase = a.services.vaultService.queryBy<Produto>().states.single().state.data

            assertEquals(descricaoProduto, produtoBase.descricao)
            assertEquals(tipoProduto, produtoBase.tipoProduto)
        }
    }
}