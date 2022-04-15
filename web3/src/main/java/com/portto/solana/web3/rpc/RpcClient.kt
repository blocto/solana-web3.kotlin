/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/20
 */

package com.portto.solana.web3.rpc

import com.portto.solana.web3.rpc.types.RpcRequest
import com.portto.solana.web3.rpc.types.RpcResponse
import com.portto.solana.web3.util.Cluster
import com.portto.solana.web3.util.json
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.single
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.tinylog.kotlin.Logger
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit

class RpcClient {

    private val endpoint: String

    constructor(endpoint: Cluster) {
        this.endpoint = endpoint.endpoint
    }

    constructor(endpoint: String) {
        this.endpoint = endpoint
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }

    suspend fun <T> requestBatch(
        batch: List<RpcRequest>,
        deserializer: KSerializer<T>,
    ): List<RpcResponse<T>> {

        val request = Request.Builder()
            .url(getEndpoint())
            .post(json.encodeToString(batch).also { Logger.debug(it) }.toRequestBody(JSON))
            .build()

        return callbackFlow {
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cancel("RPC Error", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseString =
                        response.body?.source()?.inputStream()?.bufferedReader()?.readText()
                    Logger.debug(responseString)
                    if (response.isSuccessful) {
                        val rpcResponse =
                            json.decodeFromString(ListSerializer(RpcResponse.serializer(deserializer)),
                                responseString ?: throw IllegalArgumentException())
                        trySendBlocking(rpcResponse)
                            .onFailure { Logger.error(it) }
                            .onSuccess { channel.close() }
                    } else {
                        cancel("RPC Error", Error(responseString))
                    }
                }
            })
            awaitClose { }
        }.single()
    }

    suspend fun <T> request(
        methodName: String,
        args: List<@Serializable Any>,
        deserializer: KSerializer<T>,
    ): RpcResponse<T> {
        val rpcRequest = RpcRequest(methodName, args)

        val request = Request.Builder()
            .url(getEndpoint())
            .post(json.encodeToString(rpcRequest).also { Logger.debug(it) }.toRequestBody(JSON))
            .build()

        return callbackFlow {
            httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cancel("RPC Error", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseString =
                        response.body?.source()?.inputStream()?.bufferedReader()?.readText()
                    Logger.debug(responseString)
                    if (response.isSuccessful) {
                        val rpcResponse =
                            json.decodeFromString(RpcResponse.serializer(deserializer),
                                responseString ?: throw IllegalArgumentException())
                        if (rpcResponse.error != null) {
                            cancel(CancellationException(rpcResponse.error.message))
                        }
                        trySendBlocking(rpcResponse)
                            .onFailure {
                                Logger.error(it)
                            }.onSuccess {
                                channel.close()
                            }
                    } else {
                        cancel("RPC Error", Error(responseString))
                    }
                }
            })
            awaitClose { }
        }.single()
    }

    private fun getEndpoint(): String = endpoint

    companion object {
        private val JSON: MediaType = "application/json; charset=utf-8".toMediaTypeOrNull()!!
    }
}
