/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/10
 */

package com.portto.solana.app

import com.portto.solana.web3.AccountMeta
import com.portto.solana.web3.PublicKey
import com.portto.solana.web3.TransactionInstruction
import com.portto.solana.web3.programs.Program
import org.near.borshj.BorshBuffer

object ValueProgram : Program() {

    private val PROGRAM_ID = PublicKey("G4YkbRN4nFQGEUg4SXzPsrManWzuk8bNq9JaMhXepnZ6")
    private val ACCOUNT_PUBKEY = PublicKey("4AXy5YYCXpMapaVuzKkz25kVHzrdLDgKN3TiQvtf1Eu8")

    private const val INSTRUCTION_SET_VALUE = 0

    fun createSetValueInstruction(
        value: UInt,
        walletAddress: PublicKey
    ): TransactionInstruction {
        val buffer = BorshBuffer.allocate(Byte.SIZE_BYTES + Int.SIZE_BYTES)
        buffer.writeU8(INSTRUCTION_SET_VALUE)
        buffer.writeU32(value.toInt())
        return createTransactionInstruction(
            programId = PROGRAM_ID,
            keys = listOf(
                AccountMeta(publicKey = ACCOUNT_PUBKEY, isSigner = false, isWritable = true),
                AccountMeta(publicKey = walletAddress, isSigner = false, isWritable = false)
            ),
            data = buffer.toByteArray()
        )
    }
}