package com.tahirslist.application.favorite

import java.util.UUID

/**
 * Thrown when a user tries to favour a listing that does not exist. The listing
 * is a foreign key on the favorite; failing fast here keeps the data model
 * consistent instead of surfacing a DB FK violation.
 */
class ListingNotFoundException(val listingId: UUID) :
    RuntimeException("Cannot favourite a listing that does not exist: $listingId")