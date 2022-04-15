/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/19
 */

package com.portto.solana.web3.rpc.types


import com.portto.solana.web3.MessageHeader
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BlockRpcResult(
    @SerialName("blockHeight")
    val blockHeight: Long,
    @SerialName("blockTime")
    val blockTime: Long,
    @SerialName("blockhash")
    val blockhash: String,
    @SerialName("parentSlot")
    val parentSlot: Long,
    @SerialName("previousBlockhash")
    val previousBlockhash: String,
    @SerialName("transactions")
    val transactions: List<Transaction>
) {
    @Serializable
    data class Transaction(
        @SerialName("meta")
        val meta: Meta,
        @SerialName("transaction")
        val transaction: Transaction
    ) {
        @Serializable
        data class Meta(
            @SerialName("err")
            @Contextual val err: Any?,
            @SerialName("fee")
            val fee: Int,
            @SerialName("innerInstructions")
            val innerInstructions: List<InnerInstruction>,
            @SerialName("logMessages")
            val logMessages: List<String>,
            @SerialName("postBalances")
            val postBalances: List<Long>,
            @SerialName("postTokenBalances")
            val postTokenBalances: List<PostTokenBalance>,
            @SerialName("preBalances")
            val preBalances: List<Long>,
            @SerialName("preTokenBalances")
            val preTokenBalances: List<PreTokenBalance>,
            @SerialName("rewards")
            val rewards: List<@Contextual Any?>
        ) {
            @Serializable
            data class InnerInstruction(
                @SerialName("index")
                val index: Int,
                @SerialName("instructions")
                val instructions: List<Instruction>
            ) {
                @Serializable
                data class Instruction(
                    @SerialName("accounts")
                    val accounts: List<Int>,
                    @SerialName("data")
                    val `data`: String,
                    @SerialName("programIdIndex")
                    val programIdIndex: Int
                )
            }

            @Serializable
            data class PostTokenBalance(
                @SerialName("accountIndex")
                val accountIndex: Int,
                @SerialName("mint")
                val mint: String,
                @SerialName("owner")
                val owner: String,
                @SerialName("uiTokenAmount")
                val uiTokenAmount: UiTokenAmount
            ) {
                @Serializable
                data class UiTokenAmount(
                    @SerialName("amount")
                    val amount: String,
                    @SerialName("decimals")
                    val decimals: Int,
                    @SerialName("uiAmount")
                    val uiAmount: Double,
                    @SerialName("uiAmountString")
                    val uiAmountString: String
                )
            }

            @Serializable
            data class PreTokenBalance(
                @SerialName("accountIndex")
                val accountIndex: Int,
                @SerialName("mint")
                val mint: String,
                @SerialName("owner")
                val owner: String,
                @SerialName("uiTokenAmount")
                val uiTokenAmount: UiTokenAmount
            ) {
                @Serializable
                data class UiTokenAmount(
                    @SerialName("amount")
                    val amount: String,
                    @SerialName("decimals")
                    val decimals: Int,
                    @SerialName("uiAmount")
                    val uiAmount: Double,
                    @SerialName("uiAmountString")
                    val uiAmountString: String
                )
            }
        }

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
                val accountKeys: List<String>,
                @SerialName("header")
                val header: MessageHeader,
                @SerialName("instructions")
                val instructions: List<com.portto.solana.web3.CompiledInstruction>,
                @SerialName("recentBlockhash")
                val recentBlockhash: String
            ) {
/*
                @Serializable
                data class Header(
                    @SerialName("numReadonlySignedAccounts")
                    val numReadonlySignedAccounts: Int,
                    @SerialName("numReadonlyUnsignedAccounts")
                    val numReadonlyUnsignedAccounts: Int,
                    @SerialName("numRequiredSignatures")
                    val numRequiredSignatures: Int
                )
*/

/*
                @Serializable
                data class Instruction(
                    @SerialName("accounts")
                    val accounts: List<Int>,
                    @SerialName("data")
                    val `data`: String,
                    @SerialName("programIdIndex")
                    val programIdIndex: Int
                )
*/
            }
        }
    }
}