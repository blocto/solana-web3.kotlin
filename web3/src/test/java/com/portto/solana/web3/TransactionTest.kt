/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/10
 */

@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)

package com.portto.solana.web3

import com.portto.solana.web3.programs.SystemProgram
import com.portto.solana.web3.util.Cluster
import kotlinx.coroutines.*
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import java.util.*

class TransactionTest {

    private val mainThreadSurrogate = newSingleThreadContext("UI thread")

    @Rule
    @JvmField
    var watcher: TestRule = object : TestWatcher() {
        override fun starting(description: Description) {
            Logger.info("Starting test: " + description.methodName)
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
    fun `accountKeys are ordered`() {
        val payer = KeyPair.generate()
        val account2 = KeyPair.generate()
        val account3 = KeyPair.generate()
        val recentBlockhash = KeyPair.generate().publicKey.toBase58()
        val programId = KeyPair.generate().publicKey
        val transaction = Transaction().apply {
            this.recentBlockhash = recentBlockhash
            add(
                TransactionInstruction(
                    keys = listOf(
                        AccountMeta(
                            publicKey = account3.publicKey,
                            isSigner = true,
                            isWritable = false
                        ),
                        AccountMeta(
                            publicKey = payer.publicKey,
                            isSigner = true,
                            isWritable = true
                        ),
                        AccountMeta(
                            publicKey = account2.publicKey,
                            isSigner = true,
                            isWritable = true
                        )
                    ),
                    programId = programId
                )
            )
        }

        transaction.setSigners(
            payer.publicKey,
            account2.publicKey,
            account3.publicKey,
        )

        val message = transaction.compileMessage()
        assertEquals(message.accountKeys[0], payer.publicKey)
        assertEquals(message.accountKeys[1], account2.publicKey)
        assertEquals(message.accountKeys[2], account3.publicKey)
    }

    @Test
    fun `payer is first account meta`() {
        val payer = KeyPair.generate()
        val other = KeyPair.generate()
        val recentBlockhash = KeyPair.generate().publicKey.toBase58()
        val programId = KeyPair.generate().publicKey
        val transaction = Transaction().apply {
            this.recentBlockhash = recentBlockhash
            add(
                TransactionInstruction(
                    keys = listOf(
                        AccountMeta(
                            publicKey = other.publicKey,
                            isSigner = true,
                            isWritable = true
                        ),
                        AccountMeta(
                            publicKey = payer.publicKey,
                            isSigner = true,
                            isWritable = true
                        ),
                    ),
                    programId = programId
                )
            )
        }

        transaction.sign(payer, other)
        val message = transaction.compileMessage()
        assertEquals(message.accountKeys[0], payer.publicKey)
        assertEquals(message.accountKeys[1], other.publicKey)
        assertEquals(message.header.numRequiredSignatures.toInt(), 2)
        assertEquals(message.header.numReadonlySignedAccounts.toInt(), 0)
        assertEquals(message.header.numReadonlyUnsignedAccounts.toInt(), 1)
    }

    @Test
    fun validation() {
        val payer = KeyPair.generate()
        val recentBlockhash = KeyPair.generate().publicKey.toBase58()

        val transaction = Transaction()
        assertThrows(
            "Transaction recentBlockhash required",
            UninitializedPropertyAccessException::class.java
        ) { transaction.compileMessage() }

        transaction.recentBlockhash = recentBlockhash

        assertThrows(
            "Transaction fee payer required",
            IllegalArgumentException::class.java
        ) { transaction.compileMessage() }

        transaction.setSigners(payer.publicKey, KeyPair.generate().publicKey)

        assertThrows(
            "unknown signer",
            Error::class.java
        ) { transaction.compileMessage() }

        // Expect compile to succeed with implicit fee payer from signers
        transaction.setSigners(payer.publicKey)
        transaction.compileMessage()

        // Expect compile to succeed with fee payer and no signers
        transaction.signatures = mutableListOf()
        transaction.feePayer = payer.publicKey
        transaction.compileMessage()
    }

    @Test
    fun `payer is writable`() {
        val payer = KeyPair.generate()
        val recentBlockhash = KeyPair.generate().publicKey.toBase58()
        val programId = KeyPair.generate().publicKey
        val transaction = Transaction().apply {
            this.recentBlockhash = recentBlockhash
            add(
                TransactionInstruction(
                    keys = listOf(
                        AccountMeta(
                            publicKey = payer.publicKey,
                            isSigner = true,
                            isWritable = false
                        )
                    ),
                    programId = programId
                )
            )
        }

        transaction.sign(payer)
        val message = transaction.compileMessage()
        assertEquals(message.accountKeys[0], payer.publicKey)
        assertEquals(message.header.numRequiredSignatures.toInt(), 1)
        assertEquals(message.header.numReadonlySignedAccounts.toInt(), 0)
        assertEquals(message.header.numReadonlyUnsignedAccounts.toInt(), 1)
    }

    @Test
    fun getEstimatedFee() = runBlocking {
        val api = Connection(Cluster.DEVNET)
        val accountFrom = KeyPair.generate()
        val accountTo = KeyPair.generate()

        val blockhash = api.getLatestBlockhash()

        val transaction = Transaction().apply {
            this.feePayer = accountFrom.publicKey
            this.recentBlockhash = Blockhash().plus(blockhash)
            add(
                SystemProgram.transfer(
                    fromPublicKey = accountFrom.publicKey,
                    toPublicKey = accountTo.publicKey,
                    lamports = 10
                )
            )
        }

        val fee = transaction.getEstimatedFee(api)
        assertEquals(fee, 5000)
    }

    @Test
    fun partialSign() {
        val account1 = KeyPair.generate()
        val account2 = KeyPair.generate()
        val recentBlockhash = account1.publicKey.toBase58() // Fake recentBlockhash
        val transfer = SystemProgram.transfer(
            fromPublicKey = account1.publicKey,
            toPublicKey = account2.publicKey,
            lamports = 123
        )

        val transaction = Transaction()
            .apply { this.recentBlockhash = recentBlockhash }
            .add(transfer)
        transaction.sign(account1, account2)

        val partialTransaction = Transaction()
            .apply { this.recentBlockhash = recentBlockhash }
            .add(transfer)
        partialTransaction.setSigners(account1.publicKey, account2.publicKey)
        assertNull(partialTransaction.signatures[0].signature)
        assertNull(partialTransaction.signatures[1].signature)

        partialTransaction.partialSign(account1)
        assertNotNull(partialTransaction.signatures[0].signature)
        assertNull(partialTransaction.signatures[1].signature)

        assertThrows(Error::class.java) { partialTransaction.serialize() }
        partialTransaction.serialize(
            SerializeConfig(requireAllSignatures = false)
        )

        partialTransaction.partialSign(account2)

        assertNotNull(partialTransaction.signatures[0].signature)
        assertNotNull(partialTransaction.signatures[1].signature)

        partialTransaction.serialize()

        // assertArrayEquals(partialTransaction.serialize().toTypedArray(), transaction.serialize().toTypedArray())
        assertTrue(partialTransaction.serialize().contentEquals(transaction.serialize()))

        assertNotNull(partialTransaction.signatures[0].signature)
        partialTransaction.signatures[0].signature!![0] = 0
        assertThrows(Error::class.java) {
            partialTransaction.serialize(SerializeConfig(requireAllSignatures = false))
        }
        partialTransaction.serialize(
            SerializeConfig(requireAllSignatures = false, verifySignatures = false)
        )
    }

    @Test
    fun setSigners() {
        val payer = KeyPair.generate()
        val duplicate1 = payer
        val duplicate2 = payer
        val recentBlockhash = KeyPair.generate().publicKey.toBase58()
        val programId = KeyPair.generate().publicKey

        val transaction = Transaction().apply {
            this.recentBlockhash = recentBlockhash
            add(
                TransactionInstruction(
                    keys = listOf(
                        AccountMeta(
                            publicKey = duplicate1.publicKey,
                            isSigner = true,
                            isWritable = true
                        ),
                        AccountMeta(
                            publicKey = payer.publicKey,
                            isSigner = false,
                            isWritable = true
                        ),
                        AccountMeta(
                            publicKey = duplicate2.publicKey,
                            isSigner = true,
                            isWritable = false
                        )
                    ),
                    programId = programId
                )
            )
        }

        transaction.setSigners(
            payer.publicKey,
            duplicate1.publicKey,
            duplicate2.publicKey,
        )

        assertEquals(transaction.signatures.count(), 1)
        assertEquals(transaction.signatures[0].publicKey, payer.publicKey)

        val message = transaction.compileMessage()
        assertEquals(message.accountKeys[0], payer.publicKey)
        assertEquals(message.header.numRequiredSignatures.toInt(), 1)
        assertEquals(message.header.numReadonlySignedAccounts.toInt(), 0)
        assertEquals(message.header.numReadonlyUnsignedAccounts.toInt(), 1)

        transaction.signatures
    }

    @Test
    fun sign() {
        val payer = KeyPair.generate()
        val duplicate1 = payer
        val duplicate2 = payer
        val recentBlockhash = KeyPair.generate().publicKey.toBase58()
        val programId = KeyPair.generate().publicKey

        val transaction = Transaction().apply {
            this.recentBlockhash = recentBlockhash
            add(
                TransactionInstruction(
                    keys = listOf(
                        AccountMeta(
                            publicKey = duplicate1.publicKey,
                            isSigner = true,
                            isWritable = true
                        ),
                        AccountMeta(
                            publicKey = payer.publicKey,
                            isSigner = false,
                            isWritable = true
                        ),
                        AccountMeta(
                            publicKey = duplicate2.publicKey,
                            isSigner = true,
                            isWritable = false
                        )
                    ),
                    programId = programId
                )
            )
        }

        transaction.sign(payer, duplicate1, duplicate2);

        assertEquals(transaction.signatures.count(), 1)
        assertEquals(transaction.signatures[0].publicKey, payer.publicKey)

        val message = transaction.compileMessage()
        assertEquals(message.accountKeys[0], payer.publicKey)
        assertEquals(message.header.numRequiredSignatures.toInt(), 1)
        assertEquals(message.header.numReadonlySignedAccounts.toInt(), 0)
        assertEquals(message.header.numReadonlyUnsignedAccounts.toInt(), 1)

        transaction.signatures
    }

    @Test
    fun `transfer signatures`() {
        val account1 = KeyPair.generate()
        val account2 = KeyPair.generate()
        val recentBlockhash = account1.publicKey.toBase58() // Fake recentBlockhash
        val transfer1 = SystemProgram.transfer(
            fromPublicKey = account1.publicKey,
            toPublicKey = account2.publicKey,
            lamports = 123
        )
        val transfer2 = SystemProgram.transfer(
            fromPublicKey = account2.publicKey,
            toPublicKey = account1.publicKey,
            lamports = 123
        )

        val orgTransaction = Transaction().apply {
            this.recentBlockhash = recentBlockhash
            add(transfer1, transfer2)
        }
        orgTransaction.sign(account1, account2)

        val newTransaction = Transaction().apply {
            this.recentBlockhash = recentBlockhash
            this.signatures = orgTransaction.signatures
            add(transfer1, transfer2)
        }

        assertTrue(newTransaction.serialize().contentEquals(orgTransaction.serialize()))
    }

    @Test
    fun `dedup signatures`() {

    }

    @Test
    fun `use nonce`() {

    }

    @Test
    fun `parse wire format and serialize`() {
        val sender = KeyPair.fromSeed(ByteArray(32).apply { fill(8) }) // Arbitrary known account
        val recentBlockhash =
            "EETubP5AKHgjPAhzPAFcb8BAY1hMH639CWCFTqi3hq1k" // Arbitrary known recentBlockhash
        val recipient =
            PublicKey("J3dxNj7nDRRqRRXuEMynDG57DkZK4jYRuv3Garmb1i99") // Arbitrary known public key
        val transfer = SystemProgram.transfer(
            fromPublicKey = sender.publicKey,
            toPublicKey = recipient,
            lamports = 49
        )
        val expectedTransaction = Transaction().apply {
            this.recentBlockhash = recentBlockhash
            this.feePayer = sender.publicKey
            add(transfer)
            sign(sender)
        }

        val wireTransaction = Base64.getDecoder()
            .decode("AVuErQHaXv0SG0/PchunfxHKt8wMRfMZzqV0tkC5qO6owYxWU2v871AoWywGoFQr4z+q/7mE8lIufNl/kxj+nQ0BAAEDE5j2LG0aRXxRumpLXz29L2n8qTIWIY3ImX5Ba9F9k8r9Q5/Mtmcn8onFxt47xKj+XdXXd3C8j/FcPu7csUrz/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAxJrndgN4IFTxep3s6kO0ROug7bEsbx0xxuDkqEvwUusBAgIAAQwCAAAAMQAAAAAAAAA=")
        val tx = Transaction.from(wireTransaction)

        assertArrayEquals(
            tx.serialize().toTypedArray(),
            expectedTransaction.serialize().toTypedArray()
        )
        assertArrayEquals(
            wireTransaction.toTypedArray(),
            expectedTransaction.serialize().toTypedArray()
        )
    }

    @Test
    fun `populate transaction`() {
        val recentBlockhash = PublicKey(byteArrayOf(1)).toString()
        val message = Message(
            accountKeys = listOf(
                PublicKey(byteArrayOf(1)),
                PublicKey(byteArrayOf(2)),
                PublicKey(byteArrayOf(3)),
                PublicKey(byteArrayOf(4)),
                PublicKey(byteArrayOf(5))
            ),
            header = MessageHeader().apply {
                numReadonlySignedAccounts = 0
                numReadonlyUnsignedAccounts = 3
                numRequiredSignatures = 2
            },
            instructions = listOf(
                CompiledInstruction(
                    accounts = listOf(1, 2, 3),
                    data = Base58.encode(ByteArray(5).apply { fill(9) }),
                    programIdIndex = 4
                )
            ),
            recentBlockhash = recentBlockhash
        )

        val signatures = listOf(
            Base58.encode(ByteArray(64).apply { fill(1) }),
            Base58.encode(ByteArray(64).apply { fill(2) })
        )

        val transaction = Transaction.populate(message, signatures)
        assertEquals(transaction.instructions.count(), 1)
        assertEquals(transaction.signatures.count(), 2)
        assertEquals(transaction.recentBlockhash, recentBlockhash)
    }

    @Test
    fun `serialize unsigned transaction`() {
        val sender = KeyPair.fromSeed(ByteArray(32).apply { fill(8) }) // Arbitrary known account
        val recentBlockhash =
            "EETubP5AKHgjPAhzPAFcb8BAY1hMH639CWCFTqi3hq1k" // Arbitrary known recentBlockhash
        val recipient =
            PublicKey("J3dxNj7nDRRqRRXuEMynDG57DkZK4jYRuv3Garmb1i99") // Arbitrary known public key
        val transfer = SystemProgram.transfer(
            fromPublicKey = sender.publicKey,
            toPublicKey = recipient,
            lamports = 49
        )
        val expectedTransaction = Transaction().apply {
            this.recentBlockhash = recentBlockhash
            add(transfer)
        }

        // Empty signature array fails.
        assertEquals(expectedTransaction.signatures.count(), 0)
        assertThrows(
            "Transaction fee payer required",
            IllegalArgumentException::class.java
        ) { expectedTransaction.serialize() }
        assertThrows(
            "Transaction fee payer required",
            IllegalArgumentException::class.java
        ) { expectedTransaction.serialize(SerializeConfig(verifySignatures = false)) }
        assertThrows(
            "Transaction fee payer required",
            IllegalArgumentException::class.java
        ) { expectedTransaction.serializeMessage() }

        expectedTransaction.feePayer = sender.publicKey

        // Transactions with missing signatures will fail sigverify.
        assertThrows(
            "Signature verification failed",
            Error::class.java
        ) { expectedTransaction.serialize() }

        // Serializing without signatures is allowed if sigverify disabled.
        expectedTransaction.serialize(SerializeConfig(verifySignatures = false))

        // Serializing the message is allowed when signature array has null signatures
        expectedTransaction.serializeMessage()

        expectedTransaction.feePayer = null
        expectedTransaction.setSigners(sender.publicKey)
        assertEquals(expectedTransaction.signatures.count(), 1)

        // Transactions with missing signatures will fail sigverify.
        assertThrows(
            "Signature verification failed",
            Error::class.java
        ) { expectedTransaction.serialize() }

        // Serializing without signatures is allowed if sigverify disabled.
        expectedTransaction.serialize(SerializeConfig(verifySignatures = false))

        // Serializing the message is allowed when signature array has null signatures
        expectedTransaction.serializeMessage()

        val expectedSerializationWithNoSignatures = Base64.getDecoder().decode(
            "AQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAAAABAAEDE5j2LG0aRXxRumpLXz29L2n8qTIWIY3ImX5Ba9F9k8r9" +
                    "Q5/Mtmcn8onFxt47xKj+XdXXd3C8j/FcPu7csUrz/AAAAAAAAAAAAAAAAAAAAAAAAAAA" +
                    "AAAAAAAAAAAAAAAAxJrndgN4IFTxep3s6kO0ROug7bEsbx0xxuDkqEvwUusBAgIAAQwC" +
                    "AAAAMQAAAAAAAAA="
        )
        assertTrue(
            expectedTransaction.serialize(SerializeConfig(requireAllSignatures = false))
                .contentEquals(expectedSerializationWithNoSignatures)
        )

        // Properly signed transaction succeeds
        expectedTransaction.partialSign(sender)
        assertEquals(expectedTransaction.signatures.count(), 1)
        val expectedSerialization = Base64.getDecoder().decode(
            "AVuErQHaXv0SG0/PchunfxHKt8wMRfMZzqV0tkC5qO6owYxWU2v871AoWywGoFQr4z+q/7mE8lIufNl/" +
                    "kxj+nQ0BAAEDE5j2LG0aRXxRumpLXz29L2n8qTIWIY3ImX5Ba9F9k8r9Q5/Mtmcn8onFxt47xKj+XdXX" +
                    "d3C8j/FcPu7csUrz/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAxJrndgN4IFTxep3s6kO0" +
                    "ROug7bEsbx0xxuDkqEvwUusBAgIAAQwCAAAAMQAAAAAAAAA="
        )
        assertTrue(expectedTransaction.serialize().contentEquals(expectedSerialization))
        assertEquals(expectedTransaction.signatures.count(), 1)
    }

    @Test
    fun `externally signed stake delegate`() {

    }

    @Test
    fun `can serialize, deserialize, and reserialize with a partial signer`() {
        val signer = KeyPair.generate()
        val acc0Writable = KeyPair.generate()
        val acc1Writable = KeyPair.generate()
        val acc2Writable = KeyPair.generate()
        val t0 = Transaction().apply {
            this.recentBlockhash = Blockhash().plus("HZaTsZuhN1aaz9WuuimCFMyH7wJ5xiyMUHFCnZSMyguH")
            this.feePayer = signer.publicKey
        }
        t0.add(
            TransactionInstruction(
                keys = listOf(
                    AccountMeta(
                        publicKey = signer.publicKey,
                        isSigner = true,
                        isWritable = true
                    ),
                    AccountMeta(
                        publicKey = acc0Writable.publicKey,
                        isSigner = false,
                        isWritable = true
                    )
                ),
                programId = KeyPair.generate().publicKey
            )
        )
        t0.add(
            TransactionInstruction(
                keys = listOf(
                    AccountMeta(
                        publicKey = acc1Writable.publicKey,
                        isSigner = false,
                        isWritable = false
                    )
                ),
                programId = KeyPair.generate().publicKey
            )
        )
        t0.add(
            TransactionInstruction(
                keys = listOf(
                    AccountMeta(
                        publicKey = acc2Writable.publicKey,
                        isSigner = false,
                        isWritable = true
                    )
                ),
                programId = KeyPair.generate().publicKey
            )
        )
        t0.add(
            TransactionInstruction(
                keys = listOf(
                    AccountMeta(
                        publicKey = signer.publicKey,
                        isSigner = true,
                        isWritable = true
                    ),
                    AccountMeta(
                        publicKey = acc0Writable.publicKey,
                        isSigner = false,
                        isWritable = false
                    ),
                    AccountMeta(
                        publicKey = acc2Writable.publicKey,
                        isSigner = false,
                        isWritable = false
                    ),
                    AccountMeta(
                        publicKey = acc1Writable.publicKey,
                        isSigner = false,
                        isWritable = true
                    )
                ),
                programId = KeyPair.generate().publicKey
            )
        )
        t0.partialSign(signer)
        val t1 = Transaction.from(t0.serialize())
        t1.serialize()
    }
}
