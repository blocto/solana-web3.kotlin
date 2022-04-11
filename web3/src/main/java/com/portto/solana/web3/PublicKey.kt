package com.portto.solana.web3

import com.portto.solana.web3.util.BorshCodable
import com.portto.solana.web3.util.BorshRule
import com.portto.solana.web3.util.ByteUtils
import com.portto.solana.web3.util.TweetNaclFast
import org.bitcoinj.core.Base58
import org.bitcoinj.core.Sha256Hash
import org.near.borshj.BorshInput
import org.near.borshj.BorshOutput
import java.io.ByteArrayOutputStream
import java.io.IOException

class PublicKeyRule(
    override val clazz: Class<PublicKey> = PublicKey::class.java
) : BorshRule<PublicKey> {

    fun <Self> writeZeros(output: BorshOutput<Self>): Self {
        return output.writeFixedArray(ByteArray(size = 32))
    }

    override fun read(input: BorshInput): PublicKey {
        return PublicKey(input.readFixedArray(32))
    }

    override fun <Self> write(obj: Any, output: BorshOutput<Self>): Self {
        val publicKey = obj as PublicKey
        return output.writeFixedArray(publicKey.toByteArray())
    }
}

data class PublicKey(val pubkey: ByteArray) : BorshCodable {
    init {
        require(pubkey.size <= PUBLIC_KEY_LENGTH) { "Invalid public key input" }
    }

    constructor(pubkeyString: String) : this(Base58.decode(pubkeyString))

    fun toByteArray(): ByteArray {
        return pubkey
    }

    fun toBase58(): String {
        return Base58.encode(pubkey)
    }

    fun equals(pubkey: PublicKey): Boolean {
        return this.pubkey.contentEquals(pubkey.toByteArray())
    }

    override fun hashCode(): Int {
        var result = 17
        result = 31 * result + pubkey.contentHashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (javaClass != other.javaClass) return false
        val person = other as PublicKey
        return equals(person)
    }

    override fun toString(): String {
        return toBase58()
    }

    class ProgramDerivedAddress(val address: PublicKey, val nonce: Int)
    companion object {
        const val PUBLIC_KEY_LENGTH = 32

        fun readPubkey(bytes: ByteArray, offset: Int): PublicKey {
            val buf = ByteUtils.readBytes(bytes, offset, PUBLIC_KEY_LENGTH)
            return PublicKey(buf)
        }

        fun createProgramAddress(seeds: List<ByteArray>, programId: PublicKey): PublicKey {
            val buffer = ByteArrayOutputStream()
            for (seed in seeds) {
                require(seed.size <= 32) { "Max seed length exceeded" }
                try {
                    buffer.write(seed)
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
            try {
                buffer.write(programId.toByteArray())
                buffer.write("ProgramDerivedAddress".toByteArray())
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            val hash = Sha256Hash.hash(buffer.toByteArray())
            if (TweetNaclFast.is_on_curve(hash) != 0) {
                throw RuntimeException("Invalid seeds, address must fall off the curve")
            }
            return PublicKey(hash)
        }

        @Throws(Exception::class)
        fun findProgramAddress(
            seeds: List<ByteArray>,
            programId: PublicKey
        ): ProgramDerivedAddress {
            var nonce = 255
            val address: PublicKey
            val seedsWithNonce: MutableList<ByteArray> = ArrayList()
            seedsWithNonce.addAll(seeds)
            while (nonce != 0) {
                address = try {
                    seedsWithNonce.add(byteArrayOf(nonce.toByte()))
                    createProgramAddress(seedsWithNonce, programId)
                } catch (e: Exception) {
                    seedsWithNonce.removeAt(seedsWithNonce.size - 1)
                    nonce--
                    continue
                }
                return ProgramDerivedAddress(address, nonce)
            }
            throw Exception("Unable to find a viable program address nonce")
        }

        fun valueOf(publicKey: String): PublicKey {
            return PublicKey(publicKey)
        }
    }
}
