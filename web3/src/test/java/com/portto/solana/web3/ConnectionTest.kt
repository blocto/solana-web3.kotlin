/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/12
 */

@file:OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)

package com.portto.solana.web3

import com.portto.solana.web3.programs.SystemProgram
import com.portto.solana.web3.rpc.types.config.Commitment
import com.portto.solana.web3.util.Cluster
import com.portto.solana.web3.util.json
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import org.bitcoinj.core.Base58
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.tinylog.kotlin.Logger

class ConnectionTest {

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    private val connection by lazy { Connection(Cluster.DEVNET) }

    @Rule
    @JvmField
    var watcher: TestRule = object : TestWatcher() {
        override fun starting(description: Description) {
            Logger.debug("Starting test: " + description.methodName)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // reset the main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    @Test
    fun `get minimum balance for rent exemption`() = runBlocking {
        assertTrue(connection.getMinimumBalanceForRentExemption(512) >= 0)
    }

    @Test
    fun `get latest blockhash`() = runBlocking {
        val commitments = enumValues<Commitment>()
        for (commitment in commitments) {
            val blockhash = connection.getLatestBlockhash(commitment)
            assertEquals(Base58.decode(blockhash).count(), 32)
        }
    }

    @Test
    fun `get balance`() = runBlocking {
        val account = KeyPair.generate()
        val balance = connection.getBalance(account.publicKey)
        assertTrue(balance >= 0)
    }

    @Test
    fun getNonceAndContext() = runBlocking {
        val nonceAccount = PublicKey("EWWqpa8SBHVJXq8q5BFokfmgSHUXYqPFzhiFGTiA85y7")
        val connection = Connection(Cluster.MAINNET_BETA)
        val nonce = connection.getNonceAndContext(nonceAccount)
        assertEquals(nonce.value?.authorizedPubkey, PublicKey("9nUNwTjTZASbbK9cigF7WKbo16oQ97uDG5kb38j4nd8H"))
        assertEquals(nonce.value?.feeCalculator?.lamportsPerSignature, 5000L)
    }

    @Test
    fun `simulate transaction with message`() {
        val times = 15
        runBlocking {
            connection.commitment = Commitment.CONFIRMED

            val account1 = KeyPair.generate()
            val account2 = KeyPair.generate()

            connection.requestAirdrop(
                to = account1.publicKey,
                lamports = LAMPORTS_PER_SOL,
            )

            repeat(times) {
                delay(1000)
                Logger.debug(it)
            }
            connection.requestAirdrop(
                to = account2.publicKey,
                lamports = LAMPORTS_PER_SOL,
            )

            repeat(times) {
                delay(1000)
                Logger.debug(it)
            }
            val recentBlockhash = connection.getLatestBlockhash()
            val message = Message(
                accountKeys = listOf(
                    account1.publicKey,
                    account2.publicKey,
                    PublicKey("Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo")
                ),
                header = MessageHeader().apply {
                    numReadonlySignedAccounts = 1
                    numReadonlyUnsignedAccounts = 2
                    numRequiredSignatures = 1
                },
                instructions = listOf(
                    CompiledInstruction(
                        accounts = listOf(0, 1),
                        data = Base58.encode(ByteArray(5).apply { fill(9) }),
                        programIdIndex = 2
                    )
                ),
                recentBlockhash = Blockhash().plus(recentBlockhash)
            )

            repeat(times) {
                delay(1000)
                Logger.debug(it)
            }
            val results1 = connection.simulateTransaction(
                message = message,
                signers = listOf(account1),
                includeAccounts = true,
            )

            assertEquals(results1.value.accounts?.count(), 2)

            repeat(times) {
                delay(1000)
                Logger.debug(it)
            }
            val results2 = connection.simulateTransaction(
                message,
                listOf(account1),
                listOf(account1.publicKey, PublicKey("Missing111111111111111111111111111111111111")
                )
            )

            assertEquals(results2.value.accounts?.count(), 2)
            if (!results2.value.accounts.isNullOrEmpty()) {
                assertNull(results2.value.accounts!![1])
            }
        }
    }

    @Test
    fun `multi-instruction transaction`() = runBlocking {

//        connection.commitment = Commitment.CONFIRMED

        val accountFrom = KeyPair.generate()
        val accountTo = KeyPair.generate()

        var signature = connection.requestAirdrop(
            accountFrom.publicKey,
            LAMPORTS_PER_SOL,
        )
        connection.confirmTransaction(signature)
        assertEquals(connection.getBalance(accountFrom.publicKey), LAMPORTS_PER_SOL)

        val minimumAmount = connection.getMinimumBalanceForRentExemption(0)
        signature = connection.requestAirdrop(accountTo.publicKey, minimumAmount + 21)
        connection.confirmTransaction(signature)
        assertEquals(connection.getBalance(accountTo.publicKey), minimumAmount + 21)

        // 1. Move(accountFrom, accountTo)
        // 2. Move(accountTo, accountFrom)
        val transaction = Transaction()
            .add(
                SystemProgram.transfer(
                    fromPublicKey = accountFrom.publicKey,
                    toPublicKey = accountTo.publicKey,
                    lamports = 100
                )
            )
            .add(
                SystemProgram.transfer(
                    fromPublicKey = accountTo.publicKey,
                    toPublicKey = accountFrom.publicKey,
                    lamports = 100
                )
            )
        signature = connection.sendTransaction(
            transaction,
            listOf(accountFrom, accountTo),
            SendOptions(skipPreflight = true)
        )

        connection.confirmTransaction(signature)

        val response = connection.getSignatureStatus(signature).value
        if (response !== null) {
            assertTrue(response.slot > 0L)
            assertNull(response.err)
        } else {
            assertNotNull(response)
        }

        // accountFrom may have less than LAMPORTS_PER_SOL due to transaction fees
        assertTrue(connection.getBalance(accountFrom.publicKey) > 0)
        assertTrue(connection.getBalance(accountFrom.publicKey) <= LAMPORTS_PER_SOL)

        assertEquals(connection.getBalance(accountTo.publicKey), minimumAmount + 21)
    }

    @Test
    fun getFirstAvailableBlock() = runBlocking {
        val slot = connection.getFirstAvailableBlock()
        assertTrue(slot > 0)
    }

    @Test
    fun getBlock() = runBlocking {
        val block = connection.getBlock(128403111)
        val signature =
            "5yifMSLW13neR8DiCwmfKTZtUvz1rTMRPGqTEphWzNFfZdGmSbj9bPH42b5q5wyXnF3ErTbrD1qcSQxWmwAc6iF7"
        val transaction =
            block?.transactions?.find { it.transaction.signatures?.contains(signature) == true }
        assertTrue(transaction != null && transaction.transaction.signatures!!.contains(signature))
    }

    @Test
    fun isBlockhashValid() = runBlocking {
        assertFalse(
            connection.isBlockhashValid(
                blockhash = "J7rBdM6AecPDEZp8aPq5iPSNKVkU5Q76F3oAV4eW5wsW",
                commitment = Commitment.PROCESSED
            )
        )
    }

    @Test
    fun `get signatures for address`() = runBlocking {
//        val blockSignatures = connection.getBlockSignatures(128403111)
//        assertTrue(blockSignatures.signatures.count() > 0)

        val transaction1 =
            connection.getParsedTransaction("2jzdXQcdNDQS67ZyDTD7qjhkPJhXsv6agX7yf6RjrUULGyJ9iTAcFuDiKyjXSyX31DfVezkrKfqT9CUkof2V9XS7")
        assertNull(transaction1)

        val transaction2 =
            connection.getParsedTransaction("3hemaWyvFYhtu36sqvQgRJPsbJQmq9pcR37SV6eKnZGS94ASLdz1u2SLkUtaHt768DZ9W3hXoWGLZ16n9sXd9FZH")
        assertTrue(transaction2?.transaction?.message?.accountKeys?.first()?.signer == true)
        assertTrue(transaction2?.transaction?.message?.accountKeys?.first()?.writable == true)

        val args = listOf(
            "2jzdXQcdNDQS67ZyDTD7qjhkPJhXsv6agX7yf6RjrUULGyJ9iTAcFuDiKyjXSyX31DfVezkrKfqT9CUkof2V9XS7",
            "3hemaWyvFYhtu36sqvQgRJPsbJQmq9pcR37SV6eKnZGS94ASLdz1u2SLkUtaHt768DZ9W3hXoWGLZ16n9sXd9FZH"
        )

        val transaction3 =
            connection.getParsedTransactions(args)
        println(transaction3)
        assertTrue(!transaction3.isNullOrEmpty())

        val accountInfo = connection.getAccountInfo("FfkSPLivFJzaBrFu5VEdVipE6MRrYhExWpTGaLnHgLAt")
        println(accountInfo)

        val accountInfoWithContext = connection.getAccountInfoAndContext("FfkSPLivFJzaBrFu5VEdVipE6MRrYhExWpTGaLnHgLAt")
        println(json.encodeToString(accountInfoWithContext))
    }

    @Test
    fun test() {
        runBlocking {
            val programAccounts =
                connection.getProgramAccounts(PublicKey("G4YkbRN4nFQGEUg4SXzPsrManWzuk8bNq9JaMhXepnZ6"))
            Logger.debug(programAccounts)

            assertEquals(1, 1)
        }
    }

    companion object {
        /**
         * There are 1-billion lamports in one SOL
         */
        private const val LAMPORTS_PER_SOL = 1000000000L
    }

}