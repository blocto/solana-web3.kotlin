package com.portto.solana.web3.rpc.types

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WsRpcResponse<T>(
    @SerialName("jsonrpc")
    val version: String? = null,
    val method: String? = null,
    val params: T? = null,
    val result: @Contextual Any? = null,
    val error: Error? = null,
    val id: String? = null
) {
    @Serializable
    class Error(val code: Long = 0, val message: String? = null)
}
