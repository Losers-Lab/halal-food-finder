package com.tahirslist.application.account

/**
 * Log Out (sc-132) use case — server-side refresh-token revocation. Revokes the
 * presented refresh token so it can no longer be used to obtain a fresh access
 * token. Deliberately idempotent: revoking an unknown or already-revoked token
 * is a no-op success, so the endpoint never reveals whether a given refresh
 * token was ever valid (no token-enumeration oracle).
 */
class LogoutSession(
    private val refreshTokenStore: RefreshTokenStore,
) {
    fun execute(refreshToken: String) {
        refreshTokenStore.revoke(refreshToken)
    }
}