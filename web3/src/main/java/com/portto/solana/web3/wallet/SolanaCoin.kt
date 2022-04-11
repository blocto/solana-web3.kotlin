package com.portto.solana.web3.wallet

import com.portto.solana.web3.wallet.key.SolanaCurve

class SolanaCoin {
    /**
     * Get the curve
     *
     * @return curve
     */
    val curve = SolanaCurve()

    /**
     * get the coin type
     *
     * @return coin type
     */
    val coinType: Long = 501

    /**
     * get the coin purpose
     *
     * @return purpose
     */
    val purpose: Long = 44

    /**
     * get whether the addresses must always be hardened
     *
     * @return always hardened
     */
    val alwaysHardened = true
}