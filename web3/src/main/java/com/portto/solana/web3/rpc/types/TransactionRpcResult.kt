package com.portto.solana.web3.rpc.types

import com.portto.solana.web3.util.AnySerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TransactionRpcResult(val value: TransactionResponse? = null) : RpcResultObject() {

    @Serializable
    data class TransactionResponse(
        @SerialName("blockTime")
        val blockTime: Int,
        @SerialName("meta")
        val meta: ConfirmedTransactionMeta,
        @SerialName("slot")
        val slot: Int,
        @SerialName("transaction")
        val transaction: ConfirmedTransaction
    ) {
        @Serializable
        data class ConfirmedTransactionMeta(
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
            val rewards: List<String>,
            @SerialName("status")
            val status: Status
        ) {
            @Serializable
            data class Status(
                @SerialName("Ok")
                @Contextual val ok: Any
            )
        }

        @Serializable
        data class ConfirmedTransaction(
            @SerialName("message")
            val message: Message,
            @SerialName("signatures")
            val signatures: List<String>
        ) {
            @Serializable
            data class Message(
//                @SerialName("accountKeys")
//                val accountKeys: List<@Contextual Any>,
                @SerialName("accountKeys")
                val accountKeys: List<String>,
                @SerialName("instructions")
                val instructions: List<@Serializable(with = AnySerializer::class) Instruction>,
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
                    @Contextual val accounts: List<Int>,
                    @SerialName("data")
                    val `data`: String,
                    @SerialName("programId")
                    val programId: String? = null,
                    @SerialName("programIdIndex")
                    val programIdIndex: Int? = null
                )
            }
        }
    }
}
