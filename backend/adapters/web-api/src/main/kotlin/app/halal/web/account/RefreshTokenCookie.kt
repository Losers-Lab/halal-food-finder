package app.halal.web.account

import app.halal.application.account.SessionLifetimes
import java.time.Duration

/**
 * The refresh-token cookie contract (sc-133).
 *
 * The refresh token is moved out of the JSON body / JS-readable storage and
 * delivered only as this HttpOnly; Secure; SameSite=Lax cookie. It is scoped
 * with `Path=/v1/auth` so the browser only ever sends it to the auth endpoints
 * that consume it (refresh, logout) and never to other API routes. The access
 * JWT stays memory-only on the frontend, which is why it remains in the JSON
 * body while the refresh token does not.
 *
 * The cookie carries the raw refresh token (opaque, high-entropy); the server
 * persists only its SHA-256 hash (see JdbcRefreshTokenStore), so the browser is
 * the single holder of the plaintext.
 */
object RefreshTokenCookie {

    const val NAME = "refresh_token"
    const val PATH = "/v1/auth"

    /** Same 30-day flat lifetime as the persisted refresh token (founder-ratified). */
    val MAX_AGE: Duration = SessionLifetimes.REFRESH_LIFETIME

    /** The Set-Cookie line that issues (or re-issues) the refresh cookie. */
    fun issue(refreshToken: String): String = Value(refreshToken, MAX_AGE).toSetCookie()

    /** A zero-Max-Age Set-Cookie line that tells the browser to drop the cookie. */
    fun clear(): String = Value("", Duration.ZERO).toSetCookie()

    private data class Value(private val token: String, private val maxAge: Duration) {
        fun toSetCookie(): String {
            val parts = mutableListOf("$NAME=$token")
            parts += "Path=$PATH"
            parts += "Max-Age=${maxAge.seconds}"
            parts += "HttpOnly"
            parts += "Secure"
            parts += "SameSite=Lax"
            return parts.joinToString("; ")
        }
    }
}