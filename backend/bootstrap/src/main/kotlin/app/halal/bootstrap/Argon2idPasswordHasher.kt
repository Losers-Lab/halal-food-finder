package app.halal.bootstrap

import app.halal.application.account.PasswordHasher
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Argon2id password hasher backed by Spring Security Crypto's
 * Argon2PasswordEncoder (Argon2id flavour). Injected as a [PasswordHasher]
 * bean so the application layer stays framework-free.
 */
@Component
class Argon2idPasswordHasher : PasswordHasher {

    private val encoder: Argon2PasswordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()

    override fun hash(plainPassword: String): String = encoder.encode(plainPassword)

    override fun verify(plainPassword: String, storedHash: String): Boolean =
        encoder.matches(plainPassword, storedHash)
}