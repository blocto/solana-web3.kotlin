/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/10
 */

package com.portto.solana.web3

import com.portto.solana.web3.rpc.*
import com.portto.solana.web3.rpc.types.*
import com.portto.solana.web3.rpc.types.config.Commitment
import com.portto.solana.web3.rpc.types.config.Finality
import com.portto.solana.web3.rpc.types.config.TransactionRpcConfig
import com.portto.solana.web3.util.AnySerializer
import com.portto.solana.web3.util.Cluster
import com.portto.solana.web3.util.json
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.bitcoinj.core.Base58
import org.bouncycastle.util.encoders.Base64
import org.tinylog.kotlin.Logger


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

/***
 * Expected JSON RPC response for the "accountNotification" message
 */
@Serializable
class AccountNotificationResult(
    val subscription: Long,
    val result: AccountInfoRpcResult
)

/**
 * Expected JSON RPC response for the "signatureNotification" message
 */
@Serializable
class SignatureNotificationResult(
    val subscription: Long,
    val result: SignatureNotificationRpcResult
)

/**
 * RPC Response with extra contextual information
 */
class RpcResponseAndContext<T>(
    /** response context */
    context: Context,
    /** response value */
    val value: T
) : RpcResultObject(context)

/**
 * Configuration object for getProgramAccounts requests
 */
@Serializable
class GetProgramAccountsConfig(
    /** Optional commitment level */
    val commitment: Commitment? = null,
    /** Optional encoding for account data (default base64)
     * To use "jsonParsed" encoding, please refer to `getParsedProgramAccounts` in connection.ts
     * */
    val encoding: String? = "base64",
    /** Optional data slice to limit the returned account data */
    val dataSlice: DataSlice? = null,
    /** Optional array of filters to apply to accounts */
    val filters: Connection.GetProgramAccountsFilter? = null
)

/**
 * Data slice argument for getProgramAccounts
 */
@Serializable
class DataSlice(
    /** offset of data slice */
    val offset: Int,
    /** length of data slice */
    val length: Int,
)

@Serializable
class KeyedAccountInfoResult(
    val pubkey: String,
    val account: AccountInfoRpcResult.Value,
)

@Serializable
class SignaturesForAddressOptions(
    /**
     * Start searching backwards from this transaction signature.
     * @remark If not provided the search starts from the highest max confirmed block.
     */
    val before: TransactionSignature?,
    /** Search until this transaction signature is reached, if found before `limit`. */
    val until: TransactionSignature?,
    /** Maximum transaction signatures to return (between 1 and 1,000, default: 1,000). */
    val limit: Int = 1000
)

/**
 * A processed block fetched from the RPC API
 */
class BlockResponse(
    /** Blockhash of this block */
    val blockhash: Blockhash,
    /** Blockhash of this block's parent */
    val previousBlockhash: Blockhash,
    /** Slot index of this block's parent */
    val parentSlot: Long,
    /** Vector of transactions with status meta and original message */
    val transactions: List<Transaction>,
    /** The unix timestamp of when the block was processed */
    val blockTime: Long?,
) {
    class Transaction(
        @SerialName("meta")
        /** Metadata produced from the transaction */
        val meta: BlockRpcResult.Transaction.Meta?,
        @SerialName("transaction")
        val transaction: Transaction,
    ) {
        /** The transaction */
        class Transaction(
            /** The transaction message */
            val message: Message,
            /** The transaction signatures */
            val signatures: List<String>? = emptyList(),
        )

    }
}

class Connection(private val endpoint: Cluster, var commitment: Commitment? = null) {

    private val waitForSetId = mutableMapOf<String, Pair<String, Int>>()
    private val rpcClient by lazy { RpcClient(endpoint) }
    private val rpcWebSocket by lazy { RpcWebSocketClient(endpoint) }
    private var rpcWebSocketConnected: Boolean = false

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

    init {
        rpcWebSocket.init(webSocketListener())
    }

