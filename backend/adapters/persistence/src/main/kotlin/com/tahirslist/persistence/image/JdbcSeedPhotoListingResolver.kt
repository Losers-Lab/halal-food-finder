package com.tahirslist.persistence.image

import com.tahirslist.application.image.SeedHeroPhoto
import com.tahirslist.application.image.SeedPhotoListingResolver
import com.tahirslist.application.image.SeedPhotoResolution
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * Resolves a [SeedHeroPhoto] to its seeded `restaurant_listings` row.
 *
 * Scoped to seed provenance (`research-seed / photon-geocode` — the only rows
 * the manifest covers). Matching is unambiguous only:
 *  - normalized-name equality first, then normalized-address equality;
 *    "normalized" = lower-case + trimmed (both sides), so a manifest casing or
 *    whitespace variant of a seeded name still matches;
 *  - a row is resolved only if EXACTLY ONE listing matches; multiple candidates
 *    ("The Halal Guys" has 3 locations) or zero -> [SeedPhotoResolution.Unresolved].
 *
 * This deliberately avoids fuzzy name guessing: attaching a hero photo to the
 * wrong restaurant is worse than skipping it and reporting it
 * (docs/design/sc-157-image-variants.md §"Manifest → listing resolution").
 */
@Repository
class JdbcSeedPhotoListingResolver(private val jdbc: JdbcTemplate) : SeedPhotoListingResolver {

    override fun resolve(photo: SeedHeroPhoto): SeedPhotoResolution {
        if (!photo.name.isBlank()) {
            matchUnique("name", photo.name.trim())?.let { return SeedPhotoResolution.Resolved(it) }
        }
        // Name not unique/exact: fall back to normalized address when the row
        // carries a street we can key on (brief-approx neighborhood refs won't).
        val address = photo.addressGiven
        if (!address.isNullOrBlank()) {
            matchUnique("address", address.trim())?.let { return SeedPhotoResolution.Resolved(it) }
        }
        return SeedPhotoResolution.Unresolved("no unique seed listing for '${photo.name}'")
    }

    /** Returns the single matching listing id, or null when 0 or >1 match. */
    private fun matchUnique(column: String, value: String): UUID? {
        val ids = jdbc.query(
            """
            SELECT id FROM restaurant_listings
            WHERE provenance = ? AND lower(btrim($column)) = lower(?)
            """.trimIndent(),
            { rs, _ -> rs.getObject("id", UUID::class.java) },
            SEED_PROVENANCE,
            value,
        )
        return if (ids.size == 1) ids.single() else null
    }

    private companion object {
        const val SEED_PROVENANCE = "research-seed / photon-geocode"
    }
}