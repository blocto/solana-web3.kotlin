/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/16
 */

package com.portto.solana.web3

/**
 * Calculator for transaction fees.
 */
class FeeCalculator(
    /** Cost in lamports to validate a signature. */
    val lamportsPerSignature: Long,
)