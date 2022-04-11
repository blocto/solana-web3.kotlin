package com.portto.solana.web3.rpc

import com.portto.solana.web3.util.Cluster
import com.portto.solana.web3.rpc.types.RpcRequest
import com.portto.solana.web3.rpc.types.RpcResponse
import com.portto.solana.web3.util.json
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Created by guness on 6.12.2021 21:17
 */
class RpcClient {
    private var endpoint: String? = null
    private var cluster: WeightedCluster? = null

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    constructor(cluster: WeightedCluster) {
        this.cluster = cluster
    }

    constructor(endpoint: Cluster) : this(endpoint.endpoint)

    constructor(endpoint: String) {
        this.endpoint = endpoint
    }

    /**
     * Every type must be registered to SerializersModule in [json]
     */
    @Throws(RpcException::class)
    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun <T> call(method: String, params: List<@Serializable Any>, deserializer: KSerializer<T>): T {
        val rpcRequest = RpcRequest(method, params)

        val request = Request.Builder()
            .url(getEndpoint())
            .post(json.encodeToString(rpcRequest).toRequestBody(JSON))
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                println(body)
                val rpcResponse = json.decodeFromString(RpcResponse.serializer(deserializer), body!!)
                if (rpcResponse.error != null) {
                    throw RpcException(rpcResponse.error.message)
                }
                rpcResponse.result!!
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw RpcException(e.message)
        }
    }

    private fun getEndpoint(): String {
        return cluster?.getWeightedEndpoint() ?: endpoint!!
    }

    companion object {
        private val JSON: MediaType = "application/json; charset=utf-8".toMediaTypeOrNull()!!
    }
}
