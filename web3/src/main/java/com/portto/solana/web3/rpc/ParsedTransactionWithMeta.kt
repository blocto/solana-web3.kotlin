/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/19
 */

package com.portto.solana.web3.rpc
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

import kotlinx.serialization.SerialName


@Serializable
data class ParsedTransactionWithMeta(
    @SerialName("blockTime")
    val blockTime: Long,
    @SerialName("meta")
    val meta: Meta,
    @SerialName("slot")
    val slot: Long,
    @SerialName("transaction")
    val transaction: Transaction
) {
    @Serializable
    data class Meta(
        @SerialName("err")
        @Contextual val err: Any,
        @SerialName("fee")
        val fee: Int,
        @SerialName("innerInstructions")
        val innerInstructions: List<String>,
        @SerialName("logMessages")
        val logMessages: List<String>,
        @SerialName("postBalances")
        val postBalances: List<Int>,
        @SerialName("postTokenBalances")
        val postTokenBalances: List<String>,
        @SerialName("preBalances")
        val preBalances: List<Int>,
        @SerialName("preTokenBalances")
        val preTokenBalances: List<String>,
        @SerialName("rewards")
        val rewards: List<@Contextual Any>
    )

    @Serializable
    data class Transaction(
        @SerialName("message")
        val message: Message,
        @SerialName("signatures")
        val signatures: List<String>
    ) {
        @Serializable
        data class Message(
            @SerialName("accountKeys")
            val accountKeys: List<AccountKey>,
            @SerialName("instructions")
            val instructions: List<Instruction>,
            @SerialName("recentBlockhash")
            val recentBlockhash: String
        ) {
            @Serializable
            data class AccountKey(
                @SerialName("pubkey")
                val pubkey: String,
                @SerialName("signer")
                val signer: Boolean,
                @SerialName("writable")
                val writable: Boolean
            )

            @Serializable
            data class Instruction(
                @SerialName("accounts")
                val accounts: List<String>,
                @SerialName("data")
                val `data`: String,
                @SerialName("programId")
                val programId: String
            )
        }
    }
}