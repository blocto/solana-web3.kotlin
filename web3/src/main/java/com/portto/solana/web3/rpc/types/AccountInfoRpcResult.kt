package com.portto.solana.web3.rpc.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AccountInfoRpcResult(val value: Value? = null) : RpcResultObject() {

    @Serializable
    data class Value(
        @SerialName("data")
        val `data`: List<String>,
        @SerialName("executable")
        val executable: Boolean,
        @SerialName("lamports")
        val lamports: Long,
        @SerialName("owner")
        val owner: String,
        @SerialName("rentEpoch")
        val rentEpoch: Long,
    )
}

@Serializable
class AccountInfoResult<T>(val value: T) : RpcResultObject() {

    @Serializable
    class Value(
        @SerialName("data")
        val `data`: List<String>,
        @SerialName("executable")
        val executable: Boolean,
        @SerialName("lamports")
        val lamports: Long,
        @SerialName("owner")
        val owner: String,
        @SerialName("rentEpoch")
        val rentEpoch: Long,
    )
}