    private fun webSocketListener() = object : WebSocketListener() {
        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosing(webSocket, code, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            super.onFailure(webSocket, t, response)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            super.onMessage(webSocket, text)
            Logger.debug(text)
            if (waitForSetId.isNotEmpty()) {
                val rpcResponse =
                    json.decodeFromString(RpcResponse.serializer(Long.serializer()), text)
                if (waitForSetId.containsKey(rpcResponse.id)) {
                    val id = waitForSetId.remove(rpcResponse.id)
                    when (id?.first) {
                        "accountSubscribe" -> {
                            this@Connection._accountChangeSubscriptions[id.second]?.let { sub ->
                                try {
                                    if (sub.subscriptionId?.id == -1L) {
                                        // eslint-disable-next-line require-atomic-updates
                                        this@Connection._accountChangeSubscriptions[id.second]?.subscriptionId?.id =
                                            rpcResponse.result!!
                                    }
                                } catch (e: Exception) {
                                    if (sub.subscriptionId?.id == -1L) {
                                        // eslint-disable-next-line require-atomic-updates
                                        this@Connection._accountChangeSubscriptions[id.second]?.subscriptionId = null
                                    }
                                    e.printStackTrace()
                                }
                            }
                        }
                        "signatureSubscribe" -> {
                            this@Connection._signatureSubscriptions[id.second]?.let { sub ->
                                try {
                                    if (sub.subscriptionId?.id == null) {
                                        // eslint-disable-next-line require-atomic-updates
                                        this@Connection._signatureSubscriptions[id.second]?.subscriptionId = SubscriptionId(
                                            rpcResponse.result!!)
                                    }
                                } catch (e: Exception) {
                                    if (sub.subscriptionId?.id == -1L) {
                                        // eslint-disable-next-line require-atomic-updates
                                        this@Connection._signatureSubscriptions[id.second]?.subscriptionId = null
                                    }
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
                }
            }
            val rpcResponse = json.decodeFromString(WsRpcResponse.serializer(AnySerializer), text)
            when (rpcResponse.method) {
                "accountNotification" -> wsOnAccountNotification(text)
                "programNotification" -> TODO()
                "slotNotification" -> TODO()
                "slotsUpdatesNotification" -> TODO()
                "signatureNotification" -> wsOnSignatureNotification(text)
                "rootNotification" -> TODO()
                "logsNotification" -> TODO()
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            super.onMessage(webSocket, bytes)
        }

        override fun onOpen(webSocket: WebSocket, response: Response) {
            super.onOpen(webSocket, response)
            wsOnOpen()
        }
    }

    private fun wsOnOpen() {
        this@Connection.rpcWebSocketConnected = true
        this.updateSubscriptions()
    }

    private fun wsOnAccountNotification(notification: String) {
        val res = json.decodeFromString(
            WsRpcResponse.serializer(AccountNotificationResult.serializer()),
            notification
        ).params ?: return
        for (sub in this._accountChangeSubscriptions.values) {
            if (sub.subscriptionId?.id == res.subscription) {
                sub.callback(res.result.value!!, res.result.context)
                return
            }
        }
    }

    private fun wsOnSignatureNotification(notification: String) {
        val res = json.decodeFromString(
            WsRpcResponse.serializer(SignatureNotificationResult.serializer()),
            notification
        ).params ?: return
        for ((id, sub) in this._signatureSubscriptions) {
            if (sub.subscriptionId?.id == res.subscription) {
                if (res.result.value is SignatureReceivedResult) {
                    sub.callback(SignatureReceivedNotification(), res.result.context)
                } else {
                    // Signatures subscriptions are auto-removed by the RPC service so
                    // no need to explicitly send an unsubscribe message
                    this._signatureSubscriptions.remove(id)
                    this.updateSubscriptions()
                    sub.callback(
                        SignatureStatusNotification(
                            result = SignatureStatusResult(res.result.value),
                        ),
                        res.result.context,
                    )
                }
                return
            }
        }
    }


    /**
     * Fetch the balance for the specified public key, return with context
     */
    suspend fun getBalanceAndContext(
        account: PublicKey,
        commitment: Commitment? = null,
    ): RpcResultTypes.ValueLong {
        val params = mutableListOf<Any>()
        params.add(account.toString())
        commitment?.let { params.add(mapOf("commitment" to it.value)) }
        val res = rpcClient.request("getBalance", params, RpcResultTypes.ValueLong.serializer())
        res.error?.message?.let { throw Error("failed to get balance for ${account.toBase58()}: $it") }
        return res.result!!
    }

    /**
     * Fetch the balance for the specified public key
     */
    suspend fun getBalance(account: PublicKey, commitment: Commitment? = null): Long {
        return getBalanceAndContext(account, commitment).value
    }

    sealed class TokenAccountsFilter(val pubkey: String)
    class Mint(val mint: String) : TokenAccountsFilter(mint)
    class ProgramId(val programId: String) : TokenAccountsFilter(programId)

    /**
     * Fetch the slot of the lowest confirmed block that has not been purged from the ledger
     */
    suspend fun getFirstAvailableBlock(): Long {
        val res = rpcClient.request(
            "getFirstAvailableBlock",
            emptyList(),
            Long.serializer()
        )
        res.error?.let { throw Error("failed to get first available block: ${res.error.message}") }
        return res.result!!
    }

    /**
     * Fetch all the token accounts owned by the specified account
     *
     * @return {Promise<RpcResponseAndContext<Array<{pubkey: PublicKey, account: AccountInfo<Buffer>}>>>}
     */
    suspend fun getTokenAccountsByOwner(
        ownerAddress: String,
        filter: TokenAccountsFilter,
        commitment: Commitment? = null
    ): TokenAccountsByOwnerRpcResult {
        val params = mutableListOf<Any>()
        params.add(ownerAddress)
        when (filter) {
            is Mint -> params.add(mapOf("mint" to filter.mint))
            is ProgramId -> params.add(mapOf("programId" to filter.programId))
        }
        val optional = mutableMapOf<String, String>()
        optional["encoding"] = "base64"
        optional["commitment"] = "base64"
        commitment?.let { optional["commitment"] = it.value }
        params.add(optional)
        val res = rpcClient.request(
            "getTokenAccountsByOwner",
            params,
            TokenAccountsByOwnerRpcResult.serializer()
        )
        res.error?.message?.let { throw Error("failed to get token accounts owned by account ${ownerAddress}: ${res.error.message}") }
        return res.result!!
    }

    /**
     * Fetch all the account info for the specified public key, return with context
     */
    suspend fun getAccountInfoAndContext(
        publicKey: String,
        commitment: Commitment? = null,
        encoding: String? = null
    ): AccountInfoRpcResult {
        val params = mutableListOf<Any>()
        params.add(publicKey)
        commitment?.let {
            params.add(mapOf("commitment" to it.value))
        }
        params.add(mapOf("encoding" to (encoding ?: "base64")))
        return rpcClient.request("getAccountInfo", params, AccountInfoRpcResult.serializer()).result!!
    }

    /**
     * Fetch parsed account info for the specified public key
     */
    suspend fun getAccountInfo(
        publicKey: String,
        commitment: Commitment? = null,
        encoding: String? = null
    ): AccountInfoRpcResult.Value? {
        return getAccountInfoAndContext(publicKey, commitment, encoding).value
    }

    /**
     * Fetch all the accounts owned by the specified program id, return with context
     *
     * @return {Promise<Array<{pubkey: PublicKey, account: AccountInfo<Buffer>}>>}
     */
    suspend fun getProgramAccountsAndContext(
        programId: PublicKey,
        config: GetProgramAccountsConfig? = GetProgramAccountsConfig(),
    ): RpcResponseAndContext<List<KeyedAccountInfoResult>> {
        val params = mutableListOf<Any>()
        params.add(programId.toBase58())
        params.add(buildMap {
            config?.apply {
                commitment?.let { put("commitment", it.value) }
                encoding?.let { put("encoding", it) }
                put("withContext", true)
            }
        })
        val res = rpcClient.request(
            "getProgramAccounts",
            params,
            AccountInfoResult.serializer(ListSerializer(KeyedAccountInfoResult.serializer()))
        )
        res.error?.message?.let { throw Error("failed to get accounts owned by program ${programId.toBase58()}: $it") }
        return RpcResponseAndContext(res.result!!.context, res.result.value)
    }

    /**
     * Fetch all the accounts owned by the specified program id
     *
     * @return {Promise<Array<{pubkey: PublicKey, account: AccountInfo<Buffer>}>>}
     */
    suspend fun getProgramAccounts(
        programId: PublicKey,
        config: GetProgramAccountsConfig? = GetProgramAccountsConfig()
    ): List<KeyedAccountInfoResult> {
        return getProgramAccountsAndContext(programId, config).value
    }

    /**
     * Fetch and parse all the accounts owned by the specified program id
     *
     * @return {Promise<Array<{pubkey: PublicKey, account: AccountInfo<Buffer | ParsedAccountData>}>>}
     */
    suspend fun getParsedProgramAccounts(
        programId: PublicKey,
        config: GetProgramAccountsConfig? = GetProgramAccountsConfig()
    ): AccountInfoResult<List<KeyedAccountInfoResult>>? {
        val params = mutableListOf<Any>()
        params.add(programId.toBase58())
        params.add(buildMap {
            config?.apply {
                commitment?.let { put("commitment", it.value) }
                put("encoding", "jsonParsed")
                put("withContext", false)
            }
        })
        val res = rpcClient.request(
            "getProgramAccounts",
            params,
            AccountInfoResult.serializer(ListSerializer(KeyedAccountInfoResult.serializer()))
        )
        res.error?.message?.let { throw Error("failed to get accounts owned by program ${programId.toBase58()}: $it") }
        return res.result
    }

    /**
     * Confirm the transaction identified by the specified signature.
     */
    suspend fun confirmTransaction(
        signature: TransactionSignature,
        commitment: Commitment? = null
    ): RpcResponseAndContext<SignatureResult?>? {
        val decodedSignature = try {
            Base58.decode(signature)
        } catch (e: Exception) {
            throw Error("signature must be base58 encoded: $signature")
        }

        assert(decodedSignature.count() == 64) { "signature has invalid length" }

        val start = System.currentTimeMillis()
        val subscriptionCommitment = commitment ?: this.commitment

        var subscriptionId: Int? = null
        var response: RpcResponseAndContext<SignatureResult?>? = null

        val confirmPromise = callbackFlow {
            var id: Int? = null
            val callback = object : SignatureResultCallback {
                override fun invoke(
                    signatureResult: SignatureResult?,
                    context: RpcResultObject.Context,
                ) {
                    subscriptionId = null
                    response = RpcResponseAndContext(
                        value = signatureResult,
                        context = context,
                    )
                    trySend(id ?: -1)
                    channel.close()
                }
            }
            id = this@Connection.onSignature(
                signature = signature,
                callback = callback,
                commitment = subscriptionCommitment,
            )
            awaitClose {
//                Logger.debug("awaitClose and id$id")
            }
        }

        confirmPromise.collect {
            subscriptionId = it
        }

        subscriptionId?.let { this.removeSignatureListener(it) }

        if (response == null) {
            val duration = (System.currentTimeMillis() - start) / 1000
            throw Error("Transaction was not confirmed in $duration seconds. It is unknown if it succeeded or failed. Check signature $signature using the Solana Explorer or CLI tools.")
        }

        return response
    }

    /**
     * Deregister a signature notification callback
     *
     * @param id subscription id to deregister
     */
    fun removeSignatureListener(id: Int) {
        if (this._signatureSubscriptions[id] != null) {
            this._signatureSubscriptions.remove(id)?.let { subInfo ->
                subInfo.subscriptionId?.let {
                    rpcWebSocket.unsubscribe(it, "signatureUnsubscribe")
                    this.updateSubscriptions()
                }
            }
        }
    }

    /**
     * Fetch the current slot that the node is processing
     */
    suspend fun getSlot(commitment: Commitment? = null): Long {
        val params = mutableListOf<Any>()
        commitment?.let { params.add(mapOf("commitment" to it.value)) }
        val res = rpcClient.request(
            "getSlotLeader",
            params,
            Long.serializer()
        )
        res.error?.let { throw Error("failed to get slot: ${res.error.message}") }
        return res.result!!
    }

    /**
     * Fetch the current status of a signature
     */
    suspend fun getSignatureStatus(
        signature: TransactionSignature,
        /** enable searching status history, not needed for recent transactions */
        searchTransactionHistory: Boolean? = false
    ): RpcResponseAndContext<SignatureStatusesRcpResult.SignatureStatus?> {
        val result = this.getSignatureStatuses(
            listOf(signature),
            searchTransactionHistory
        )
        assert(result.value.count() == 1)
        val value = result.value.first()
        return RpcResponseAndContext(result.context, value)
    }

    /**
     * Fetch the current status of a signature
     */
    suspend fun getSignatureStatuses(
        signature: List<TransactionSignature>,
        /** enable searching status history, not needed for recent transactions */
        searchTransactionHistory: Boolean? = false
    ): SignatureStatusesRcpResult {
        val params = mutableListOf<Any>(signature)
        searchTransactionHistory?.let { params.add(mapOf("searchTransactionHistory" to it)) }
        val res = rpcClient.request(
            "getSignatureStatuses",
            params,
            SignatureStatusesRcpResult.serializer()
        )
        res.error?.message?.let { throw Error("failed to get signature status: " + res.error.message) }
        return res.result!!
    }

    /**
     * Fetch the minimum balance needed to exempt an account of `dataLength`
     * size from rent
     */
    suspend fun getMinimumBalanceForRentExemption(
        dataLength: Int,
        commitment: Commitment? = null
    ): Long {
        val params = mutableListOf<Any>()
        params.add(dataLength)
        commitment?.let {
            params.add(mapOf("commitment" to it.value))
        }
        val res = rpcClient.request(
            "getMinimumBalanceForRentExemption",
            params,
            Long.serializer()
        )
        res.error?.message?.let { Logger.warn("Unable to fetch minimum balance for rent exemption") }
        return res.result ?: 0
    }

    /**
     * Returns whether a blockhash is still valid or not
     */
    suspend fun isBlockhashValid(
        blockhash: String,
        commitment: Commitment? = null
    ): Boolean {
        val params = mutableListOf<Any>()
        params.add(blockhash)
        commitment?.let { params.add(mapOf("commitment" to it.value)) }
        val res = rpcClient.request(
            methodName = "isBlockhashValid",
            args = params,
            deserializer = RpcResultTypes.ValueBoolean.serializer()
        )
        return res.result!!.value
    }

    /**
     * Fetch the fee for a message from the cluster, return with context
     */
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
        val res = rpcClient.request(
            methodName = "getFeeForMessage",
            args = params,
            deserializer = RpcResultTypes.ValueLong.serializer()
        )
        res.error?.message?.let { throw Error("failed to get slot: $it") }
        res.result ?: throw Error("invalid blockhash")
        return res.result
    }

    /**
     * Request an allocation of lamports to the specified address
     *
     * ```typescript
     * import { Connection, PublicKey, LAMPORTS_PER_SOL } from "@solana/web3.js";
     *
     * (async () => {
     *   const connection = new Connection("https://api.testnet.solana.com", "confirmed");
     *   const myAddress = new PublicKey("2nr1bHFT86W9tGnyvmYW4vcHKsQB3sVQfnddasz4kExM");
     *   const signature = await connection.requestAirdrop(myAddress, LAMPORTS_PER_SOL);
     *   await connection.confirmTransaction(signature);
     * })();
     * ```
     */
    suspend fun requestAirdrop(
        to: PublicKey,
        lamports: Long,
    ): String {
        val res = rpcClient.request(
            methodName = "requestAirdrop",
            args = listOf<Any>(to.toBase58(), lamports),
            deserializer = String.serializer()
        )
        res.error?.message?.let { throw Error("airdrop to ${to.toBase58()} failed: $it") }
        return res.result!!
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
        val res = rpcClient.request(
            "getLatestBlockhash",
            params,
            LatestBlockhashRpcResult.serializer()
        )
        return res.result!!.value!!.blockhash
    }

    /**
     * Fetch a processed block from the cluster.
     */
    suspend fun getBlock(
        slot: Long,
        commitment: Finality? = Finality.FINALIZED
    ): BlockResponse? {
        val params = mutableListOf<Any>(slot)
        commitment?.let {
            params.add(mapOf("commitment" to it.value))
        }
        val res = rpcClient.request(
            "getBlock",
            params,
            BlockRpcResult.serializer()
        )
        res.error?.message?.let { throw Error("failed to get confirmed block: $it") }
        val result = res.result ?: return null
        return BlockResponse(
            blockhash = result.blockhash,
            previousBlockhash = result.previousBlockhash,
            parentSlot = result.parentSlot,
            blockTime = result.blockTime,
            transactions = result.transactions.map { (meta, transaction) ->
                val message = Message(
                    header = transaction.message.header,
                    accountKeys = transaction.message.accountKeys.map { PublicKey(it) },
                    recentBlockhash = transaction.message.recentBlockhash.let { Blockhash().plus(it) },
                    transaction.message.instructions
                )
                BlockResponse.Transaction(
                    transaction = BlockResponse.Transaction.Transaction(
                        message = message,
                        signatures = transaction.signatures
                    ),
                    meta = meta
                )
            }
        )
    }

    /**
     * Fetch a confirmed or finalized transaction from the cluster.
     */
    suspend fun getTransaction(
        signature: String,
        config: TransactionRpcConfig? = null
    ): TransactionRpcResult.TransactionResponse? {
        val params = mutableListOf<Any>()
        params.add(signature)
        config?.let {
            params.add(json.encodeToJsonElement(TransactionRpcConfig.serializer(), config))
//            it.encoding?.let { encoding -> params.add(mapOf("encoding" to encoding)) }
//            it.commitment?.let { commitment -> params.add(mapOf("commitment" to commitment)) }
//            it.commitment?.let { maxVersion -> params.add(mapOf("maxSupportedTransactionVersion" to maxVersion)) }
        }
        val res = rpcClient.request(
            "getTransaction",
            params,
            TransactionRpcResult.TransactionResponse.serializer()
        )
        res.error?.message?.let { throw Error("failed to get transaction: $it") }
        return res.result
    }

    /**
     * Fetch parsed transaction details for a confirmed or finalized transaction
     */
    suspend fun getParsedTransaction(
        signature: TransactionSignature,
        commitment: Finality? = Finality.FINALIZED,
    ): ParsedTransactionWithMeta? {
        val params = mutableListOf<Any>(signature)
        val optionals = mutableMapOf(
            "encoding" to "jsonParsed",
        )
        commitment?.let { optionals.put("commitment", it.value) }
        params.add(optionals)
        return rpcClient.request(
            "getTransaction",
            params,
            ParsedTransactionWithMeta.serializer()
        ).result
    }

    /**
     * Fetch parsed transaction details for a batch of confirmed transactions
     */
    suspend fun getParsedTransactions(
        signatures: List<TransactionSignature>,
        commitment: Finality? = Finality.FINALIZED,
    ): List<ParsedTransactionWithMeta?> {
        val batch = signatures.map { signature ->
            val params = mutableListOf<Any>(signature)
            val optionals = mutableMapOf(
                "encoding" to "jsonParsed",
            )
            commitment?.let { optionals.put("commitment", it.value) }
            params.add(optionals)
            return@map RpcRequest("getTransaction", params)
        }
        return rpcClient.requestBatch(
            batch,
            ParsedTransactionWithMeta.serializer()
        ).map { it.result }
    }

    /**
     * Fetch a list of Signatures from the cluster for a block, excluding rewards
     */
    suspend fun getBlockSignatures(
        slot: Long,
        commitment: Finality? = Finality.FINALIZED,
    ): BlockSignatures {
        val params = mutableListOf<Any>(slot)
        val optionals = mutableMapOf(
            "encoding" to "jsonParsed",
            "transactionDetails" to "signatures",
            "rewards" to false
        )
        commitment?.let { optionals.put("commitment", it.value) }
        params.add(optionals)
        val res = rpcClient.request(
            "getBlock",
            params,
            BlockSignaturesRpcResult.serializer()
        )
        res.error?.message?.let { throw Error("failed to get block: $it") }
        return res.result!!
    }

    /**
     * Returns confirmed signatures for transactions involving an
     * address backwards in time from the provided signature or most recent confirmed block
     *
     *
     * @param address queried address
     * @param options
     */
    suspend fun getSignaturesForAddress(
        address: PublicKey,
        options: SignaturesForAddressOptions?,
        commitment: Finality?, // 'confirmed' | 'finalized',
    ): List<ConfirmedSignatureInfo> {
        val params = mutableListOf<Any>()
        params.add(address.toBase58())
        options?.let {
            params.add(options)
        }
        commitment?.let {
            params.add(mapOf("commitment" to it.value))
        }
        val res = rpcClient.request(
            methodName = "getSignaturesForAddress",
            args = params,
            deserializer = ListSerializer(ConfirmedSignatureInfo.serializer()),
        )
        res.error?.message?.let { throw Error("failed to get signatures for address: $it") }
        return res.result!!
    }

    /**
     * Fetch the contents of a Nonce account from the cluster, return with context
     */
    suspend fun getNonceAndContext(
        nonceAccount: PublicKey,
        commitment: Commitment? = null,
    ): RpcResponseAndContext<NonceAccount?> {
        val result = this.getAccountInfoAndContext(
            nonceAccount.toBase58(),
            commitment
        )

        val accountInfo = result.value
        val value = accountInfo?.let {
            NonceAccount.fromAccountData(Base64.decode(it.data.first()))
        }

        return RpcResponseAndContext(result.context, value)
    }

    /**
     * Fetch the contents of a Nonce account from the cluster
     */
    suspend fun getNonce(
        nonceAccount: PublicKey,
        commitment: Commitment? = null,
    ): NonceAccount? {
        return this.getNonceAndContext(nonceAccount, commitment).value
    }

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

    /**
     * Simulate a transaction
     */
    suspend fun simulateTransaction(
        message: Message,
        signers: List<Signer>?,
        includeAccounts: Any?,
    ): RpcResponseAndContext<SimulatedTransactionResponse.Value> =
        simulateTransaction(Transaction.populate(message), signers, includeAccounts)

    /**
     * Simulate a transaction
     */
    suspend fun simulateTransaction(
        transaction: Transaction,
        signers: List<Signer>?,
        includeAccounts: Any?,
    ): RpcResponseAndContext<SimulatedTransactionResponse.Value> {
        if (transaction.nonceInfo != null && !signers.isNullOrEmpty()) {
            transaction.sign(signers)
        } else {
            var disableCache = /*this._disableBlockhashCaching*/ false
            while (true) {
                transaction.recentBlockhash = recentBlockhash(disableCache)

                if (signers.isNullOrEmpty()) break

                transaction.sign(signers)
                if (transaction.signature == null) {
                    throw Error("!signature") // should never happen
                }

                val signature = Base64.toBase64String(transaction.signature)
                if (!this.blockhashInfo.simulatedSignatures.contains(signature) &&
                    !this.blockhashInfo.transactionSignatures.contains(signature)) {
                    // The signature of this transaction has not been seen before with the
                    // current recentBlockhash, all done. Let's break
                    this.blockhashInfo.simulatedSignatures.add(signature)
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

        val message = transaction.compile()
        val signData = message.serialize()
        val wireTransaction = transaction.serialize(signData)
        val encodedTransaction = Base64.toBase64String(wireTransaction)
        val config = /*mutableListOf<Any>(
            mutableMapOf<String, Any?>(
                "encoding" to "base64",
                "commitment" to this.commitment?.value
            )
        )
*/
            mutableMapOf<String, Any?>(
                "encoding" to "base64",
                "commitment" to this.commitment?.value
            )

        if (includeAccounts != null) {
            val addresses = when (includeAccounts) {
                is List<*> -> includeAccounts as List<PublicKey>
                is Boolean -> if (includeAccounts) message.nonProgramIds() else throw Exception()
                else -> throw Exception()
            }.map { key ->
                key.toBase58()
            }

            config["accounts"] = mapOf(
                "encoding" to "base64",
                "addresses" to addresses
            )
        /*listOf(
                mapOf("encoding" to "base64"),
                addresses
            )
*/        }

        if (!signers.isNullOrEmpty()) {
            config["sigVerify"] = true
        }

        val args = listOf(encodedTransaction, config)
        val res = rpcClient.request(
            methodName = "simulateTransaction",
            args = args,
            deserializer = SimulatedTransactionResponse.serializer(),
        )
        res.error?.message?.let { throw Error("failed to simulate transaction: $it") }
        return RpcResponseAndContext(res.result!!.context, res.result.value)
    }

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
        val res = rpcClient.request("sendTransaction", args, String.serializer())
        res.error?.message?.let { throw Error("failed to send transaction: $it") }
        return res.result!!
    }

    /**
     * Signature subscription options
     */
    @Serializable
    class SignatureSubscriptionOptions(
        val commitment: Commitment? = null,
        val enableReceivedNotification: Boolean? = null
    )

    sealed class SignatureNotification(val type: String)
    class SignatureStatusNotification(val result: SignatureResult?) : SignatureNotification(type = "status")
    class SignatureReceivedNotification : SignatureNotification(type = "received")

    class SubscriptionId(var id: Long)

    private data class SignatureSubscriptionInfo(
        val signature: TransactionSignature,// TransactionSignature as a base 58 string
        val callback: SignatureSubscriptionCallback,
        val options: SignatureSubscriptionOptions?,
        var subscriptionId: SubscriptionId? // null when there's no current server subscription id
    )

    class AccountSubscriptionInfo(
        val publicKey: String, // PublicKey of the account as a base 58 string
        val callback: AccountChangeCallback,
        val commitment: Commitment?,
        var subscriptionId: SubscriptionId? // null when there's no current server subscription id
    )

    /**
     * Information about the latest slot being processed by a node
     */
    class SlotInfo(
        /** Currently processing slot */
        slot: Long,
        /** Parent of the current slot */
        parent: Int,
        /** The root block of the current slot's fork */
        root: Int
    )

    /**
     * A filter object for getProgramAccounts
     */
    @Serializable
    sealed class GetProgramAccountsFilter
    class MemcmpFilter(val offset: Int, val bytes: String) : GetProgramAccountsFilter()
    class DataSizeFilter(val dataSize: Int) : GetProgramAccountsFilter()

    private class ProgramAccountSubscriptionInfo(
        programId: String, // PublicKey of the program as a base 58 string
        callback: ProgramAccountChangeCallback,
        commitment: Commitment?,
        subscriptionId: SubscriptionId?, // null when there's no current server subscription id
        filters: List<GetProgramAccountsFilter>?,
    )

    private class SlotSubscriptionInfo(
        callback: SlotChangeCallback,
        subscriptionId: SubscriptionId? // null when there's no current server subscription id
    )

    private class SlotUpdateSubscriptionInfo(
        callback: SlotUpdateCallback,
        subscriptionId: SubscriptionId? // null when there's no current server subscription id
    )

    /**
     * Information describing an account
     */
    class AccountInfo(
        /** `true` if this account's data contains a loaded program */
        val executable: Boolean,
        /** Identifier of the program that owns the account */
        val owner: PublicKey,
        /** Number of lamports assigned to the account */
        val lamports: Long,
        /** Optional data assigned to the account */
        val data: List<String>,
        /** Optional rent epoch info for account */
        val rentEpoch: Long?
    )

    /**
     * Account information identified by pubkey
     */
    class KeyedAccountInfo(
        accountId: PublicKey,
        accountInfo: AccountInfo,
    )

    class RootSubscriptionInfo(
        callback: RootChangeCallback,
        subscriptionId: SubscriptionId? // null when there's no current server subscription id
    )

    /**
     * Logs result.
     */
    class Logs(
        err: String,
        logs: List<String>,
        signature: String
    )

    class LogsSubscriptionInfo(
        callback: LogsCallback,
        filter: LogsFilter,
        subscriptionId: SubscriptionId, // null when there's no current server subscription id
        commitment: Commitment? = null
    )

    private var _accountChangeSubscriptionCounter: Int = 0
    private var _signatureSubscriptionCounter: Int = 0
    private val _signatureSubscriptions = mutableMapOf<Int, SignatureSubscriptionInfo>()
    private val _accountChangeSubscriptions = mutableMapOf<Int, AccountSubscriptionInfo>()
    private val _programAccountChangeSubscriptions = mutableMapOf<Int, ProgramAccountSubscriptionInfo>()
    private val _slotSubscriptions = mutableMapOf<Int, SlotSubscriptionInfo>()
    private val _slotUpdateSubscriptions = mutableMapOf<Int, SlotUpdateSubscriptionInfo>()
    private val _rootSubscriptions = mutableMapOf<Int, RootSubscriptionInfo>()
    private val _logsSubscriptions = mutableMapOf<Int, LogsSubscriptionInfo>()

    /**
     * Register a callback to be invoked upon signature updates
     *
     * @param signature Transaction signature string in base 58
     * @param callback Function to invoke on signature notifications
     * @param commitment Specify the commitment level signature must reach before notification
     * @return subscription id
     */
    fun onSignature(
        signature: TransactionSignature,
        callback: SignatureResultCallback,
        commitment: Commitment? = null
    ): Int {
        val id = ++this._signatureSubscriptionCounter
        this._signatureSubscriptions[id] = SignatureSubscriptionInfo(
            signature = signature,
            callback = { notification, context ->
                if (notification is SignatureStatusNotification && notification.type == "status") {
                    callback(notification.result, context)
                }
            },
            options = SignatureSubscriptionOptions(commitment),
            subscriptionId = null
        )
        this.updateSubscriptions()
        return id
    }

    /**
     * Register a callback to be invoked whenever the specified account changes
     *
     * @param publicKey Public key of the account to monitor
     * @param callback Function to invoke whenever the account is changed
     * @param commitment Specify the commitment level account changes must reach before notification
     * @return subscription id
     */
    fun onAccountChange(
        publicKey: PublicKey,
        callback: AccountChangeCallback,
        commitment: Commitment? = null,
    ): Int {
        val id = ++this._accountChangeSubscriptionCounter
        this._accountChangeSubscriptions[id] = AccountSubscriptionInfo(
            publicKey = publicKey.toBase58(),
            callback = callback,
            commitment = commitment,
            subscriptionId = null
        )
        updateSubscriptions()
        return id
    }

    private fun updateSubscriptions() {
        val accountKeys = this._accountChangeSubscriptions.keys
        val programKeys = this._programAccountChangeSubscriptions.keys
        val slotKeys = this._slotSubscriptions.keys
        val slotUpdateKeys = this._slotUpdateSubscriptions.keys
        val signatureKeys = this._signatureSubscriptions.keys
        val rootKeys = this._rootSubscriptions.keys
        val logsKeys = this._logsSubscriptions.keys
        if (
            accountKeys.count() == 0 &&
            programKeys.count() == 0 &&
            slotKeys.count() == 0 &&
            slotUpdateKeys.count() == 0 &&
            signatureKeys.count() == 0 &&
            rootKeys.count() == 0 &&
            logsKeys.count() == 0
        ) {
            if (rpcWebSocketConnected) {
                rpcWebSocketConnected = false
                rpcWebSocket.close()
            }
            return
        }

        if (!rpcWebSocketConnected) {
            rpcWebSocket.init(webSocketListener())
            return
        }

        for (id in accountKeys) {
            val sub = this._accountChangeSubscriptions[id] ?: break
            val params = mutableListOf<Any>()
            params.add(sub.publicKey)
            params.add(mutableMapOf("encoding" to "base64").apply {
                sub.commitment?.let { put("commitment", it.value) }
            })
            if (sub.subscriptionId == null) {
                this._accountChangeSubscriptions[id]?.subscriptionId = SubscriptionId(-1L)
                waitForSetId[rpcWebSocket.subscribe("accountSubscribe", params)] = "accountSubscribe" to id
            }
        }

        for (id in signatureKeys) {
            val sub = this._signatureSubscriptions[id] ?: break
            val args = mutableListOf<Any>(sub.signature)
            sub.options?.commitment?.let { args.add(mapOf("commitment" to it.value)) }
            waitForSetId[rpcWebSocket.subscribe("signatureSubscribe", args)] = "signatureSubscribe" to id
/*
             runBlocking {
                 rpcWebSocket.callFlow("signatureSubscribe", args).collect { subscriptionId ->
                     try {
                         if (sub.subscriptionId?.id == null) {
                             // eslint-disable-next-line require-atomic-updates
                             this@Connection._signatureSubscriptions[id]?.subscriptionId = SubscriptionId(subscriptionId.toLong())
                         }
                     } catch (e: Exception) {
                         if (sub.subscriptionId?.id == -1L) {
                             // eslint-disable-next-line require-atomic-updates
                             this@Connection._signatureSubscriptions[id]?.subscriptionId = null
                         }
                         e.printStackTrace()
                     }
                 }
            }
*/
        }
    }

    private suspend fun subscribe(
        subscriptionId: SubscriptionId? = null,
        rpcMethod: String,
        rpcArgs: MutableList<Any>
    ) {
        val id = rpcWebSocket.subscribe(rpcMethod, rpcArgs)
    }
}

/**
 * Callback function for signature status notifications
 */
typealias SignatureResultCallback = (signatureResult: SignatureResult?, context: RpcResultObject.Context) -> Unit

/**
 * Callback function for signature notifications
 */
typealias SignatureSubscriptionCallback = (notification: Connection.SignatureNotification, context: RpcResultObject.Context) -> Unit

/**
 * Callback function for account change notifications
 */
typealias AccountChangeCallback = (accountInfo: AccountInfoRpcResult.Value, context: RpcResultObject.Context) -> Unit

/**
 * Callback function for program account change notifications
 */
typealias ProgramAccountChangeCallback = (keyedAccountInfo: Connection.KeyedAccountInfo, context: RpcResultObject.Context) -> Unit

/**
 * Callback function for slot change notifications
 */
typealias SlotChangeCallback = (slotInfo: Connection.SlotInfo) -> Unit

/**
 * Callback function for slot update notifications
 */
typealias SlotUpdateCallback = (slotUpdate: SlotUpdate) -> Unit

typealias SlotUpdate = String

/**
 * Callback function for root change notifications
 */
typealias RootChangeCallback = (root: Int) -> Unit

/**
 * Callback function for log notifications.
 */
typealias LogsCallback = (logs: Connection.Logs, ctx: RpcResultObject.Context) -> Unit

typealias LogsFilter = String