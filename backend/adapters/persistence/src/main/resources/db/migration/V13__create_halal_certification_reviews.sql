-- Certification reviews (owner claim vertical, sc-46; read by VC in sc-73).
-- Scope: Trust & Verification (Sprint 4, epic 112).
--
-- One row per owner-claim of a listing's halal certification. Drives and records
-- the sc-117 verification state machine on a per-review aggregate:
--   SUBMITTED -> AI_REVIEW -> AI_SUGGESTED -> HUMAN_REVIEW -> { APPROVED | DENIED }
--                                                     |-> REVERSED (terminal)
-- sc-46 writes SUBMITTED / AI_REVIEW (provider outage) / AI_SUGGESTED rows;
-- HUMAN_REVIEW / APPROVED / DENIED / REVERSED are written by the VC vertical (sc-73).
--
-- Design notes:
--  * id: UUID (gen_random_uuid) matching users / restaurant_listings — no count
--    leakage / enumeration.
--  * listing_id -> restaurant_listings id, submitted_by -> users id. NO ACTION on
--    delete: a review is evidence and must not be silently dropped if a listing
--    or account is removed — the operator decides whether to delete first.
--  * state: the aggregate lifecycle, CHECK-constrained to the 7 documented states.
--  * suggestion_* : the AI's CONSERVATIVE suggestion (the seam's only output);
--    suggestion_verdict CHECK-constrained to APPROVE/DENY/NEEDS_REVIEW.
--  * decision_* : the human (VC) binding outcome — NULL until the VC acts (sc-73).
--  * created_at/updated_at: creation and last state change (now() defaults; the
--    writer may supply explicit values for deterministic tests).
CREATE TABLE halal_certification_reviews (
    id                    UUID           NOT NULL DEFAULT gen_random_uuid(),
    listing_id            UUID           NOT NULL,
    submitted_by          UUID           NOT NULL,
    state                 VARCHAR(32)    NOT NULL,
    suggestion_verdict    VARCHAR(32),
    suggestion_confidence DOUBLE PRECISION,
    suggestion_reasoning  TEXT,
    decision_outcome      VARCHAR(32),
    decision_by           UUID,
    decision_reason       TEXT,
    decision_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT pk_halal_certification_reviews PRIMARY KEY (id),
    CONSTRAINT fk_halal_cert_review_listing FOREIGN KEY (listing_id)
        REFERENCES restaurant_listings (id),
    CONSTRAINT fk_halal_cert_review_submitter FOREIGN KEY (submitted_by)
        REFERENCES users (id),
    CONSTRAINT ck_halal_cert_review_state CHECK (state IN (
        'SUBMITTED',
        'AI_REVIEW',
        'AI_SUGGESTED',
        'HUMAN_REVIEW',
        'APPROVED',
        'DENIED',
        'REVERSED'
    )),
    CONSTRAINT ck_halal_cert_review_suggestion CHECK (
        (suggestion_verdict IS NULL AND suggestion_confidence IS NULL AND suggestion_reasoning IS NULL)
        OR suggestion_verdict IN ('APPROVE', 'DENY', 'NEEDS_REVIEW')
    ),
    CONSTRAINT ck_halal_cert_review_decision CHECK (
        (decision_outcome IS NULL) OR
        (decision_outcome IN ('APPROVED', 'DENIED', 'REVERSED') AND decision_by IS NOT NULL)
    )
);

-- Per-listing review lookup (VC surface, sc-73).
CREATE INDEX idx_halal_cert_review_listing ON halal_certification_reviews (listing_id);
-- Review-by-state lookup (VC work queue, sc-73).
CREATE INDEX idx_halal_cert_review_state ON halal_certification_reviews (state);