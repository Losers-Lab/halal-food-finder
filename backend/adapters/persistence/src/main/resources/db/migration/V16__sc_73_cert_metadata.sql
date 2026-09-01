-- Certification display metadata (sc-73 read-surface follow-up).
-- Scope: Trust & Verification (Sprint 4, epic 112).
--
-- The CertificatePanel (detail-page.md §1.2) shows Certifier / Last reviewed /
-- Expires / View certificate for a VERIFIED listing. The VERIFIED state is
-- reached only through the human VC approve path (sc-73), and at that moment the
-- committee member is looking at the submitted certificate image. The two facts
-- they transcribe from it -- the issuing body name and the certificate expiry --
-- are therefore recorded on the review AT APPROVAL, not earlier:
--
--   * certifier  VARCHAR  -- the issuing body / certifier name (e.g. "HFSAA").
--                           NULL when unknown (omit the field, per detail-page.md).
--   * expires_on  DATE    -- the certificate's expiry date.
--                           NULL when not captured (omit the field).
--
-- reviewedOn is NOT stored here: it is the VC's decision_at (the instant the
-- review was APPROVED), already persisted on this row. The certification image
-- is not referenced here either -- it lives in the object store (S3/MinIO) via
-- CertificationImageStorage, keyed by listing. Surfacing that image's URL on a
-- public read endpoint is a separate, security-reviewed decision (Omar). This
-- migration only adds the two human-transcribed display facts.
--
-- Additive + nullable: existing rows (pre-sc-73 read-surface) carry NULL and the
-- frontend omits those fields, exactly as before this change.
ALTER TABLE halal_certification_reviews
    ADD COLUMN certifier VARCHAR(200),
    ADD COLUMN expires_on DATE;