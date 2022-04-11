package com.portto.solana.web3.util

import org.near.borshj.BorshBuffer
import org.near.borshj.BorshInput
import org.near.borshj.BorshOutput
import java.util.*

interface BorshCodable

interface BorshRule<T> {
    val clazz: Class<T>
    fun read(input: BorshInput): T?
    fun <Self> write(obj: Any, output: BorshOutput<Self>): Self
}

class Borsh {
    private var rules: List<BorshRule<*>> = listOf()

    fun setRules(rules: List<BorshRule<*>>) {
        this.rules = rules
    }

    fun getRules(): List<BorshRule<*>> {
        return rules
    }

    fun <T> isSerializable(klass: Class<T>?): Boolean {
        return if (klass == null) {
            false
        } else {
            Arrays.stream(klass.interfaces)
                .anyMatch {
                        iface: Class<*> -> iface == BorshCodable::class.java
                } || isSerializable(klass.superclass)
        }
    }

    fun serialize(obj: Any): ByteArray {
        return BorshBuffer.allocate(4096).write(obj).toByteArray()
    }

    fun <T> deserialize(bytes: ByteArray, klass: Class<T>): T {
        return deserialize(BorshBuffer.wrap(bytes), klass)
    }

    private fun <T> deserialize(buffer: BorshBuffer, klass: Class<*>): T {
        return buffer.read(klass)
    }
}