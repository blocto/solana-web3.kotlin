/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/11
 */

package com.portto.solana.web3.programs

import com.portto.solana.web3.AccountMeta
import com.portto.solana.web3.PUBKEY_LENGTH
import com.portto.solana.web3.PublicKey
import com.portto.solana.web3.TransactionInstruction
import org.bitcoinj.core.Utils
import org.near.borshj.BorshBuffer

/**
 * Factory class for transactions to interact with the System program
 */
object SystemProgram : Program() {

    /**
     * Public key that identifies the System program
     */
    val PROGRAM_ID = PublicKey("11111111111111111111111111111111")

    private const val PROGRAM_INDEX_CREATE_ACCOUNT = 0
    private const val PROGRAM_INDEX_TRANSFER = 2
    private const val PROGRAM_INDEX_TRANSFER_WITH_SEED = 11

//    /** 0 **/CreateAccount {/**/},
//    /** 1 **/Assign {/**/},
//    /** 2 **/Transfer {/**/},
//    /** 3 **/CreateAccountWithSeed {/**/},
//    /** 4 **/AdvanceNonceAccount,
//    /** 5 **/WithdrawNonceAccount(u64),
//    /** 6 **/InitializeNonceAccount(Pubkey),
//    /** 7 **/AuthorizeNonceAccount(Pubkey),
//    /** 8 **/Allocate {/**/},
//    /** 9 **/AllocateWithSeed {/**/},
//    /** 10 **/AssignWithSeed {/**/},
//    /** 11 **/TransferWithSeed {/**/},

    /**
     * Generate a transaction instruction that creates a new account
     */
    fun createAccount(
        /** The account that will transfer lamports to the created account */
        fromPublicKey: PublicKey,
        /** Public key of the created account */
        newAccountPublicKey: PublicKey,
        /** Amount of lamports to transfer to the created account */
        lamports: Long,
        /** Amount of space in bytes to allocate to the created account */
        space: Long,
        /** Public key of the program to assign as the owner of the created account */
        programId: PublicKey
    ): TransactionInstruction {
        val keys = ArrayList<AccountMeta>()
        keys.add(AccountMeta(publicKey = fromPublicKey, isSigner = true, isWritable = true))
        keys.add(AccountMeta(publicKey = newAccountPublicKey, isSigner = true, isWritable = true))
/*
        BufferLayout.struct<SystemInstructionInputData['Create']>([
            BufferLayout.u32('instruction'),
            BufferLayout.ns64('lamports'),
            BufferLayout.ns64('space'),
            Layout.publicKey('programId'),
        ])
*/
        val data = ByteArray(Int.SIZE_BYTES + Long.SIZE_BYTES + Long.SIZE_BYTES + PUBKEY_LENGTH)
        Utils.uint32ToByteArrayLE(PROGRAM_INDEX_CREATE_ACCOUNT.toLong(), data, 0)
        Utils.int64ToByteArrayLE(lamports, data, 4)
        Utils.int64ToByteArrayLE(space, data, 12)
        System.arraycopy(programId.toByteArray(), 0, data, 20, 32)
        return createTransactionInstruction(PROGRAM_ID, keys, data)
    }

    /**
     * Generate a transaction instruction that transfers lamports from one account to another
     */
    @JvmStatic
    fun transfer(
        /** Account that will transfer lamports */
        fromPublicKey: PublicKey,
        /** Base public key to use to derive the funding account address */
        basePubkey: PublicKey? = null,
        /** Account that will receive transferred lamports */
        toPublicKey: PublicKey,
        /** Amount of lamports to transfer */
        lamports: Long,
        /** Seed to use to derive the funding account address */
        seed: String? = null,
        /** Program id to use to derive the funding account address */
        programId: PublicKey? = null
    ): TransactionInstruction {

        // SystemInstruction.Transfer

        if (basePubkey != null && seed != null && programId != null) {
            val rustStringLength = Int.SIZE_BYTES + Int.SIZE_BYTES + 1
            val buffer =
                BorshBuffer.allocate(Int.SIZE_BYTES + Long.SIZE_BYTES + rustStringLength + PUBKEY_LENGTH)
            buffer.writeU32(PROGRAM_INDEX_TRANSFER_WITH_SEED)
            buffer.writeU64(lamports)
            buffer.writeString(seed)
            buffer.write(programId.pubkey)

            return createTransactionInstruction(
                programId = PROGRAM_ID,
                keys = listOf(
                    AccountMeta(publicKey = fromPublicKey, isSigner = true, isWritable = true),
                    AccountMeta(publicKey = basePubkey, isSigner = false, isWritable = true),
                    AccountMeta(publicKey = toPublicKey, isSigner = false, isWritable = true)
                ),
                data = buffer.toByteArray()
            )

        } else {
            val buffer = BorshBuffer.allocate(Int.SIZE_BYTES + Long.SIZE_BYTES)
            buffer.writeU32(PROGRAM_INDEX_TRANSFER)
            buffer.writeU64(lamports)

            return createTransactionInstruction(
                programId = PROGRAM_ID,
                keys = listOf(
                    AccountMeta(publicKey = fromPublicKey, isSigner = true, isWritable = true),
                    AccountMeta(publicKey = toPublicKey, isSigner = false, isWritable = true)
                ),
                data = buffer.toByteArray()
            )

        }

    }
}