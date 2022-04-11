/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/10
 */

package com.portto.solana.web3

import com.portto.solana.web3.rpc.types.TransactionRpcResult
import com.portto.solana.web3.rpc.types.LatestBlockhashRpcResult
import com.portto.solana.web3.rpc.types.config.Commitment
import com.portto.solana.web3.util.Cluster
import com.portto.solana.web3.rpc.RpcClient
import com.portto.solana.web3.rpc.types.RpcResultTypes
import com.portto.solana.web3.rpc.types.config.TransactionRpcConfig
import com.portto.solana.web3.util.json
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.bouncycastle.util.encoders.Base64

/**
 * Attempt to use a recent blockhash for up to 30 seconds
 * @internal
 */
const val BLOCKHASH_CACHE_TIMEOUT_MS = 30 * 1000

/**
 * Options for sending transactions
 */
@Serializable
data class SendOptions(
    /** disable transaction verification step */
    val skipPreflight: Boolean = false,
    /** preflight commitment level */
    val preflightCommitment: Commitment? = Commitment.FINALIZED,
    val encoding: String = "base64",
    /** Maximum number of times for the RPC node to retry sending the transaction to the leader. */
    val maxRetries: Int? = null
)

class Connection(private val endpoint: Cluster) {

    private val client by lazy { RpcClient(endpoint) }

    private var _recentBlockhash: Blockhash? = null
    private var _pollingBlockhash: Boolean = false
    private var lastFetch: Long = 0L

    private data class BlockhashInfo(
        val recentBlockhash: Blockhash? = null,
        val lastFetch: Long = 0L,
        val simulatedSignatures: MutableList<String> = mutableListOf(),
        val transactionSignatures: MutableList<String> = mutableListOf()
    )

    private var blockhashInfo = BlockhashInfo()

    suspend fun getBalanceAndContext() = { TODO() }

    suspend fun getBalance(account: PublicKey, commitment: Commitment? = null): Long {
        val params = mutableListOf<Any>()
        params.add(account.toString())
        commitment?.let {
            params.add(mapOf("commitment" to it.value))
        }
        return client.call("getBalance", params, RpcResultTypes.ValueLong.serializer()).value
    }

    suspend fun getTokenAccountsByOwner() = { TODO() }
    suspend fun getAccountInfoAndContext() = { TODO() }
    suspend fun getAccountInfo() = { TODO() }
    suspend fun getProgramAccounts() = { TODO() }
    suspend fun getParsedProgramAccounts() = { TODO() }
    suspend fun confirmTransaction() = { TODO() }
    suspend fun getSlot() = { TODO() }
    suspend fun getSignatureStatuses() = { TODO() }
    suspend fun getMinimumBalanceForRentExemption() = { TODO() }
    suspend fun getFeeCalculatorForBlockhash() = { TODO() }

    suspend fun getFeeForMessage(
        message: Message,
        commitment: Commitment? = null
    ): RpcResultTypes.ValueLong {
        val params = mutableListOf<Any>()
        val serializedTransaction = message.serialize()
        val base64Msg = Base64.toBase64String(serializedTransaction)
        params.add(base64Msg)
        commitment?.let {
            params.add(mapOf("commitment" to it.value))
        }
        return client.call("getFeeForMessage", params, RpcResultTypes.ValueLong.serializer())
    }

    @Deprecated(
        message = "Deprecated since Solana v1.8.0.",
        replaceWith = ReplaceWith(expression = "getLatestBlockhash(commitment)"),
        level = DeprecationLevel.WARNING
    )
    suspend fun getRecentBlockhash(commitment: Commitment? = null) = getLatestBlockhash(commitment)

    /**
     * Fetch the latest blockhash from the cluster
     * @property commitment (optional) Commitment (used for retrieving blockhash)
     * @return blockhash: <string> - a Hash as base-58 encoded string
     */
    suspend fun getLatestBlockhash(commitment: Commitment? = null): String? {
        val params = mutableListOf<Any>()
        commitment?.let {
            params.add(mapOf("commitment" to it.value))
        }
        return client.call(
            "getLatestBlockhash",
            params,
            LatestBlockhashRpcResult.serializer()
        ).value?.blockhash
    }

    /**
     * Fetch a confirmed or finalized transaction from the cluster.
     */
    suspend fun getTransaction(
        signature: String,
        config: TransactionRpcConfig? = null
    ): TransactionRpcResult.TransactionResponse {
        val params = mutableListOf<Any>()
        params.add(signature)
        config?.let {
            params.add(json.encodeToJsonElement(TransactionRpcConfig.serializer(), config))
//            it.encoding?.let { encoding -> params.add(mapOf("encoding" to encoding)) }
//            it.commitment?.let { commitment -> params.add(mapOf("commitment" to commitment)) }
//            it.commitment?.let { maxVersion -> params.add(mapOf("maxSupportedTransactionVersion" to maxVersion)) }
        }
        return client.call(
            "getTransaction",
            params,
            TransactionRpcResult.TransactionResponse.serializer()
        )
    }

