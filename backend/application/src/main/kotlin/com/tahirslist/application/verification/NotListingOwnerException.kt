package com.tahirslist.application.verification

import java.util.UUID

/**
 * Thrown when an authenticated account tries to claim (request verification of)
 * a listing it does not own. The claim vertical is owner-guarded (sc-46): only
 * `restaurant_listings.owner_id` may submit proof of ownership + certification.
 */
class NotListingOwnerException(
    val listingId: UUID,
    val claimerId: UUID,
) : RuntimeException("Account $claimerId is not the owner of listing $listingId")