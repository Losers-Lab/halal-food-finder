-- sc-118: alcohol-served indicator (partial-halal/alcohol MVP addition, ratified
-- 2026-08-29). Part of the 50% scope — the column is added on BOTH the source
-- listing and the denormalised search projection, exactly like rating (V10) and
-- price (V9), so the search read model (listing_search) stays consistent with the
-- write path on the flag.
--
-- Semantics: a plain boolean, defaulting to FALSE. The MVP is a display-only
-- attribute — there is deliberately NO search filter for alcoholServed in sc-118
-- (the story is "display attribute only"; a filter, if later wanted, is its own
-- story). The domain field `alcoholServed` mirrors this column via
-- RestaurantListing.new/fromStorage, and JdbcRestaurantListingRepository.save
-- writes it to both tables in one transaction.
--
-- Backfill: the column defaults FALSE on both tables, so existing (seed) rows
-- need no explicit UPDATE — every pre-existing listing is non-club dry by default.
ALTER TABLE restaurant_listings ADD COLUMN alcohol_served BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE listing_search ADD COLUMN alcohol_served BOOLEAN NOT NULL DEFAULT FALSE;