-- sc-42 founder ruling: replace the either/or CuttingMethod (HAND_CUT |
-- MACHINE_CUT | UNSPECIFIED) with a single nullable boolean `is_hand_cut`.
-- There is no machine-cut concept; hand-cut is an extra boolean a listing
-- claims or not, and null means "unknown / not claimed" (community/research
-- seed rows).
--
-- Data mapping (lossless tri-state -> tri-state):
--   HAND_CUT    -> TRUE
--   MACHINE_CUT -> FALSE   (becomes "not hand-cut", not a distinct feature)
--   UNSPECIFIED -> NULL    (unknown / not claimed)
--
-- Both the write-path table (restaurant_listings) and the denormalised search
-- projection (listing_search) carry the column; the CHECK vocabulary and the
-- old column are dropped. The transform is additive-first then drops, so an
-- interrupted migration leaves the new column present but the old data intact.
--
-- Historical migrations V4/V7/V8 are left untouched (applied history); this
-- migration is the single point that moves a greenfield or existing DB to the
-- new shape.

-- write-path table
ALTER TABLE restaurant_listings ADD COLUMN is_hand_cut BOOLEAN;

UPDATE restaurant_listings
SET is_hand_cut = CASE cutting_method
    WHEN 'HAND_CUT'    THEN TRUE
    WHEN 'MACHINE_CUT' THEN FALSE
    ELSE NULL
END;

ALTER TABLE restaurant_listings DROP CONSTRAINT ck_restaurant_listings_cutting_method;
ALTER TABLE restaurant_listings DROP COLUMN cutting_method;

-- denormalised search projection
ALTER TABLE listing_search ADD COLUMN is_hand_cut BOOLEAN;

UPDATE listing_search
SET is_hand_cut = CASE cutting_method
    WHEN 'HAND_CUT'    THEN TRUE
    WHEN 'MACHINE_CUT' THEN FALSE
    ELSE NULL
END;

ALTER TABLE listing_search DROP COLUMN cutting_method;