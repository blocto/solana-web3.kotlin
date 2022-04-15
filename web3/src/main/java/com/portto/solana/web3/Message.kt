/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/10
 */

package com.portto.solana.web3

import com.portto.solana.web3.util.Shortvec
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bitcoinj.core.Base58
import org.near.borshj.BorshBuffer
import java.nio.ByteBuffer

@Serializable
class MessageHeader {
    @SerialName("numRequiredSignatures")
    var numRequiredSignatures: Byte = 0
    @SerialName("numReadonlySignedAccounts")
    var numReadonlySignedAccounts: Byte = 0
    @SerialName("numReadonlyUnsignedAccounts")
    var numReadonlyUnsignedAccounts: Byte = 0
    fun toByteArray(): ByteArray {
        return byteArrayOf(
            numRequiredSignatures,
            numReadonlySignedAccounts,
            numReadonlyUnsignedAccounts
        )
    }

    companion object {
        const val HEADER_LENGTH = 3
    }
}

/**
 * An instruction to execute by a program
 *
 * @property {number} programIdIndex
 * @property {number[]} accounts
 * @property {string} data
 */
@Serializable
data class CompiledInstruction(
    /** Index into the transaction keys array indicating the program account that executes this instruction */
    val programIdIndex: Int,
    /** Ordered indices into the transaction keys array indicating which accounts to pass to the program */
    val accounts: List<Int>,
    /** The program input data encoded as base 58 */
    val data: String
)

const val PUBKEY_LENGTH = 32

/**
 * List of instructions to be processed atomically
 */
