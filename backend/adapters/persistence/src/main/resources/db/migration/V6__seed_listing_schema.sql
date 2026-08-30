-- Adapt restaurant_listings for the listing-first seed model (sc-155).
-- Schema rulings adjudicated by Omar (task t_4097a3a4) and cleared by Adnan:
--   * owner_id becomes NULLABLE — community/research seed rows have no real
--     owning account. FK REFERENCES users(id) with the default NO ACTION still
--     applies whenever a value is present; the claim flow (sc-139+) attaches it.
--     (Explicitly rejected: a reserved "community/system" users row.)
--   * cuisine becomes NULLABLE — seeds carry no ratified cuisine. NULL-cuisine
--     rows are excluded from cuisine filters; there is NO 'ANY' sentinel and the
--     cuisine vocabulary is NOT cross-bred with cutting_method's.
--   * brand_id (nullable) FK -> brands: brand/location split.
--   * provenance: closed-vocab VARCHAR — 'research-seed' / 'photon-geocode' /
--     the combined 'research-seed / photon-geocode' stamped on every seed row.
--     Ordinary user-added listings keep provenance NULL.
--   * idempotent seed ingest: a partial unique index over the geocoded-normalised
--     location (lat/lng rounded to 6 dp, ~0.1 m) scoped to seed provenance, so an
--     INSERT ... ON CONFLICT DO NOTHING dedupes by location, not raw name
--     (The Halal Guys has 3 branches at 3 locations). Scoped to seed rows so a
--     coincident location on an ordinary user listing is never blocked.

ALTER TABLE restaurant_listings ALTER COLUMN owner_id DROP NOT NULL;
ALTER TABLE restaurant_listings ALTER COLUMN cuisine   DROP NOT NULL;

ALTER TABLE restaurant_listings ADD COLUMN brand_id UUID;
ALTER TABLE restaurant_listings ADD CONSTRAINT fk_restaurant_listings_brand
    FOREIGN KEY (brand_id) REFERENCES brands (id);

ALTER TABLE restaurant_listings ADD COLUMN provenance VARCHAR(64);
ALTER TABLE restaurant_listings ADD CONSTRAINT ck_restaurant_listings_provenance CHECK (
    provenance IS NULL OR provenance IN (
        'research-seed',
        'photon-geocode',
        'research-seed / photon-geocode'
    )
);

-- Normalised-location dedupe key. ST_X/ST_Y on a geometry are IMMUTABLE, so the
-- generated columns are legal and give a clean ON CONFLICT target.
ALTER TABLE restaurant_listings
    ADD COLUMN location_lat_6 NUMERIC GENERATED ALWAYS AS
        (round(ST_Y(location::geometry)::numeric, 6)) STORED,
    ADD COLUMN location_lng_6 NUMERIC GENERATED ALWAYS AS
        (round(ST_X(location::geometry)::numeric, 6)) STORED;

CREATE UNIQUE INDEX uq_restaurant_listings_seed_location
    ON restaurant_listings (location_lat_6, location_lng_6)
    WHERE provenance = 'research-seed / photon-geocode';
