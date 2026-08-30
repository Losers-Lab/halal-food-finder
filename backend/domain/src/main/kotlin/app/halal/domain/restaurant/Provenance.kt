package app.halal.domain.restaurant

/**
 * Stamps the origin of a listing row. Closed vocabulary, mirrored by the
 * `ck_restaurant_listings_provenance` CHECK constraint in V6:
 *
 *  - [RESEARCH_SEED]          — the row came from the verified research seed set.
 *  - [PHOTON_GEOCODE]         — coordinates resolved via the Photon/OSM geocoder.
 *  - [RESEARCH_SEED_PHOTON_GEOCODE] — the combined stamp on every seed row
 *                            (`research-seed / photon-geocode`).
 *
 * Ordinary user-added listings (Add Listing use case) carry `null` provenance.
 * The vocabulary is deliberately kept separate from `cutting_method`'s enum
 * (Omar adjudication): no cross-breeding between the two vocabularies.
 */
data class Provenance(val raw: String) {

    val value: String = raw.trim().lowercase()

    init {
        require(value in ClosedVocabulary) {
            "Provenance must be one of [${ClosedVocabulary.joinToString(", ")}], got: '$raw'"
        }
    }

    companion object {
        // Declared before the constants so Companion initialises the vocabulary
        // first (Kotlin initialises companion properties in declaration order);
        // the Provenance constructor validates against this set.
        val ClosedVocabulary: Set<String> = setOf(
            "research-seed",
            "photon-geocode",
            "research-seed / photon-geocode",
        )

        val RESEARCH_SEED = Provenance("research-seed")
        val PHOTON_GEOCODE = Provenance("photon-geocode")
        val RESEARCH_SEED_PHOTON_GEOCODE = Provenance("research-seed / photon-geocode")
    }
}
