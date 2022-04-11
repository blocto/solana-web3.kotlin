package com.portto.solana.web3.rpc.types

import com.portto.solana.web3.util.DynamicLookupSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

/**
 * Created by guness on 6.12.2021 20:48
 */
@Serializable
class RpcRequest constructor(
    val method: String,
    val params: @Serializable List<@Serializable(with = DynamicLookupSerializer::class) Any>? = null
) {
    @SerialName("jsonrpc")
    val version = "2.0"
    val id: String = UUID.randomUUID().toString()
}
