-- sc-43 (price filter) + sc-44 (cuisine AND/OR filter): extend the listing and
-- search projections to support price ranges and multi-cuisine matching.
--
-- Why a join table (restaurant_listing_cuisines) instead of widening the single
-- `cuisine` column: the PRD edge case requires AND/OR cuisine chaining — a
-- listing may carry MULTIPLE cuisines, and an AND query must match a listing
-- that has ALL selected cuisines, an OR query ANY. A single scalar column
-- cannot express that. The join table is the authoritative multi-cuisine store
-- (one row per (listing, cuisine)), matched by the search with EXISTS (OR) /
-- COUNT(DISTINCT) (AND). The existing `restaurant_listings.cuisine` column is
-- KEPT as the primary/display cuisine so cards and the Add-Listing single-cuisine
-- write path are unaffected (no public contract change).
--
-- Price: a single nullable NUMERIC on restaurant_listings is the listing's price
-- point; the search filters `minPrice`..`maxPrice` against its denormalised
-- mirror on listing_search (which the search reads alone). NULL price means "no
-- price known" and is EXCLUDED from price filters — the same null semantics V6
-- already documents for cuisine filters.
--
-- Backfills are idempotent: existing rows with a cuisine seed one join row; the
-- search projection's price mirrors its source.

ALTER TABLE restaurant_listings ADD COLUMN price NUMERIC(10,2);
ALTER TABLE restaurant_listings
    ADD CONSTRAINT ck_restaurant_listings_price CHECK (price IS NULL OR price > 0);

ALTER TABLE listing_search ADD COLUMN price NUMERIC(10,2);

-- Authoritative multi-cuisine store. PK (listing_id, cuisine) makes each
-- (listing, cuisine) pair unique; ON DELETE CASCADE keeps cuisines from
-- outliving their listing.
CREATE TABLE restaurant_listing_cuisines (
    listing_id UUID        NOT NULL REFERENCES restaurant_listings (id) ON DELETE CASCADE,
    cuisine    VARCHAR(64) NOT NULL,
    PRIMARY KEY (listing_id, cuisine)
);

-- Lookup support for the search's `cuisine IN (...)` / containment clauses.
CREATE INDEX idx_restaurant_listing_cuisines_cuisine
    ON restaurant_listing_cuisines (cuisine);

-- Backfill: any existing non-null primary cuisine becomes one multi-cuisine row,
-- so previously-added user listings are immediately filterable by their cuisine.
-- NULL-cuisine (seed) rows stay absent — cuisine filters never match them (V6).
INSERT INTO restaurant_listing_cuisines (listing_id, cuisine)
SELECT id, cuisine
FROM restaurant_listings
WHERE cuisine IS NOT NULL
ON CONFLICT (listing_id, cuisine) DO NOTHING;

-- Backfill the search projection's price from its source listing so existing
-- priced rows are immediately filterable.
UPDATE listing_search ls
SET price = rl.price
FROM restaurant_listings rl
WHERE ls.id = rl.id
  AND ls.price IS DISTINCT FROM rl.price;