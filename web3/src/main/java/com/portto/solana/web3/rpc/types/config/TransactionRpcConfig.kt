package com.portto.solana.web3.rpc.types.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TransactionRpcConfig(
    @SerialName("encoding")
    val encoding: String? = "json",
    @SerialName("commitment")
    val commitment: Commitment? = null,
    @SerialName("maxSupportedTransactionVersion")
    val maxSupportedTransactionVersion: Int? = null
)