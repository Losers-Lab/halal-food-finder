package app.halal.application.account

import app.halal.domain.account.Account
import app.halal.domain.account.Email

/**
 * Persistence port (hexagonal "out" port) for accounts. Implemented by the
 * infrastructure/persistence adapter; the application layer depends only on this
 * contract.
 */
interface AccountRepository {
    fun findByEmail(email: Email): Account?
    fun save(account: Account): Account
}