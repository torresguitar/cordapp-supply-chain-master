package com.supply.chain.api

import com.supply.chain.flow.*
import com.supply.chain.state.TipoProduto
import io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST
import io.netty.handler.codec.http.HttpResponseStatus.CREATED
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

val SERVICE_NAMES = listOf("Notary", "Network Map Service")

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("supply")
class SupplyChainApi(private val rpcOps: CordaRPCOps) {
    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpcOps.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    @PUT
    @Path("adicionar-produto")
    @Produces(MediaType.TEXT_PLAIN)
    fun adicionarProduto(@QueryParam("tipoProduto") tipoProduto: TipoProduto,
                         @QueryParam("descricaoProduto") descricaoProduto: String): Response {

        return try {
            val signedTx = rpcOps.startTrackedFlow(CriarProdutoFlow::Initiator, descricaoProduto, tipoProduto).returnValue.getOrThrow()
            Response.status(CREATED.code()).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()
        }catch (e: Throwable){
            return Response.status(BAD_REQUEST.code()).entity(e.message).build()
        }
    }

    @PUT
    @Path("solicitar-pedido")
    @Produces(MediaType.TEXT_PLAIN)
    fun solicitarPedido(@QueryParam("tipoProduto") tipoProduto: TipoProduto,
                        @QueryParam("total") totalProduto: Int,
                        @QueryParam("partyName") partyName: CordaX500Name?): Response {
        if (partyName == null) {
            return Response.status(BAD_REQUEST.code()).entity("Query parameter 'partyName' missing or has wrong format.\n").build()
        }
        val otherParty = rpcOps.wellKnownPartyFromX500Name(partyName) ?:
            return Response.status(BAD_REQUEST.code()).entity("Party named $partyName cannot be found.\n").build()
        if (totalProduto <= 0)
            return Response.status(BAD_REQUEST.code()).entity("O total de produtos deve ser maior que zero.").build()
        return try {
            val signedTx = rpcOps.startTrackedFlow(SolicitarPedidoFlow::Initiator, otherParty, tipoProduto, totalProduto).returnValue.getOrThrow()
            Response.status(CREATED.code()).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()
        }catch (e: Throwable){
            return Response.status(BAD_REQUEST.code()).entity(e.message).build()
        }
    }

    @POST
    @Path("enviar-pedido")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    fun enviarPedido(pedido: PedidoDTO): Response {
        val transportadora = rpcOps.wellKnownPartyFromX500Name(pedido.alvo) ?:
            return Response.status(BAD_REQUEST.code()).entity("Party named ${pedido.alvo} cannot be found.\n").build()
        return try {
            val signedTx = rpcOps.startTrackedFlow(EnviarPedidoFlow::Initiator,
                    UniqueIdentifier(id = pedido.idPedido),
                    pedido.produtos.map{ UniqueIdentifier(id = it) }, transportadora).returnValue.getOrThrow()
            Response.status(CREATED.code()).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()
        }catch (e: Throwable){
            return Response.status(BAD_REQUEST.code()).entity(e.message).build()
        }
    }


    @POST
    @Path("entregar-pedido")
    @Produces(MediaType.TEXT_PLAIN)
    @Consumes(MediaType.APPLICATION_JSON)
    fun entregarPedido(pedido: PedidoDTO): Response {
        val destino = rpcOps.wellKnownPartyFromX500Name(pedido.alvo) ?:
        return Response.status(BAD_REQUEST.code()).entity("Party named ${pedido.alvo} cannot be found.\n").build()
        return try {
            val signedTx = rpcOps.startTrackedFlow(EntregarPedidoFlow::Initiator,
                    UniqueIdentifier(id = pedido.idPedido),
                    pedido.produtos.map{ UniqueIdentifier(id = it) }, destino).returnValue.getOrThrow()
            Response.status(CREATED.code()).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()
        } catch (e: Throwable){
            return Response.status(BAD_REQUEST.code()).entity(e.message).build()
        }
    }

    @PUT
    @Path("entregar-pedido")
    @Produces(MediaType.TEXT_PLAIN)
    fun venderProduto(@QueryParam("idProduto") idProduto: UUID): Response {
        return try {
            val signedTx = rpcOps.startTrackedFlow(VenderProdutoFlow::Initiator,
                    UniqueIdentifier(id = idProduto)).returnValue.getOrThrow()
            Response.status(CREATED.code()).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()
        } catch (e: Throwable){
            return Response.status(BAD_REQUEST.code()).entity(e.message).build()
        }
    }
}
