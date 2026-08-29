package app.halal.persistence.account

import app.halal.application.account.RefreshTokenStore
import app.halal.application.account.StoredRefreshToken
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * JDBC implementation of the [RefreshTokenStore] port against the
 * `refresh_tokens` table (see V2__create_refresh_tokens.sql). Only the SHA-256
 * hash of each token is persisted — the plaintext token is never stored, so a
 * database leak of this table cannot be replayed. Tokens are single-use and
 * rotated on refresh (old hash deleted, new inserted).
 */
@Repository
class JdbcRefreshTokenStore(private val jdbc: JdbcTemplate) : RefreshTokenStore {

    override fun store(token: String, accountId: UUID, expiresAt: Instant) {
        jdbc.update(
            """
            INSERT INTO refresh_tokens (token_hash, account_id, expires_at)
            VALUES (?, ?, ?)
            ON CONFLICT (token_hash) DO UPDATE
                SET account_id = EXCLUDED.account_id,
                    expires_at = EXCLUDED.expires_at
            """.trimIndent(),
            sha256(token),
            accountId,
            Timestamp.from(expiresAt),
        )
    }

    override fun findByToken(token: String): StoredRefreshToken? {
        val rows = jdbc.query(
            "SELECT account_id, expires_at FROM refresh_tokens WHERE token_hash = ?",
            { rs, _ ->
                StoredRefreshToken(
                    accountId = rs.getObject("account_id", UUID::class.java),
                    expiresAt = rs.getTimestamp("expires_at").toInstant(),
                )
            },
            sha256(token),
        )
        return rows.firstOrNull()
    }

    override fun revoke(token: String) {
        jdbc.update("DELETE FROM refresh_tokens WHERE token_hash = ?", sha256(token))
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}