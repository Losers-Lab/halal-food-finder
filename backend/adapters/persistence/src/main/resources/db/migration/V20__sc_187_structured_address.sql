-- sc-187 (founder re-scope #2): structured address fields on listings.
--
-- PROBLEM: the detail page shows only the street line (e.g. "3885 Belt Line Rd"),
-- because the DB carries a single flat `address` string. City/province/postal/
-- country exist in seed_restaurants_geocoded.json but were dropped by the V7 seed
-- migration.
--
-- This migration:
--  1. Adds `city` / `province` / `postal` / `country` to the write-path table
--     restaurant_listings (nullable — legacy/user rows may predate them).
--  2. Mirrors the same four columns into the denormalised search projection
--     listing_search (exactly the sc-184 / sc-119 precedent) so the search read
--     surface can render the full comma-form address without re-joining.
--  3. Backfills the existing V7 seed rows from the source geocoded data, keyed on
--     (name, address) and scoped to seed provenance — the same keys that are
--     unique in the seed corpus (The Halal Guys appears 3× under distinct street
--     lines). User-added rows are left NULL (structured address unknown).
--
-- Semantics:
--  * `address` stays the street line ("3885 Belt Line Rd") for backward compat —
--    this migration does NOT drop or reinterpret it.
--  * `province` is country-agnostic (works for both CA/ON and US/TX).
--  * New client-supplied listings carry the structured fields through the Add
--    Listing write path (domain + repository); only pre-existing rows need this
--    migration-time backfill.

-- Write-path table (source of truth).
ALTER TABLE restaurant_listings ADD COLUMN city     VARCHAR(128);
ALTER TABLE restaurant_listings ADD COLUMN province VARCHAR(64);
ALTER TABLE restaurant_listings ADD COLUMN postal   VARCHAR(32);
ALTER TABLE restaurant_listings ADD COLUMN country  VARCHAR(16);

-- Denormalised search projection (mirrors the source).
ALTER TABLE listing_search ADD COLUMN city     VARCHAR(128);
ALTER TABLE listing_search ADD COLUMN province VARCHAR(64);
ALTER TABLE listing_search ADD COLUMN postal   VARCHAR(32);
ALTER TABLE listing_search ADD COLUMN country  VARCHAR(16);

-- Backfill the existing V7 seed rows from seed_restaurants_geocoded.json
-- (verified_seeds, 30 rows). Keyed on (name, address) with the seed provenance
-- so only the curated seed corpus is touched.
UPDATE restaurant_listings AS l
SET city     = v.city,
    province = v.province,
    postal   = v.postal,
    country  = v.country
FROM ( VALUES
        ('Osmow''s', '505 St. Clair Ave W', 'Toronto', 'ON', 'M6C 1A1', 'CA'),
        ('Paramount Fine Foods', 'The Queensway', 'Toronto', 'ON', 'M8Z 1V1', 'CA'),
        ('The Halal Guys', '563 Yonge St', 'Toronto', 'ON', 'M4Y 1Z2', 'CA'),
        ('Iqbal Foods', 'Birchmount Rd', 'Toronto', 'ON', 'M1K 1C7', 'CA'),
        ('Karahi Point', 'Overlea Blvd', 'Toronto', 'ON', 'M4H 1C3', 'CA'),
        ('Lazeez Shawarma', 'Rogers Rd', 'Toronto', 'ON', 'M6N 2B5', 'CA'),
        ('Aroma Fine Indian Cuisine', '287 King St W', 'Toronto', 'ON', 'M5V 0W3', 'CA'),
        ('Sultan of Samosas', '1677 O''Connor Dr', 'Toronto', 'ON', 'M4A 1W5', 'CA'),
        ('Bamiyan Kabob', '62 Overlea Blvd', 'Toronto', 'ON', 'M4H 1E7', 'CA'),
        ('Watan Kabob', '6974 Financial Dr', 'Mississauga (GTA)', 'ON', 'L5N 7H5', 'CA'),
        ('Madina Naan & Kabob', 'Overlea Blvd', 'Toronto', 'ON', 'M4H 1H1', 'CA'),
        ('The Halal Guys', '307 E 14th St', 'Manhattan', 'NY', '10003', 'US'),
        ('Sami''s Kabab House', '35-57 Crescent St', 'Queens', 'NY', '11106', 'US'),
        ('Ayat', 'Hull Ave', 'Staten Island', 'NY', '10306', 'US'),
        ('Tanoreen', '7523 3rd Ave', 'Bay Ridge, Brooklyn', 'NY', '11209', 'US'),
        ('Bedouin Tent', '405 Atlantic Ave', 'Boerum Hill, Brooklyn', 'NY', '11217', 'US'),
        ('Mamoun''s Falafel', '30 St Marks Pl', 'Manhattan', 'NY', '10003', 'US'),
        ('The Kati Roll Company', '99 MacDougal St', 'Manhattan', 'NY', '10012', 'US'),
        ('Yemen Cafe', '176 Atlantic Ave', 'Brooklyn', 'NY', '11201', 'US'),
        ('Punjabi Deli', '114 E 1st St', 'Manhattan', 'NY', '10002', 'US'),
        ('Kabab King', '73-01 37th Rd', 'Jackson Heights, Queens', 'NY', '11372', 'US'),
        ('Dera', '72-09 Broadway', 'Jackson Heights, Queens', 'NY', '11372', 'US'),
        ('The Halal Guys', '5444 Lemmon Ave', 'Dallas', 'TX', '75209', 'US'),
        ('Al-Amir Lebanese Restaurant & Club', '3885 Belt Line Rd', 'Addison', 'TX', '75001', 'US'),
        ('Afrah', 'E Main St', 'Richardson (DFW)', 'TX', '75081', 'US'),
        ('Andalous Mediterranean Buffet', '1601 N Central Expwy', 'Richardson (DFW)', 'TX', '75080', 'US'),
        ('Ali Baba Mediterranean', '2103 N Central Expwy', 'Richardson (DFW)', 'TX', '75080', 'US'),
        ('Halal Bros', '11521 N FM 620', 'Austin', 'TX', '78726', 'US'),
        ('Halal Wings', '1200 Barbara Jordan Blvd', 'Austin', 'TX', '78723', 'US'),
        ('Caspian Grill', '12518 Research Blvd', 'Austin', 'TX', '78759', 'US')
) AS v(name, address, city, province, postal, country)
WHERE l.name = v.name
  AND l.address = v.address
  AND l.provenance = 'research-seed / photon-geocode';

-- Mirror the source backfill into the search projection (1:1 by id).
UPDATE listing_search AS s
SET city     = r.city,
    province = r.province,
    postal   = r.postal,
    country  = r.country
FROM restaurant_listings r
WHERE r.id = s.id;