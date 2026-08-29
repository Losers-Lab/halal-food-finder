package app.halal.application.account

/**
 * Rejected because the submitted credentials are wrong OR the account does not
 * exist. A single generic error — never distinguishing "unknown user" from
 * "wrong password" — so the response is not user-enumeration friendly.
 */
class InvalidCredentialsException :
    RuntimeException("Invalid email or password.")