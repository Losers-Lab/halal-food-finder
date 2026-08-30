package app.halal.application.image

/**
 * One row of the seed hero-photo manifest
 * (docs/research/seed-photos-2026-08-30.json). Carrier of the fields ingest
 * needs: where to fetch the image and enough identity to resolve the listing
 * it belongs to. Only the fields the backend consumes are modeled here — the
 * richer fields (license, address_confidence, vision ratings) stay in the
 * research artifact.
 *
 * @param name           the seed's display name from the manifest (may not equal
 *                       the seeded DB name — e.g. "Iqbal Foods Birchmount" vs a
 *                       seed "Iqbal Foods").
 * @param city           seed city, used as a disambiguator.
 * @param addressGiven   brief address the research brief carried (may be
 *                       neighborhood-level).
 * @param heroUrl        remote image to fetch and store.
 */
data class SeedHeroPhoto(
    val name: String,
    val city: String?,
    val addressGiven: String?,
    val heroUrl: String,
)