package com.tahirslist.application.account

import com.tahirslist.domain.account.Account

/**
 * Issues the token pair that makes up an authenticated session: a short-lived
 * RS256 JWT access token (with the account's RBAC role embedded) and an opaque
 * rotating refresh token. Implemented by the JWT infrastructure layer.
 */
interface TokenIssuer {
    fun issue(account: Account): SessionTokens
}