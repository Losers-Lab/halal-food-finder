-- sc-10 location search: denormalised read-optimised projection for the search
-- vertical (task t_847010c3, contract ratified).
--
-- Why a denormalised table instead of querying restaurant_listings directly:
-- the search read surface needs only a stable subset of the listing (identity,
-- card fields, location) and must answer ST_DWithin-filtered distance queries
-- without coupling to the write-path shape of restaurant_listings (provenance,
-- brand_id, owner_id, generated location_lat_6/lng_6 columns). It stays
-- deliberately narrow and its own GiST index keeps search hot without competing
-- with the write-path index on restaurant_listings.
--
-- Consistency is preserved by the persistence adapter: JdbcRestaurantListingRepository.save
-- writes both tables in one transaction, so any user-added listing is
-- immediately searchable; this migration backfills existing (seed) rows from
-- restaurant_listings so the 30 V7 seeds are searchable out of the box.
--
--  - id            : 1:1 with restaurant_listings.id (FK for integrity).
--  - location      : geography(Point,4326), same as source; GiST-indexed for
--                    ST_DWithin.
--  - Card fields   : what the read surface renders (name, address, cuisine,
--                    cutting_method, verification_status). owner/brand/provenance
--                    are intentionally NOT projected — search never renders them.
CREATE TABLE IF NOT EXISTS listing_search (
    id                  UUID        NOT NULL PRIMARY KEY REFERENCES restaurant_listings (id) ON DELETE CASCADE,
    name                VARCHAR(255) NOT NULL,
    address             VARCHAR(512) NOT NULL,
    location            geography(Point, 4326) NOT NULL,
    cuisine             VARCHAR(64),
    cutting_method      VARCHAR(32)  NOT NULL,
    verification_status VARCHAR(32)  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_listing_search_location
    ON listing_search USING GIST (location);

-- Backfill existing rows so the seed listings are immediately searchable.
-- ON CONFLICT DO NOTHING keeps re-running safely idempotent.
INSERT INTO listing_search (id, name, address, location, cuisine, cutting_method, verification_status)
SELECT
    id,
    name,
    address,
    location,
    cuisine,
    cutting_method,
    verification_status
FROM restaurant_listings
ON CONFLICT (id) DO NOTHING;