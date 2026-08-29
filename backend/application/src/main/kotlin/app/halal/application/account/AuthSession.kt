package app.halal.application.account

import app.halal.domain.account.Account

/** The authenticated account plus the tokens that carry its session. */
data class AuthSession(val account: Account, val tokens: SessionTokens)