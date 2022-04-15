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


/**
 * A subset of Commitment levels, which are at least optimistically confirmed
 * <pre>
 *   'confirmed': Query the most recent block which has reached 1 confirmation by the cluster
 *   'finalized': Query the most recent block which has been finalized by the cluster
 * </pre>
 */
@Serializable
enum class Finality(val value: String) {
    @SerialName("confirmed")
    CONFIRMED("confirmed"),

    @SerialName("finalized")
    FINALIZED("finalized")
}