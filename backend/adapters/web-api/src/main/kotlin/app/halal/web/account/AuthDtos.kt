package app.halal.web.account

import app.halal.application.account.AuthSession
import java.util.UUID

/**
 * Shared contract for the Log In (sc-40) and Refresh (sc-40) endpoints from the
 * sc-133 cookie migration onward: the client receives ONLY the short-lived
 * access JWT plus the token type / lifetime and the authenticated account's
 * id + role (RBAC). The refresh token is deliberately absent from the JSON body
 * — it is delivered exclusively via the HttpOnly refresh cookie (see
 * [RefreshTokenCookie]) so it never becomes JS-readable storage surface.
 */
data class AuthResponse(
    val accessToken: String,
    val tokenType: String,
    val expiresIn: Long,
    val accountId: UUID,
    val role: String,
) {
    companion object {
        fun from(session: AuthSession): AuthResponse = AuthResponse(
            accessToken = session.tokens.accessToken,
            tokenType = session.tokens.tokenType,
            expiresIn = session.tokens.accessTokenExpiresInSeconds,
            accountId = session.account.id,
            role = session.account.role.name,
        )
    }
}

/** Uniform error envelope (`code` + optional human message). Never echoes secrets. */
data class ErrorResponse(val code: String, val message: String? = null)