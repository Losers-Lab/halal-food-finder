package app.halal.application.listing

import java.util.UUID

/**
 * Thrown when adding a listing whose owning account does not exist. The owning
 * account must exist up front (it is a foreign key on the listing); failing fast
 * here keeps the data model consistent instead of surfacing a DB FK violation.
 */
class ListingOwnerNotFoundException(val ownerId: UUID) :
    RuntimeException("Cannot create a listing for an owner that does not exist: $ownerId")
