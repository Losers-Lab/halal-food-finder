package app.halal.persistence.account

import app.halal.application.account.RefreshTokenStore
import app.halal.application.account.StoredRefreshToken
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionTemplate
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
class JdbcRefreshTokenStore(
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate,
) : RefreshTokenStore {

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

    override fun consumeAndRotate(
        presentedToken: String,
        newToken: String,
        accountId: UUID,
        expiresAt: Instant,
    ): Boolean = tx.execute {
        // The conditional DELETE is the atomic claim on the token. Its
        // affected-row count is the gate: exactly one concurrent caller can
        // delete the live row (Postgres locks the row until this tx commits, so
        // a concurrent DELETE blocks, then re-evaluates and sees 0 rows after the
        // winner commits). If we did NOT own the row (0 deleted), we must not
        // insert a replacement — that is what stops a second live token. On a
        // successful delete the INSERT happens in the same transaction, so the
        // rotate is atomic (an INSERT failure rolls the delete back too).
        val deleted = jdbc.update(
            "DELETE FROM refresh_tokens WHERE token_hash = ?",
            sha256(presentedToken),
        )
        if (deleted != 1) {
            // Losing branch: no row was written, so a normal (empty) commit is
            // harmless and avoids relying on rollback-only commit semantics.
            false
        } else {
            jdbc.update(
                "INSERT INTO refresh_tokens (token_hash, account_id, expires_at) VALUES (?, ?, ?)",
                sha256(newToken),
                accountId,
                Timestamp.from(expiresAt),
            )
            true
        }
    } ?: false

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}