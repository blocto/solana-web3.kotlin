/*
 * Copyright (C) 2022 portto Co., Ltd.
 *
 * Created by Kihon on 2022/4/10
 */

package com.portto.solana.web3

// TODO: These constants should be removed in favor of reading them out of a
// Syscall account

internal const val NUM_TICKS_PER_SECOND = 160L

internal const val DEFAULT_TICKS_PER_SLOT = 64L

internal const val NUM_SLOTS_PER_SECOND = NUM_TICKS_PER_SECOND / DEFAULT_TICKS_PER_SLOT

internal const val MS_PER_SLOT = 1000L / NUM_SLOTS_PER_SECOND
