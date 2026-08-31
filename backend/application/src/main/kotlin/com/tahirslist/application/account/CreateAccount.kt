package com.tahirslist.application.account

import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email

/**
 * Create Account use case. Validates the submitted password, canonicalises the
 * e-mail, guards against duplicate accounts, hashes the password and persists
 * the account with the default role.
 */
class CreateAccount(
    private val repository: AccountRepository,
    private val hasher: PasswordHasher,
) {

    fun execute(emailRaw: String, password: String): Account {
        // Validate strength first (cheap) before any I/O or hashing.
        PasswordPolicy.validate(password)

        val email = Email(emailRaw) // trims + lowercases

        // Uniqueness guard — fail fast before spending Argon2id work.
        if (repository.findByEmail(email) != null) {
            throw EmailAlreadyExistsException(email)
        }

        val hashed = hasher.hash(password)
        val account = Account.new(email = email, passwordHash = hashed)

        return repository.save(account)
    }
}