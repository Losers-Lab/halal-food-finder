package com.tahirslist.domain.account

/**
 * A validated, canonical e-mail address. Normalised to lowercase in the
 * constructor so uniqueness checks compare the same address regardless of case.
 *
 * @param raw the address as supplied by the caller (whitespace is trimmed).
 * @throws IllegalArgumentException if the address has no `@`, no local part, or
 *         no domain.
 */
data class Email(val raw: String) {

    val value: String = raw.trim().lowercase()

    init {
        require(value.contains("@")) { "Email must contain an '@' separator." }
        val parts = value.split("@", limit = 2)
        require(parts[0].isNotBlank()) { "Email must have a local part." }
        require(parts[1].isNotBlank()) { "Email must have a domain." }
    }
}