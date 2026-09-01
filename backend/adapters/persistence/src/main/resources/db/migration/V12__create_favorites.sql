-- Create the favorites table (user ↔ listing favourite relation).
-- Scope: Favorites (sc-50/51/52, epic 113 Accounts).
--
-- Design notes:
--  * idempotency-by-constraint: a composite PRIMARY KEY (user_id, listing_id)
--    means favouriting twice is a no-op at the DB level (a second INSERT is a
--    unique violation, which the adapter swallows via ON CONFLICT DO NOTHING) —
--    the idempotent POST/DELETE contract is enforced structurally, not in code.
--  * user_id: FK to users(id) with ON DELETE CASCADE — when an account is
--    deleted its favourites go with it (no orphan rows).
--  * listing_id: FK to restaurant_listings(id) with ON DELETE CASCADE — when a
--    listing is removed its likes are removed too.
--  * created_at: when the relationship was added, so "my favourites" can be
--    ordered most-recent-first without denormalising a rank column.
CREATE TABLE favorites (
    user_id     UUID        NOT NULL,
    listing_id  UUID        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_favorites PRIMARY KEY (user_id, listing_id),
    CONSTRAINT fk_favorites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favorites_listing FOREIGN KEY (listing_id) REFERENCES restaurant_listings (id) ON DELETE CASCADE
);

-- Per-user lookups ("my favourites"), ordered by recency.
CREATE INDEX idx_favorites_user_created ON favorites (user_id, created_at DESC);