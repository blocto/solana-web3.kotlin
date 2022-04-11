package com.portto.solana.web3.rpc.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Created by guness on 6.12.2021 21:10
 */
@Serializable
enum class Encoding(val encoding: String) {
    @SerialName("base64")
    Base64("base64");
}
