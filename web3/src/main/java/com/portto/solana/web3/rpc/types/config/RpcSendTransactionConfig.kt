package com.portto.solana.web3.rpc.types.config

import com.portto.solana.web3.rpc.types.Encoding
import kotlinx.serialization.Serializable

/**
 * Created by guness on 6.12.2021 21:07
 */
@Serializable
class RpcSendTransactionConfig(
    val encoding: Encoding = Encoding.Base64,
    val skipPreFlight: Boolean = true
)
