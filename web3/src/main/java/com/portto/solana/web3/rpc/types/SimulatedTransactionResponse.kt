/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/15
 */

package com.portto.solana.web3.rpc.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class SimulatedTransactionResponse(val value: Value) : RpcResultObject() {
    @Serializable
    class Value(
        @SerialName("accounts")
        val accounts: List<SimulatedTransactionAccountInfo?>?,
        @SerialName("err")
        val err: String?,
        @SerialName("logs")
        val logs: List<String>,
        @SerialName("unitsConsumed")
        val unitsConsumed: Int,
    )
}

@Serializable
class SimulatedTransactionAccountInfo(
    @SerialName("data")
    val `data`: List<String>,
    @SerialName("executable")
    val executable: Boolean,
    @SerialName("lamports")
    val lamports: Long,
    @SerialName("owner")
    val owner: String,
    @SerialName("rentEpoch")
    val rentEpoch: Long,
)
