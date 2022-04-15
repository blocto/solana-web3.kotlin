/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/16
 */

package com.portto.solana.web3

import org.near.borshj.BorshBuffer
import org.tinylog.kotlin.Logger

class NonceAccount(
    val authorizedPubkey: PublicKey,
    val nonce: Blockhash,
    val feeCalculator: FeeCalculator,
) {

    companion object {
        /**
         * Deserialize NonceAccount from the account data.
         *
         * @param buffer account data
         * @return NonceAccount
         */
        fun fromAccountData(
            buffer: ByteArray,
        ): NonceAccount {
            val nonceAccount = BorshBuffer.wrap(buffer)
            Logger.info("version:${nonceAccount.readU32()}")
            Logger.info("state:${nonceAccount.readU32()}")
            return NonceAccount(
                authorizedPubkey = PublicKey(nonceAccount.readFixedArray(32)),
                nonce = PublicKey(nonceAccount.readFixedArray(32)).toString(),
                feeCalculator = FeeCalculator(nonceAccount.readU64()),
            )
        }
    }

}