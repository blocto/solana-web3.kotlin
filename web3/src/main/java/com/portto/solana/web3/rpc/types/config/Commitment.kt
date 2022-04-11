package com.portto.solana.web3.rpc.types.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Commitment(val value: String) {
    @SerialName("processed")
    PROCESSED("processed"),
    @SerialName("confirmed")
    CONFIRMED("confirmed"),
    @SerialName("finalized")
    FINALIZED("finalized")
}
