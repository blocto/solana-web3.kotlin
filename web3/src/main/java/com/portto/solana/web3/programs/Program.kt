package com.portto.solana.web3.programs

import com.portto.solana.web3.AccountMeta
import com.portto.solana.web3.PublicKey
import com.portto.solana.web3.TransactionInstruction

/**
 * Abstract class for
 */
abstract class Program {
    companion object {
        /**
         * Returns a [TransactionInstruction] built from the specified values.
         * @param programId Solana program we are calling
         * @param keys AccountMeta keys
         * @param data byte array sent to Solana
         * @return [TransactionInstruction] object containing specified values
         */
        @JvmStatic
        fun createTransactionInstruction(
            programId: PublicKey,
            keys: List<AccountMeta>,
            data: ByteArray
        ): TransactionInstruction {
            return TransactionInstruction(programId, keys, data)
        }
    }
}