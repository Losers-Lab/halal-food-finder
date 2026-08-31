-- sc-45 (rating filter) + sc-72 (distance/radius filter): extend the listing
-- and search projections to carry an optional rating, so the search can narrow
-- by a minimum rating on top of the sc-10 location radius and every filter.
--
-- Rating source & semantics: ratings are enrichment data (Google/Yelp U-10)
-- with an owner/user-entered fallback (ARCHITECTURE.md). A listing simply MAY
-- carry a rating. It is stored as a nullable NUMERIC(3,2) on restaurant_listings
-- (the source of truth) and mirrored onto listing_search (the read model the
-- search reads alone), exactly like price (V9). The search's `minRating` filter
-- is `listing_search.rating >= ?`.
--
-- NULL rating means "no rating known" and is EXCLUDED from the rating filter --
-- the same null semantics V6/V9 already document for cuisine and price. A
-- present rating is constrained to the 0..5 scale by an explicit CHECK (the
-- NUMERIC(3,2) column alone would allow up to 9.99, so the CHECK is the real
-- boundary).
--
-- The distance filter itself is the sc-10 radius (`ST_DWithin` over the
-- geography(Point,4326) column already GiST-indexed by V8); sc-72 only ratifies
-- that it composes with the other filters in the single search query. No new
-- spatial index is needed here.

ALTER TABLE restaurant_listings ADD COLUMN rating NUMERIC(3,2);
ALTER TABLE restaurant_listings
    ADD CONSTRAINT ck_restaurant_listings_rating
    CHECK (rating IS NULL OR (rating >= 0 AND rating <= 5));

ALTER TABLE listing_search ADD COLUMN rating NUMERIC(3,2);

-- Backfill the search projection's rating from its source listing so any
-- existing (future-rated) rows are immediately filterable.
UPDATE listing_search ls
SET rating = rl.rating
FROM restaurant_listings rl
WHERE ls.id = rl.id
  AND ls.rating IS DISTINCT FROM rl.rating;