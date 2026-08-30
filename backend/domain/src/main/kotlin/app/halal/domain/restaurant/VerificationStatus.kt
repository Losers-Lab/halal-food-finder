package app.halal.domain.restaurant

/**
 * The verification lifecycle of a listing.
 *
 * Per the PRD's listing-first model, every restaurant starts **unverified** —
 * anyone can add it, and verification (owner claim + certification review) is a
 * separate, later vertical. Only [UNVERIFIED] is reachable today; [VERIFIED] is
 * reserved for when certificate-based verification lands (it requires a ratified
 * HalalCertification to become legal, so it is deliberately not reachable yet).
 */
enum class VerificationStatus {
    UNVERIFIED,
    VERIFIED,
    ;

    companion object {
        /** The status every newly created listing starts with. */
        val DEFAULT: VerificationStatus = UNVERIFIED
    }
}
