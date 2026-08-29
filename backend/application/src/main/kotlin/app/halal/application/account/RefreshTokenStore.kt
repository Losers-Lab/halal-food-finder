package app.halal.application.account

import java.time.Instant
import java.util.UUID

/**
 * Persistence port for refresh tokens (hexagonal "out" port), implemented by
 * the persistence adapter. Refresh tokens are opaque random strings handled by
 * this contract in raw form; the adapter is responsible for storing only a
 * one-way SHA-256 hash and for looking tokens up by that hash — the plaintext
 * token must never be persisted. Records are single-use and rotated on refresh.
 */
interface RefreshTokenStore {

    /** Persist a hashed refresh token, bound to the owning account, expiring at [expiresAt]. */
    fun store(token: String, accountId: UUID, expiresAt: Instant)

    /** Resolve a previously-issued (non-revoked) refresh token, if any. */
    fun findByToken(token: String): StoredRefreshToken?

    /** Irrevocably invalidate a refresh token (rotation / expiry). */
    fun revoke(token: String)
}

/** A live refresh-token record as read back from storage. */
data class StoredRefreshToken(val accountId: UUID, val expiresAt: Instant)