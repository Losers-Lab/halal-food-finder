-- sc-184: pickup/delivery service-mode on restaurant listings + search filter.
-- Mirrors the sc-42 hand-cut boolean pattern (V17): delivery is an EXTRA on/off
-- flag a listing claims or not, NOT an enum of service modes. null = "unknown /
-- not claimed" (community/research seed rows); true = offers delivery;
-- false = no delivery (pickup-only).
--
-- The column is added to BOTH the source write-path table (restaurant_listings)
-- and the denormalised search projection (listing_search), exactly like rating
-- (V10) and price (V9), so the search read model stays consistent with the write
-- path on the flag. The persistence adapter writes both in one transaction.
--
-- The column is nullable with no default, so existing (seed) rows are NULL
-- (delivery status unknown) and require no explicit backfill — a seed never
-- claims delivery, matching how V17 left is_hand_cut NULL for pre-existing rows.
ALTER TABLE restaurant_listings ADD COLUMN is_delivery BOOLEAN;

ALTER TABLE listing_search ADD COLUMN is_delivery BOOLEAN;