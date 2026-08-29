package app.halal.application.account

/**
 * Policy that defines the minimum acceptable password strength for generated
 * accounts. In this MVP a password must be at least 8 characters and must not
 * be blank. Tune (length + complexity) without coupling to callers.
 */
object PasswordPolicy {

    const val MIN_LENGTH = 8

    fun validate(password: String) {
        if (password.isBlank() || password.length < MIN_LENGTH) {
            throw WeakPasswordException("Password must be at least $MIN_LENGTH characters.")
        }
    }
}