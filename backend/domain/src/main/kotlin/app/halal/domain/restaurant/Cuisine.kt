package app.halal.domain.restaurant

/**
 * A restaurant's primary cuisine.
 *
 * Deliberately a free-form, validated value object rather than a closed enum:
 * the PRD/architecture has not ratified a fixed cuisine taxonomy, and inventing
 * one here would bake an un-agreed product decision into the domain. Search/filter
 * cuisine chaining (AND/OR, default OR) is a query-time concern and is out of
 * scope for add-listing; the stored value just needs to be a canonical, bounded
 * string. If the product later ratifies a taxonomy, migrate to an enum then.
 *
 * @param raw the cuisine as supplied by the caller (trimmed + lowercased).
 * @throws IllegalArgumentException if the value is blank or exceeds 64 chars.
 */
data class Cuisine(val raw: String) {

    val value: String = raw.trim().lowercase()

    init {
        require(value.isNotBlank()) { "Cuisine must not be blank." }
        require(value.length <= 64) { "Cuisine must be 64 characters or fewer." }
    }
}
