/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/19
 */

package com.portto.solana.web3.rpc.types


import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConfirmedSignatureInfo(
    @SerialName("blockTime")
    val blockTime: Long?,
//    @SerialName("confirmationStatus")
//    val confirmationStatus: String,
    @SerialName("err")
    @Contextual val err: Any?,
    @SerialName("memo")
    val memo: String?,
    @SerialName("signature")
    val signature: String,
    @SerialName("slot")
    val slot: Long,
)