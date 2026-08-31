package com.tahirslist.application.account

import com.tahirslist.domain.account.Account

/** The authenticated account plus the tokens that carry its session. */
data class AuthSession(val account: Account, val tokens: SessionTokens)