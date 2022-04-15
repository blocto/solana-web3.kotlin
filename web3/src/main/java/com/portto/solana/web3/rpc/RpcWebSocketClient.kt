/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/13
 */

package com.portto.solana.web3.rpc

import com.portto.solana.web3.Connection
import com.portto.solana.web3.rpc.types.RpcRequest
import com.portto.solana.web3.rpc.types.RpcResponse
import com.portto.solana.web3.util.Cluster
import com.portto.solana.web3.util.json
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import okhttp3.*
import org.tinylog.kotlin.Logger
import java.util.*
import java.util.concurrent.TimeUnit

class RpcWebSocketClient {

    private var endpoint: String? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .pingInterval(5, TimeUnit.SECONDS)
            .build()
    }

    private lateinit var webSocket: WebSocket

    constructor(endpoint: Cluster) : this(endpoint.endpoint.replace("https", "wss"))

    private constructor(endpoint: String) {
        this.endpoint = endpoint
    }

    fun init(listener: WebSocketListener) {
        val request = Request.Builder()
            .url(endpoint!!)
            .build()
        webSocket = httpClient.newWebSocket(request, listener)
    }

    fun subscribe(method: String, params: List<@Serializable Any>): String {
        val rpcRequest = RpcRequest(method, params)
        webSocket.send(json.encodeToString(rpcRequest).also { Logger.debug(it) })
        return rpcRequest.id
    }

    fun call(method: String, params: List<@Serializable Any>): Int {
        val rpcRequest = RpcRequest(method, params)
        val request = Request.Builder()
            .url(endpoint!!)
            .build()
        val listener = object : WebSocketListener() {
            var id = -1
            override fun onMessage(webSocket: WebSocket, text: String) {
                id = json.decodeFromString(RpcResponse.serializer(Int.serializer()), text).result
                    ?: -1
            }
        }
        httpClient.newWebSocket(request, listener)
            .send(json.encodeToString(rpcRequest).also { Logger.debug(it) })
        return runBlocking {
            return@runBlocking withTimeoutOrNull(10000L) {
                repeat(500) {
                    if (listener.id == -1) {
                        delay(150L)
                    } else {
                        return@withTimeoutOrNull listener.id
                    }
                }
                -1
            } ?: -1
        }
    }

    suspend fun callFlow(method: String, params: List<@Serializable Any>): Flow<Int> {
        val rpcRequest = RpcRequest(method, params)
        val request = Request.Builder()
            .url(endpoint!!)
            .build()
        val confirmPromise: Flow<Int> = callbackFlow<Int> {
            var id: Int
            val listener = object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    super.onMessage(webSocket, text)
                    id =
                        json.decodeFromString(RpcResponse.serializer(Int.serializer()), text).result
                            ?: -1
                    Logger.debug("onMessage:$text")
                    trySend(id)
                    channel.close()
                }
            }
            val newWebSocket = httpClient.newWebSocket(request, listener)
            newWebSocket.send(json.encodeToString(rpcRequest).also { Logger.debug(it) })
            awaitClose {
                Logger.debug("callFlow -- awaitClose")
            }
        }
        return confirmPromise
    }


    fun unsubscribe(sub: Connection.SubscriptionId, rpcMethod: String): String {
        val rpcRequest = RpcRequest(rpcMethod, mutableListOf<Any>(sub.id))
        webSocket.send(json.encodeToString(rpcRequest).also { Logger.debug(it) })
        return rpcRequest.id
    }

/*
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun subscribe2(method: String, params: List<@Serializable Any>): Long {
        val rpcRequest = RpcRequest(method, params)
        webSocket.send(json.encodeToString(rpcRequest).also { Logger.debug(it) })
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                json.decodeFromString(RpcResponse.serializer(Long.serializer()), text)
            }
        }
        httpClient
            .newWebSocket(Request.Builder().url(endpoint!!).build(),listener)
            .send(json.encodeToString(rpcRequest).also { Logger.debug(it) })
    }
*/

    fun close() {
        webSocket.close(1000, null)
    }

}
