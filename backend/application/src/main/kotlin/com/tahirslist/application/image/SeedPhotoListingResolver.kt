package com.tahirslist.application.image

import java.util.UUID

/**
 * The result of resolving one [SeedHeroPhoto] to a seed listing id.
 *
 * Resolution is intentionally a seam (impl in the persistence adapter) because
 * the manifest name does not always equal the seeded DB name, and a name like
 * "The Halal Guys" maps to three different locations. Live resolution must be
 * unambiguous — the resolve step returns a single id, or [UNRESOLVED] / an
 * error for ambiguity, never a guess (docs/design/sc-157-image-variants.md).
 */
sealed class SeedPhotoResolution {
    /** Resolved to exactly one listing; ingest proceeds. */
    data class Resolved(val listingId: UUID) : SeedPhotoResolution()

    /** The seed photo could not be matched to any seed listing. */
    data class Unresolved(val reason: String) : SeedPhotoResolution()
}

/**
 * Maps a [SeedHeroPhoto] to the seed listing it belongs to, or reports that it
 * cannot be resolved. Implemented by the persistence adapter against the seeded
 * `restaurant_listings` (provenance 'research-seed / photon-geocode'). Ambiguous
 * matches (multiple candidates, name-only with several locations) must yield
 * [SeedPhotoResolution.Unresolved], not an arbitrary pick.
 */
fun interface SeedPhotoListingResolver {
    fun resolve(photo: SeedHeroPhoto): SeedPhotoResolution
}