package com.supply.chain.flow

import com.supply.chain.state.Pedido
import com.supply.chain.state.Produto
import com.supply.chain.state.StatusPedido
import com.supply.chain.state.TipoProduto
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNodeParameters
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class VenderProdutoFlowTest {

    private val network = MockNetwork(listOf("com.supply.chain"))
    private val produtor = network.createNode(MockNodeParameters(
            legalName = CordaX500Name.parse("O=Produtor,L=London,C=GB")))
    private val distribuidor = network.createNode(MockNodeParameters(
            legalName = CordaX500Name.parse("O=Distribuidor,L=London,C=GB")))
    private val transportador = network.createNode(MockNodeParameters(
            legalName = CordaX500Name.parse("O=Transportadora,L=London,C=GB")))
    private val loja = network.createNode(MockNodeParameters(
            legalName = CordaX500Name.parse("O=Loja,L=London,C=GB")))
    private val totalProdutos = 10

    init {
        listOf(produtor, distribuidor, transportador, loja).forEach {
            it.registerInitiatedFlow(SolicitarPedidoFlow.Acceptor::class.java)
            it.registerInitiatedFlow(EnviarPedidoFlow.Acceptor::class.java)
            it.registerInitiatedFlow(EntregarPedidoFlow.Acceptor::class.java)
            it.registerInitiatedFlow(VenderProdutoFlow.Acceptor::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    fun solicitarEValidarSolicitacao(solicitante: StartedMockNode, receptor: StartedMockNode): UniqueIdentifier {
        val quantidadeProduto = totalProdutos
        val tipoProduto = TipoProduto.CELULAR
        val flow = SolicitarPedidoFlow.Initiator(
                receptor.info.legalIdentities.first(),
                tipoProduto, quantidadeProduto)


        val future = solicitante.startFlow(flow)
        network.runNetwork()
        val transacao = future.getOrThrow()

        listOf(solicitante, receptor).forEach {
            it.transaction {
                val pedidoBase = it.services.vaultService.queryBy<Pedido>(
                        QueryCriteria.LinearStateQueryCriteria(linearId =
                        listOf(transacao.coreTransaction.outputsOfType<Pedido>().single().linearId))
                ).states.single().state.data

                assertEquals(tipoProduto, pedidoBase.tipoProduto)
                assertEquals(quantidadeProduto, pedidoBase.quantidadeProduto)
            }

        }

        return transacao.coreTransaction.outputsOfType<Pedido>().single().linearId
    }

    fun emitirProdutos(tipoProduto: TipoProduto, quantidade: Int): List<UniqueIdentifier> =
            0.until(quantidade).map {
                val flow = CriarProdutoFlow.Initiator("produto $it", tipoProduto)
                val future = produtor.startFlow(flow)
                network.runNetwork()
                future.getOrThrow()
            }.map {
                it.coreTransaction.outputsOfType<Produto>().single()
            }.map { it.linearId }


    fun emitirTransportesEValidarEnvio(emissor: StartedMockNode, chavesProdutos: List<UniqueIdentifier>, idPedido: UniqueIdentifier) {
        val flow = EnviarPedidoFlow.Initiator(idPedido, chavesProdutos, transportador.info.legalIdentities.single())
        val future = emissor.startFlow(flow)
        network.runNetwork()

        future.getOrThrow()

        listOf(produtor).forEach {
            it.transaction {
                val produtos = it.services.vaultService.queryBy<Produto>().states.map { it.state.data }

                assertEquals(totalProdutos, produtos.size)
                produtos.forEach {
                    assertEquals(transportador.info.legalIdentities.single(), it.dono)
                }
            }
        }
        listOf(emissor).forEach {
            it.transaction {
                val pedidoBase = it.services.vaultService.queryBy<Pedido>(
                        QueryCriteria.LinearStateQueryCriteria(linearId = listOf(idPedido))
                ).states.single().state.data

                assertEquals(StatusPedido.EmTransito, pedidoBase.statusPedido)
            }
        }
        listOf(transportador).forEach {
            it.transaction {
                val produtos = it.services.vaultService.queryBy<Produto>().states.map { it.state.data }

                assertEquals(totalProdutos, produtos.size)
                produtos.forEach {
                    assertEquals(transportador.info.legalIdentities.single(), it.dono)
                }
            }
        }
    }

    fun entregarEValidarEnvio(de: StartedMockNode, para: StartedMockNode, produtos: List<UniqueIdentifier>, idPedido: UniqueIdentifier) {
        val flow = EntregarPedidoFlow.Initiator(idPedido, produtos, para.info.legalIdentities.single())
        val future = transportador.startFlow(flow)
        network.runNetwork()
        future.getOrThrow()

        setOf(produtor, de, para).forEach {
            it.transaction {
                val produtosBase = it.services.vaultService.queryBy<Produto>().states.map { it.state.data }

                assertEquals(totalProdutos, produtos.size)
                produtosBase.forEach {
                    assertEquals(para.info.legalIdentities.single(), it.dono)
                }
            }
        }
        setOf(de, para).forEach {
            it.transaction {
                val pedidoBase = it.services.vaultService.queryBy<Pedido>(
                        QueryCriteria.LinearStateQueryCriteria(linearId = listOf(idPedido))
                ).states.single().state.data

                assertEquals(StatusPedido.Entregue, pedidoBase.statusPedido)
            }
        }
    }

    fun venderProdutoEValidar(idProduto: UniqueIdentifier) {
        val flow = VenderProdutoFlow.Initiator(idProduto)
        val future = loja.startFlow(flow)
        network.runNetwork()

        future.getOrThrow()

        listOf(produtor, loja).forEach {
            it.transaction {
                assertTrue(it.services.vaultService.queryBy<Produto>(
                        QueryCriteria.LinearStateQueryCriteria(linearId = listOf(idProduto))
                ).states.isEmpty())
            }
        }
    }

    @Test
    fun `deve ser possivel que a loja venda o produto`() {
        val idPedido = solicitarEValidarSolicitacao(distribuidor, produtor)
        val produtos = emitirProdutos(TipoProduto.CELULAR, totalProdutos)
        assertEquals(totalProdutos, produtos.size)
        emitirTransportesEValidarEnvio(produtor, produtos, idPedido)
        entregarEValidarEnvio(produtor, distribuidor, produtos, idPedido)
        val idPedidoLoja = solicitarEValidarSolicitacao(loja, distribuidor)
        emitirTransportesEValidarEnvio(distribuidor, produtos, idPedidoLoja)
        entregarEValidarEnvio(distribuidor, loja, produtos, idPedidoLoja)
        venderProdutoEValidar(produtos.first())
    }
}