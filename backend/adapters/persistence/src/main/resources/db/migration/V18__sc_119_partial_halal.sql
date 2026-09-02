-- sc-119: partial-halal modeling (founder re-scope 09-01).
--
-- Adds the partial-halal model on top of the sc-42 hand-cut boolean (V17),
-- which already replaced cutting_method with the nullable is_hand_cut boolean.
-- This migration does NOT touch the cutting/hand-cut columns.
--
--  1. restaurant_listings gains `halal_scope`
--     (NOT_DISCLOSED / PARTIALLY_HALAL / FULLY_HALAL) and `cross_contamination`
--     (NO_CROSS_CONTAMINATION / PRESENT / UNCERTAIN).
--  2. A child table restaurant_halal_items records WHICH items are halal per
--     listing (mirrors the restaurant_listing_cuisines precedent).
--  3. listing_search (the search index projection) mirrors halal_scope and
--     cross_contamination so the read surface + cross-contamination INDEX
--     GATE work without re-joining the source row.
--
-- Semantics (sc-119 design doc):
--  * Verification is ORTHOGONAL to halal scope — a PARTIALLY_HALAL place may be
--    VERIFIED (it can hold a certificate for the halal portion).
--  * cross_contamination is a HARD INDEX GATE: only 'NO_CROSS_CONTAMINATION'
--    qualifies a listing for the search index. The repository mirror excludes
--    PRESENT/UNCERTAIN rows; the search query additionally constrains it.
--  * New rows default cross_contamination = 'UNCERTAIN' (conservative: exempt
--    until a no-cross-contamination qualification exists) and halal_scope =
--    'NOT_DISCLOSED'.
--
-- Backfill decision (flagged to Adnan in the PR): EXISTING rows are set to
-- cross_contamination = 'NO_CROSS_CONTAMINATION' so the currently-curated seed
-- index stays searchable (no silent search regression). This mirrors the prior
-- curation; the gate still protects going forward.

-- restaurant_listings: partial-halal scope + cross-contamination gate.
ALTER TABLE restaurant_listings ADD COLUMN halal_scope VARCHAR(32) NOT NULL DEFAULT 'NOT_DISCLOSED';
ALTER TABLE restaurant_listings ADD COLUMN cross_contamination VARCHAR(32) NOT NULL DEFAULT 'UNCERTAIN';

ALTER TABLE restaurant_listings ADD CONSTRAINT ck_restaurant_listings_halal_scope CHECK (
    halal_scope IN ('NOT_DISCLOSED', 'PARTIALLY_HALAL', 'FULLY_HALAL')
);
ALTER TABLE restaurant_listings ADD CONSTRAINT ck_restaurant_listings_cross_contamination CHECK (
    cross_contamination IN ('NO_CROSS_CONTAMINATION', 'PRESENT', 'UNCERTAIN')
);

-- Child table: WHICH items are halal per listing (sc-119 per-item scope).
CREATE TABLE restaurant_halal_items (
    listing_id UUID        NOT NULL REFERENCES restaurant_listings (id) ON DELETE CASCADE,
    name       VARCHAR(128) NOT NULL,
    is_halal   BOOLEAN     NOT NULL,
    PRIMARY KEY (listing_id, name)
);

-- Backfill existing rows to keep the curated index searchable.
UPDATE restaurant_listings SET cross_contamination = 'NO_CROSS_CONTAMINATION';

-- listing_search: mirror halal_scope + cross_contamination.
ALTER TABLE listing_search ADD COLUMN halal_scope VARCHAR(32) NOT NULL DEFAULT 'NOT_DISCLOSED';
ALTER TABLE listing_search ADD COLUMN cross_contamination VARCHAR(32) NOT NULL DEFAULT 'UNCERTAIN';

ALTER TABLE listing_search ADD CONSTRAINT ck_listing_search_cross_contamination CHECK (
    cross_contamination IN ('NO_CROSS_CONTAMINATION', 'PRESENT', 'UNCERTAIN')
);

-- Backfill the mirror with the source cross-contamination state.
UPDATE listing_search l
SET cross_contamination = r.cross_contamination,
    halal_scope        = r.halal_scope
FROM restaurant_listings r
WHERE r.id = l.id;