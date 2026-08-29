package app.halal.web.account

import app.halal.application.account.AuthSession
import java.util.UUID

/**
 * Shared contract for the Log In (sc-40) and Refresh (sc-40) endpoints. The
 * client receives the token pair plus the token type / lifetime and the
 * authenticated account's id + role (RBAC).
 */
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val accountId: UUID,
    val role: String,
) {
    companion object {
        fun from(session: AuthSession): AuthResponse = AuthResponse(
            accessToken = session.tokens.accessToken,
            refreshToken = session.tokens.refreshToken,
            tokenType = session.tokens.tokenType,
            expiresIn = session.tokens.accessTokenExpiresInSeconds,
            accountId = session.account.id,
            role = session.account.role.name,
        )
    }
}

/** Uniform error envelope (`code` + optional human message). Never echoes secrets. */
data class ErrorResponse(val code: String, val message: String? = null)