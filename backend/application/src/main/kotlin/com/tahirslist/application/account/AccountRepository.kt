package com.tahirslist.application.account

import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import java.util.UUID

/**
 * Persistence port (hexagonal "out" port) for accounts. Implemented by the
 * infrastructure/persistence adapter; the application layer depends only on this
 * contract.
 */
interface AccountRepository {
    fun findByEmail(email: Email): Account?
    fun findById(id: UUID): Account?
    fun save(account: Account): Account
}