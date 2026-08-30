-- Brand / location split (sc-155). A brand (e.g. "The Halal Guys") can have
-- multiple branch locations in restaurant_listings; each location is keyed by
-- its geolocation. This table is created first so the nullable brand_id FK on
-- restaurant_listings (added in V6) has a target.
--
-- Data source note: brand identities come from OSM via Photon (ODbL). The ODbL
-- share-alike stance on OSM-derived fields is an open founder decision
-- (docs/reviews/sc-138-external-services.md §5) — flagged here, not decided.
CREATE TABLE brands (
    id         UUID         NOT NULL DEFAULT gen_random_uuid(),
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_brands PRIMARY KEY (id),
    -- One brand row per name; the get-or-create in the seed path relies on this.
    CONSTRAINT uq_brands_name UNIQUE (name)
);
