package com.portto.solana.web3.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule

val json = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    serializersModule = SerializersModule {
        contextual(Any::class, AnySerializer)
    }
}