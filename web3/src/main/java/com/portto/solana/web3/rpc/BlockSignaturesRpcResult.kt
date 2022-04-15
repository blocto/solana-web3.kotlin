/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/19
 */

package com.portto.solana.web3.rpc

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


typealias BlockSignatures = BlockSignaturesRpcResult

@Serializable
data class BlockSignaturesRpcResult(
    @SerialName("blockHeight")
    val blockHeight: Long,
    @SerialName("blockTime")
    val blockTime: Long,
    @SerialName("blockhash")
    val blockhash: String,
    @SerialName("parentSlot")
    val parentSlot: Long,
    @SerialName("previousBlockhash")
    val previousBlockhash: String,
    @SerialName("signatures")
    val signatures: List<String>,
)
