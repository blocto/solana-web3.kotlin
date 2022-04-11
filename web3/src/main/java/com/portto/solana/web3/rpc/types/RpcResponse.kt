package com.portto.solana.web3.rpc.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Created by guness on 6.12.2021 20:55
 */
@Serializable
data class RpcResponse<T>(
    @SerialName("jsonrpc")
    val version: String? = null,
    val result: T? = null,
    val error: Error? = null,
    val id: String? = null
) {
    @Serializable
    class Error(val code: Long = 0, val message: String? = null)
}
