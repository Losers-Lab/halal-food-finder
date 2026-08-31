package com.tahirslist.application.account

/**
 * Password-hashing port. Implemented with Argon2id (see the Argon2idPasswordHasher
 * in the infrastructure layer). The hash must never be reversible to plaintext.
 */
interface PasswordHasher {
    fun hash(plainPassword: String): String
    fun verify(plainPassword: String, storedHash: String): Boolean
}