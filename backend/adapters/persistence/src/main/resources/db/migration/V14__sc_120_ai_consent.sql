-- AI-analysis consent on certification reviews (owner claim vertical, sc-120).
-- Scope: Trust & Verification (Sprint 4, epic 112).
--
-- Privacy (sc-120): the owner's certification image may be sent to a hosted AI
-- for automated analysis, so the owner must explicitly consent BEFORE upload.
-- Consent is recorded with the verification request itself (per-row): the
-- timestamp of when the owner consented. NULL means the review predates consent
-- (none was ever given) — sc-46 rows carry no consent.
--
-- This is an additive, nullable column: it does NOT change the definition of
-- existing rows, and the CHECK constraints on state/suggestion/decision are
-- untouched. Rejection of un-consented claims happens in the application layer
-- (ClaimListing), before the image is ever archived.
ALTER TABLE halal_certification_reviews
    ADD COLUMN ai_consent_at TIMESTAMPTZ;