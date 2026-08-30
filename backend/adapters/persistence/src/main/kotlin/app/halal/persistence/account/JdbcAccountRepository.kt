package app.halal.persistence.account

import app.halal.application.account.AccountRepository
import app.halal.application.account.EmailAlreadyExistsException
import app.halal.domain.account.Account
import app.halal.domain.account.Email
import app.halal.domain.account.Role
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/**
 * JDBC implementation of the [AccountRepository] port against the `users` table
 * (see V1__create_users.sql). Spring JDBC only — deliberately boring and
 * explicit; no JPA magic. The database owns id generation (gen_random_uuid).
 */
@Repository
class JdbcAccountRepository(private val jdbc: JdbcTemplate) : AccountRepository {

    override fun findByEmail(email: Email): Account? {
        val rows = jdbc.query(
            "SELECT id, email, password_hash, role FROM users WHERE email = ?",
            { rs, _ -> rs.toAccount() },
            email.value,
        )
        return rows.firstOrNull()
    }

    override fun findById(id: UUID): Account? {
        val rows = jdbc.query(
            "SELECT id, email, password_hash, role FROM users WHERE id = ?",
            { rs, _ -> rs.toAccount() },
            id,
        )
        return rows.firstOrNull()
    }

    override fun save(account: Account): Account {
        val id = try {
            jdbc.queryForObject(
                """
                INSERT INTO users (email, password_hash, role)
                VALUES (?, ?, ?)
                RETURNING id
                """.trimIndent(),
                UUID::class.java,
                account.email.value,
                account.passwordHash,
                account.role.name,
            ) ?: error("INSERT RETURNING id returned no row")
        } catch (e: DuplicateKeyException) {
            // sc-135 Gap 6 / Defect 4a: the application-level findByEmail check is
            // TOCTOU-racy — two concurrent signups can both pass it, then one INSERT
            // hits the `uk_users_email` unique constraint. The DB is the real
            // backstop; translate its signal into the domain exception so the web
            // layer maps it to 409 (email_already_exists) instead of a raw 500.
            throw EmailAlreadyExistsException(account.email)
        }

        return account.copy(id = id)
    }

    private fun ResultSet.toAccount(): Account = Account.fromStorage(
        id = getObject("id", UUID::class.java),
        email = Email(getString("email")),
        passwordHash = getString("password_hash"),
        role = Role.valueOf(getString("role")),
    )
}