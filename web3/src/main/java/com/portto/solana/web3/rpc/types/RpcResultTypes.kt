package com.portto.solana.web3.rpc.types

import com.portto.solana.web3.rpc.types.RpcResultObject
import kotlinx.serialization.Serializable

/**
 * Created by guness on 6.12.2021 22:09
 */
class RpcResultTypes {

    @Serializable
    class ValueLong(val value: Long = 0) : RpcResultObject()

    @Serializable
    class ValueBoolean(val value: Boolean) : RpcResultObject()

}