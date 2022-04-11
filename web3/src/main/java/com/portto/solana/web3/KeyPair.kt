/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/10
 */

package com.portto.solana.web3

import com.portto.solana.web3.util.TweetNaclFast

/**
 * Keypair signer interface
 */
interface Signer {
    val publicKey: PublicKey
    val secretKey: ByteArray
}

/**
 * Ed25519 Keypair
 */
data class Ed25519Keypair(
    val publicKey: ByteArray,
    val secretKey: ByteArray
)

/**
 * An account keypair used for signing transactions.
 *
 * Create a new keypair instance.
 * Generate random keypair if no {@link Ed25519Keypair} is provided.
 *
 * @param keypair ed25519 keypair
 */
class KeyPair(keypair: Ed25519Keypair?) : Signer {

    private val keypair: Ed25519Keypair = keypair ?: TweetNaclFast.Signature.keyPair()
        .let { Ed25519Keypair(publicKey = it.publicKey, secretKey = it.secretKey) }

    /**
     * The public key for this keypair
     */
    override val publicKey: PublicKey
        get() = PublicKey(this.keypair.publicKey)

    /**
     * The raw secret key for this keypair
     */
    override val secretKey: ByteArray
        get() = this.keypair.secretKey

    companion object {
        /**
         * Generate a new random keypair
         */
        fun generate(): KeyPair {
            return KeyPair(TweetNaclFast.Signature.keyPair()
                .let { Ed25519Keypair(publicKey = it.publicKey, secretKey = it.secretKey) }
            )
        }

        /**
         * Create a keypair from a raw secret key byte array.
         *
         * This method should only be used to recreate a keypair from a previously
         * generated secret key. Generating keypairs from a random seed should be done
         * with the {@link Keypair.fromSeed} method.
         *
         * @throws error if the provided secret key is invalid and validation is not skipped.
         *
         * @param secretKey secret key byte array
         * @param skipValidation: skip secret key validation
         */
        fun fromSecretKey(
            secretKey: ByteArray,
            skipValidation: Boolean = false,
        ): KeyPair {
            val keypair = TweetNaclFast.Signature.keyPair_fromSecretKey(secretKey)
            if (!skipValidation) {
                val signData = "@blocto/solana.web3.kotlin-validation-v1".encodeToByteArray()
                val signature =
                    TweetNaclFast.Signature(ByteArray(0), keypair.secretKey).detached(signData)
                if (!TweetNaclFast.Signature(keypair.publicKey, ByteArray(0))
                        .detached_verify(signData, signature)
                ) {
                    throw Error("provided secretKey is invalid")
                }
            }
            return KeyPair(keypair.let {
                Ed25519Keypair(
                    publicKey = it.publicKey,
                    secretKey = it.secretKey
                )
            })
        }


        /**
         * Generate a keypair from a 32 byte seed.
         *
         * @param seed seed byte array
         */
        fun fromSeed(seed: ByteArray): KeyPair {
            return KeyPair(TweetNaclFast.Signature.keyPair_fromSeed(seed).let {
                Ed25519Keypair(
                    publicKey = it.publicKey,
                    secretKey = it.secretKey
                )
            })
        }
    }

}