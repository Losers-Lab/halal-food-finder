-- Allow the VERIFIED verification status (human review vertical sc-73).
-- Scope: Trust & Verification (Sprint 4, epic 112).
--
-- sc-46/sc-117 narrowed restaurant_listings.verification_status to UNVERIFIED only
-- (listing-first model: a listing is never auto-promoted). sc-73 opens the human
-- Verification Committee path, the ONLY route to a VERIFIED listing: the VC
-- approves a certification review, and the approval promotes the listing to
-- VERIFIED (via JdbcRestaurantListingRepository.updateVerificationStatus, which
-- updates this source row and the listing_search read mirror in one transaction).
--
-- The listing_search mirror has no CHECK on this column, so no change is needed
-- there; it is widened purely by writing VERIFIED to it. Only the source
-- restaurant_listings constraint is redefined to admit the new status. DERIVED is
-- reserved for edge cases (cert expiry/revocation) and is intentionally not added
-- yet -- nothing writes it, so it stays out of the legal state set until a story
-- actually needs it (sc-79).
ALTER TABLE restaurant_listings
    DROP CONSTRAINT ck_restaurant_listings_verification_status,
    ADD CONSTRAINT ck_restaurant_listings_verification_status CHECK (verification_status IN (
        'UNVERIFIED',
        'VERIFIED'
    ));