    suspend fun getParsedTransaction() = { TODO() }
    suspend fun getParsedTransactions() = { TODO() }
    suspend fun getConfirmedBlock() = { TODO() }
    suspend fun getConfirmedBlockSignatures() = { TODO() }
    suspend fun getConfirmedTransaction() = { TODO() }
    suspend fun getParsedConfirmedTransaction() = { TODO() }
    suspend fun getParsedConfirmedTransactions() = { TODO() }
    suspend fun getSignaturesForAddress() = { TODO() }
    suspend fun getNonceAndContext() = { TODO() }
    suspend fun getNonce() = { TODO() }

    private suspend fun recentBlockhash(disableCache: Boolean): Blockhash = run {
        if (!disableCache) {
            // Wait for polling to finish
            while (this._pollingBlockhash) {
                delay(100)
            }
            val timeSinceFetch = System.currentTimeMillis() - this.lastFetch
            val expired = timeSinceFetch >= BLOCKHASH_CACHE_TIMEOUT_MS
            this._recentBlockhash?.let {
                if (!expired) return it
            }
        }

        return pollNewBlockhash()
    }

    private suspend fun pollNewBlockhash(): Blockhash = run {
        this@Connection._pollingBlockhash = true
        try {
            val startTime = System.currentTimeMillis()
            for (i in 0..50) {
                val blockhash = Blockhash().plus(getLatestBlockhash(Commitment.FINALIZED))

                if (this@Connection._recentBlockhash != blockhash) {
                    this@Connection._recentBlockhash = blockhash
                    this@Connection.lastFetch = System.currentTimeMillis()
                    this@Connection.blockhashInfo = BlockhashInfo(
                        recentBlockhash = blockhash,
                        lastFetch = System.currentTimeMillis(),
                        simulatedSignatures = mutableListOf(),
                        transactionSignatures = mutableListOf()
                    )
                    return@run blockhash
                }

                // Sleep for approximately half a slot
                delay(MS_PER_SLOT / 2)
            }

            throw Error("Unable to obtain a new blockhash after ${System.currentTimeMillis() - startTime}ms")
        } finally {
            this@Connection._pollingBlockhash = false
        }
    }

    suspend fun simulateTransaction() = { TODO() }

    /**
     * Sign and send a transaction
     */
    suspend fun sendTransaction(
        transaction: Transaction,
        signers: List<Signer>,
        options: SendOptions? = SendOptions()
    ): TransactionSignature {
        if (transaction.nonceInfo != null) {
            transaction.sign(signers)
        } else {
            var disableCache = /*this._disableBlockhashCaching*/ false
            while (true) {
                transaction.recentBlockhash = recentBlockhash(disableCache)
                transaction.sign(signers)
                if (transaction.signature == null) {
                    throw Error("!signature") // should never happen
                }

                val signature = Base64.toBase64String(transaction.signature)
                if (!this.blockhashInfo.transactionSignatures.contains(signature)) {
                    // The signature of this transaction has not been seen before with the
                    // current recentBlockhash, all done. Let's break
                    this.blockhashInfo.transactionSignatures.add(signature)
                    break
                } else {
                    // This transaction would be treated as duplicate (its derived signature
                    // matched to one of already recorded signatures).
                    // So, we must fetch a new blockhash for a different signature by disabling
                    // our cache not to wait for the cache expiration (BLOCKHASH_CACHE_TIMEOUT_MS).
                    disableCache = true
                }
            }
        }

        val wireTransaction = transaction.serialize()
        return sendRawTransaction(wireTransaction, options)
    }

    /**
     * Send a transaction that has already been signed and serialized into the
     * wire format
     */
    suspend fun sendRawTransaction(
        rawTransaction: ByteArray,
        options: SendOptions?
    ): TransactionSignature {
        val encodedTransaction = Base64.toBase64String(rawTransaction)
        return sendEncodedTransaction(encodedTransaction, options)
    }

    /**
     * Send a transaction that has already been signed, serialized into the
     * wire format, and encoded as a base64 string
     */
    suspend fun sendEncodedTransaction(
        encodedTransaction: String,
        options: SendOptions?,
    ): TransactionSignature {
        val config = options ?: SendOptions(encoding = "base64")

        val args = listOf(encodedTransaction, config)
        return client.call("sendTransaction", args, String.serializer())
    }

}