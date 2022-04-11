package com.portto.solana.web3.util

import org.bitcoinj.core.Utils

object Shortvec {

    @JvmStatic
    fun decodeLength(bytes: ByteArray): Pair<Int, ByteArray> {
        var newBytes = bytes
        var len = 0
        var size = 0
        while (true) {
            val elem = newBytes.first().toInt().also { newBytes = newBytes.drop(1).toByteArray() }
            len = len or (elem and 0x7f) shl (size * 7)
            size += 1
            if ((elem and 0x80) == 0) {
                break
            }
        }
        return len to newBytes
    }

    @JvmStatic
    fun encodeLength(len: Int): ByteArray {
        val out = ByteArray(10)
        var remLen = len
        var cursor = 0
        while (true) {
            var elem = remLen and 0x7f
            remLen = remLen shr 7
            if (remLen == 0) {
                Utils.uint16ToByteArrayLE(elem, out, cursor)
                break
            } else {
                elem = elem or 0x80
                Utils.uint16ToByteArrayLE(elem, out, cursor)
                cursor += 1
            }
        }
        val bytes = ByteArray(cursor + 1)
        System.arraycopy(out, 0, bytes, 0, cursor + 1)
        return bytes
    }

}