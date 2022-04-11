package com.portto.solana.web3.rpc.types

import kotlinx.serialization.Serializable

@Serializable
class LatestBlockhashRpcResult(val value: Value? = null) : RpcResultObject() {

    @Serializable
    class Value(
        val blockhash: String? = null,
        val lastValidBlockHeight: Long? = null
    )
}
