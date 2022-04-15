/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/15
 */

package com.portto.solana.web3.rpc.types

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SignatureStatusesRcpResult(
    val value: List<SignatureStatus?> = emptyList(),
) : RpcResultObject() {
    @Serializable
    class SignatureStatus(
        @SerialName("confirmationStatus")
        val confirmationStatus: String?,
        @SerialName("confirmations")
        val confirmations: Int?,
        @SerialName("err")
        @Contextual val err: Any?,
        @SerialName("slot")
        val slot: Long,
    )
}