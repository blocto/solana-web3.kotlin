package com.portto.solana.web3.util

import kotlinx.serialization.ContextualSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer

/*
https://github.com/Kotlin/kotlinx.serialization/issues/1351#issuecomment-788913996
 */
object DynamicLookupSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor =
        ContextualSerializer(Any::class, null, emptyArray()).descriptor

    @Suppress("UNCHECKED_CAST")
    override fun serialize(encoder: Encoder, value: Any) {
        try {
            val actualSerializer =
                encoder.serializersModule.getContextual(value::class) ?: value::class.serializer()
            encoder.encodeSerializableValue(actualSerializer as KSerializer<Any>, value)
        } catch (e: Exception) {
            val jsonEncoder = encoder as JsonEncoder
            val jsonElement = serializeAny(value)
            jsonEncoder.encodeJsonElement(jsonElement)
        }
    }

    override fun deserialize(decoder: Decoder): Any {
        error("Unsupported")
    }

    private fun serializeAny(value: Any?): JsonElement = when (value) {
        is Map<*, *> -> {
            val mapContents = value.entries.associate { mapEntry ->
                mapEntry.key.toString() to serializeAny(mapEntry.value)
            }
            JsonObject(mapContents)
        }
        is List<*> -> {
            val arrayContents = value.map { listEntry -> serializeAny(listEntry) }
            JsonArray(arrayContents)
        }
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

}
