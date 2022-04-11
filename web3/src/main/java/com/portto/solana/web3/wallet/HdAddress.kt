package com.portto.solana.web3.wallet

import com.portto.solana.web3.wallet.key.HdPrivateKey
import com.portto.solana.web3.wallet.key.HdPublicKey

/**
 * An HD pub/private key
 */
class HdAddress(
    val privateKey: HdPrivateKey,
    val publicKey: HdPublicKey,
    val coinType: SolanaCoin,
    val path: String
)