class Message(
    val header: MessageHeader,
    val accountKeys: List<PublicKey>,
    val recentBlockhash: Blockhash,
    val instructions: List<com.portto.solana.web3.CompiledInstruction>
) {

    private var indexToProgramIds = mutableMapOf<Int, PublicKey>()

    init {
        this.instructions.forEach { ix ->
            this.indexToProgramIds[ix.programIdIndex] = this.accountKeys[ix.programIdIndex]
        }
    }

    fun isAccountSigner(index: Int): Boolean {
        return index < this.header.numRequiredSignatures
    }

    fun isAccountWritable(index: Int): Boolean {
        return index < header.numRequiredSignatures - header.numReadonlySignedAccounts ||
                (index >= header.numRequiredSignatures &&
                        index < accountKeys.count() - header.numReadonlyUnsignedAccounts)
    }

    fun isProgramId(index: Int): Boolean {
        return indexToProgramIds.containsKey(index)
    }

    fun programIds(): List<PublicKey> {
        return indexToProgramIds.values.toList()
    }

    fun nonProgramIds(): List<PublicKey> {
        return this.accountKeys.filterIndexed { index, _ -> !this.isProgramId(index) }
    }

    data class CompiledInstruction(
        var programIdIndex: Byte = 0,
        var keyIndicesCount: ByteArray,
        var keyIndices: ByteArray,
        var dataLength: ByteArray,
        var data: ByteArray
    ) {

        // 1 = programIdIndex length
        val length: Int
            get() =// 1 = programIdIndex length
                1 + keyIndicesCount.size + keyIndices.size + dataLength.size + data.size
    }

    private var feePayer: PublicKey? = null

    fun serialize(): ByteArray {
        require(recentBlockhash.isNotEmpty()) { "recentBlockhash required" }
        require(instructions.isNotEmpty()) { "No instructions provided" }
        val numKeys = this.accountKeys.count()
        val keyCount = Shortvec.encodeLength(numKeys)
        var compiledInstructionsLength = 0
        val instructions = this.instructions.map { instruction ->
            val (programIdIndex, accounts, _) = instruction
            val data = Base58.decode(instruction.data)

            val keyIndicesCount = Shortvec.encodeLength(accounts.count())
            val dataCount = Shortvec.encodeLength(data.count())

            CompiledInstruction(
                programIdIndex = programIdIndex.toByte(),
                keyIndicesCount = keyIndicesCount,
                keyIndices = accounts.map(Int::toByte).toByteArray(),
                dataLength = dataCount,
                data = data,
            )
        }
        val instructionCount = Shortvec.encodeLength(instructions.size)
        val bufferSize = (MessageHeader.HEADER_LENGTH + RECENT_BLOCK_HASH_LENGTH + keyCount.size
                + numKeys * PublicKey.PUBLIC_KEY_LENGTH + instructionCount.size
                + compiledInstructionsLength)
        val out = ByteBuffer.allocate(bufferSize)
        val accountKeysBuff = ByteBuffer.allocate(numKeys * PublicKey.PUBLIC_KEY_LENGTH)

        val buffer = BorshBuffer.allocate(2048)
        buffer.write(header.numRequiredSignatures)
        buffer.write(header.numReadonlySignedAccounts)
        buffer.write(header.numReadonlyUnsignedAccounts)
        buffer.write(keyCount)
        for (accountKey in accountKeys) {
            buffer.write(accountKey.pubkey)
        }
        buffer.write(Base58.decode(recentBlockhash))
        buffer.write(instructionCount)
        for (instruction in instructions) {
            buffer.write(instruction.programIdIndex)
            buffer.write(instruction.keyIndicesCount)
            buffer.write(instruction.keyIndices)
            buffer.write(instruction.dataLength)
            buffer.write(instruction.data)
        }
        return buffer.toByteArray()
            /*.also {
                Logger.debug(
                    it.asUByteArray().joinToString("") { it.toString(radix = 16).padStart(2, '0') })
            }*/
    }

    fun setFeePayer(publicKey: PublicKey) {
        this.feePayer = publicKey
    }

    companion object {

        /**
         * Decode a compiled message into a Message object.
         */
        fun from(buffer: ByteArray): Message {
            // Slice up wire data
            var byteArray = buffer

            val numRequiredSignatures = byteArray.first().toInt().also { byteArray = byteArray.drop(1).toByteArray() }
            val numReadonlySignedAccounts = byteArray.first().toInt().also { byteArray = byteArray.drop(1).toByteArray() }
            val numReadonlyUnsignedAccounts = byteArray.first().toInt().also { byteArray = byteArray.drop(1).toByteArray() }

            val accountCount = Shortvec.decodeLength(byteArray)
            byteArray = accountCount.second
            val accountKeys = mutableListOf<String>()
            for (i in 0 until accountCount.first) {
                val account = byteArray.slice(0 until PUBKEY_LENGTH)
                byteArray = byteArray.drop(PUBKEY_LENGTH).toByteArray()
                accountKeys.add(Base58.encode(account.toByteArray()))
            }

            val recentBlockhash = byteArray.slice(0 until PUBKEY_LENGTH).toByteArray()
            byteArray = byteArray.drop(PUBKEY_LENGTH).toByteArray()

            val instructionCount = Shortvec.decodeLength(byteArray)
            byteArray = instructionCount.second
            val instructions = mutableListOf<com.portto.solana.web3.CompiledInstruction>()
            for (i in 0 until instructionCount.first) {
                val programIdIndex = byteArray.first().toInt().also { byteArray = byteArray.drop(1).toByteArray() }
                val accountCount = Shortvec.decodeLength(byteArray)
                byteArray = accountCount.second
                val accounts =
                    byteArray.slice(0 until accountCount.first).toByteArray().toList().map(Byte::toInt)
                byteArray = byteArray.drop(accountCount.first).toByteArray()
                val dataLength = Shortvec.decodeLength(byteArray)
                byteArray = dataLength.second
                val dataSlice = byteArray.slice(0 until dataLength.first).toByteArray()
                val data = Base58.encode(dataSlice)
                byteArray = byteArray.drop(dataLength.first).toByteArray()
                instructions.add(
                    CompiledInstruction(
                        programIdIndex = programIdIndex,
                        accounts = accounts,
                        data = data,
                    )
                )
            }

            return Message(
                header = MessageHeader().apply {
                    this.numRequiredSignatures = numRequiredSignatures.toByte()
                    this.numReadonlySignedAccounts = numReadonlySignedAccounts.toByte()
                    this.numReadonlyUnsignedAccounts = numReadonlyUnsignedAccounts.toByte()
                },
                accountKeys = accountKeys.map { PublicKey(it) },
                recentBlockhash = Blockhash().plus(Base58.encode(recentBlockhash)),
                instructions = instructions
            )
        }

        private const val RECENT_BLOCK_HASH_LENGTH = 32
    }

}