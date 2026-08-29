package app.halal.domain.account

/**
 * The six MVP roles (from the product personas: U, RO, VRO, VC, IC, ET).
 * `USER` is the default assigned to a newly created account.
 */
enum class Role {
    USER,
    RESTAURANT_OWNER,
    VERIFIED_RESTAURANT_OWNER,
    VERIFICATION_COMMITTEE,
    ISSUES_COMMITTEE,
    EXTENSION_TRIGGER,
    ;

    companion object {
        /** The role automatically granted when a new account signs up. */
        val DEFAULT: Role = USER
    }
}