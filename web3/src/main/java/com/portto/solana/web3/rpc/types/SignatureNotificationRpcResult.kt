/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/15
 */

package com.portto.solana.web3.rpc.types

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject

@Serializable
class SignatureNotificationRpcResult(val value: Value? = null) : RpcResultObject() {

    @Serializable(with = SignatureNotificationValueSerializer::class)
    sealed class Value
}

@Serializable
sealed class SignatureResult : SignatureNotificationRpcResult.Value()

@Serializable
class SignatureStatusResult(@Contextual val err: Any?) : SignatureResult()

@Serializable
object SignatureReceivedResult : SignatureResult() // literal('receivedSignature')

object SignatureNotificationValueSerializer :
    JsonContentPolymorphicSerializer<SignatureNotificationRpcResult.Value>(
        SignatureNotificationRpcResult.Value::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        element.jsonObject["err"] is JsonNull -> SignatureStatusResult.serializer()
        else -> SignatureReceivedResult.serializer()
    }
}
