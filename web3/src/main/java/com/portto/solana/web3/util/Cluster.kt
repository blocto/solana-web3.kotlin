/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/10
 */

package com.portto.solana.web3.util

enum class Cluster(val endpoint: String) {
    DEVNET("https://api.devnet.solana.com"),
    TESTNET("https://api.testnet.solana.com"),
    MAINNET_BETA("https://api.mainnet-beta.solana.com");
}

