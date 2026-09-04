#!/usr/bin/env python3
"""
Generate the Flyway seed migration (V7__seed_restaurants.sql) from the verified
seed corpus seed_restaurants_geocoded.json (repo root, Aisha's research).

Keeps the JSON as the single source of truth; the generated SQL is a materialised
view of it at generation time. Re-run whenever the seed JSON changes, then bump
the migration version (V8, V9, ...) — Flyway checksums migrations, so an edited
V7 would fail on an already-migrated database.

Usage:  python3 scripts/generate_seed_migration.py [path/to/seed_restaurants_geocoded.json]
Output: backend/adapters/persistence/src/main/resources/db/migration/V7__seed_restaurants.sql

Notes:
  * Rows come from `verified_seeds` ONLY. The resolved-but-unconfirmed and
    not-on-photon lists are deliberately NOT seeded (amanah / Photon-only ruling).
  * Every row: UNVERIFIED, provenance 'research-seed / photon-geocode',
    cuisine NULL, is_hand_cut NULL (unknown; sc-42 boolean), owner_id NULL,
    brand_id -> brand.
  * Idempotent: brands ON CONFLICT (name), listings ON CONFLICT (normalised
    location, scoped to seed provenance) DO NOTHING.
  * No ZIP/postal-format assumption: CA/ON Canadian postal strings are kept as-is.
"""
import json
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_JSON = REPO_ROOT / "seed_restaurants_geocoded.json"
OUT = (
    REPO_ROOT
    / "backend/adapters/persistence/src/main/resources/db/migration/V7__seed_restaurants.sql"
)

PROVENANCE = "research-seed / photon-geocode"


def esc(s: str) -> str:
    return s.replace("'", "''")


def sql_str(s: str) -> str:
    return "'" + esc(s) + "'"


def main() -> None:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_JSON
    data = json.loads(src.read_text())
    verified = data["verified_seeds"]

    # Collect rows in canonical order, preserving first-appearance ordering.
    rows = []
    for city in ("Toronto", "New_York", "Jackson_Heights", "Dallas", "Austin"):
        rows.extend(verified.get(city, []))

    # Distinct brand names in first-appearance order.
    brands = list(dict.fromkeys(r["name"] for r in rows))

    total = len(rows)
    total_brands = len(brands)
    assert total == 30, f"expected 30 verified rows, got {total}"
    assert total_brands == 28, f"expected 28 distinct brands, got {total_brands}"

    out = []
    out.append("-- Seed verified halal restaurants (sc-155).")
    out.append("-- GENERATED from seed_restaurants_geocoded.json by scripts/generate_seed_migration.py")
    out.append(f"-- (last generated against {src.name}, {total} locations / {total_brands} brands).")
    out.append("-- Do NOT hand-edit; regenerate, then bump to the next migration version.")
    out.append("--")
    out.append("-- Source: OpenStreetMap via Photon (photon.komoot.io), ODbL 1.0; coordinates WGS84.")
    out.append("-- Every row starts UNVERIFIED with provenance 'research-seed / photon-geocode'")
    out.append("-- (listing-first model), cuisine NULL, is_hand_cut NULL, owner_id NULL.")
    out.append("-- Since sc-187, each row also carries the structured address fields")
    out.append("-- (city, province, postal, country) read straight from the seed JSON; the")
    out.append("-- committed V7 (applied history) predates them — the current DB shape is")
    out.append("-- reached via V20__sc_187_structured_address.sql, so DO NOT rewrite V7;")
    out.append("-- regenerate into a NEW bumped-version seed migration instead.")
    out.append("-- Brand vs location: one brand row per distinct name; The Halal Guys is 1 brand, 3 locations.")
    out.append("-- Idempotent: brands ON CONFLICT (name); listings ON CONFLICT (normalised location,")
    out.append("-- scoped to seed provenance) DO NOTHING — dedupe by geocoded-normalised location, not raw name.")
    out.append("")

    out.append("-- Brands (one per distinct name).")
    for b in brands:
        out.append(f"INSERT INTO brands (name) VALUES ({sql_str(b)}) ON CONFLICT (name) DO NOTHING;")

    out.append("")
    out.append("-- Listings (locations keyed by geolocation; brand resolved by name).")
    for r in rows:
        out.append(
            "INSERT INTO restaurant_listings"
            " (name, address, city, province, postal, country, location, cuisine, is_hand_cut, owner_id, brand_id, provenance, verification_status)"
            " VALUES ("
            f" {sql_str(r['name'])},"
            f" {sql_str(r['address'])},"
            f" {sql_str(r['city'])},"
            f" {sql_str(r['province'])},"
            f" {sql_str(r['postal'])},"
            f" {sql_str(r['country'])},"
            f" ST_SetSRID(ST_MakePoint({r['lon']:.6f}, {r['lat']:.6f}), 4326)::geography,"
            " NULL,"
            " NULL,"
            " NULL,"
            f" (SELECT id FROM brands WHERE name = {sql_str(r['name'])}),"
            f" {sql_str(PROVENANCE)},"
            " 'UNVERIFIED'"
            ")"
            " ON CONFLICT (location_lat_6, location_lng_6) WHERE provenance = 'research-seed / photon-geocode'"
            " DO NOTHING;"
        )

    out.append("")
    out_text = "\n".join(out) + "\n"
    OUT.write_text(out_text)
    print(f"Wrote {OUT} ({len(rows)} listings / {len(brands)} brands).")


if __name__ == "__main__":
    main()
