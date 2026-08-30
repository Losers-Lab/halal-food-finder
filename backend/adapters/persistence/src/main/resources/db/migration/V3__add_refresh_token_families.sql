-- sc-136: token-family linkage + reuse detection.
--
-- Adds two columns to refresh_tokens:
--  * family_id  — every login mints a token in a fresh family; each rotation
--    keeps that family, so the lineage of one session (login -> many refreshes)
--    is traceable. Reuse of a token whose family is still being used is a theft
--    signal: the application revokes the ENTIRE family at once.
--  * consumed_at — a live token has NULL; rotation marks the old token consumed
--    (soft delete) instead of hard-deleting it, so a *present-but-consumed*
--    token remains discoverable for reuse detection. NULL vs non-NULL replaces
--    the previous hard-delete-only model.
--
-- Backfill: any pre-existing rows (dev/test data) each become their own family
-- so the NOT NULL constraint holds without inventing cross-token lineage.
ALTER TABLE refresh_tokens
    ADD COLUMN family_id   UUID        NULL,
    ADD COLUMN consumed_at TIMESTAMPTZ NULL;

UPDATE refresh_tokens SET family_id = gen_random_uuid() WHERE family_id IS NULL;

ALTER TABLE refresh_tokens
    ALTER COLUMN family_id SET NOT NULL;

-- Whole-family teardown on reuse; also serves "logout everywhere" later.
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens (family_id);