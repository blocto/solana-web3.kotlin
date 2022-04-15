package com.portto.solana.web3.rpc.types

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
class TokenAccountsByOwnerRpcResult(val value: Value) : RpcResultObject() {

    @Serializable
    class Value : ArrayList<Value.ValueItem>() {
        @Serializable
        data class ValueItem(
            @SerialName("account")
            val account: Account,
            @SerialName("pubkey")
            val pubkey: String
        ) {
            @Serializable
            data class Account(
                @SerialName("data")
                val `data`: Data,
                @SerialName("executable")
                val executable: Boolean,
                @SerialName("lamports")
                val lamports: Long,
                @SerialName("owner")
                val owner: String,
                @SerialName("rentEpoch")
                val rentEpoch: Int
            ) {
                @Serializable
                data class Data(
                    @SerialName("program")
                    val program: String,
                    @SerialName("parsed")
                    val parsed: Parsed,
                    @SerialName("space")
                    val space: Int
                ) {
                    @Serializable
                    data class Parsed(
                        @SerialName("accountType")
                        val accountType: String,
                        @SerialName("info")
                        val info: Info,
                        @SerialName("type")
                        val type: String
                    ) {
                        @Serializable
                        data class Info(
                            @SerialName("tokenAmount")
                            val tokenAmount: TokenAmount,
                            @SerialName("delegate")
                            val `delegate`: String,
                            @SerialName("delegatedAmount")
                            val delegatedAmount: DelegatedAmount,
                            @SerialName("state")
                            val state: String,
                            @SerialName("isNative")
                            val isNative: Boolean,
                            @SerialName("mint")
                            val mint: String,
                            @SerialName("owner")
                            val owner: String
                        ) {
                            @Serializable
                            data class TokenAmount(
                                @SerialName("amount")
                                val amount: String,
                                @SerialName("decimals")
                                val decimals: Int,
                                @SerialName("uiAmount")
                                val uiAmount: Double,
                                @SerialName("uiAmountString")
                                val uiAmountString: String
                            )

                            @Serializable
                            data class DelegatedAmount(
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
                }
            }
        }
    }
}
