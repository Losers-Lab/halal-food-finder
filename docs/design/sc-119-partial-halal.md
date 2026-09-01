# Partial-Halal Modeling — per-item scope, cross-contamination gate, boolean hand-cut (sc-119)

> Backend modeling story sc-119 (Sprint 4, epic 112 Trust & Verification). The
> founder re-scoped this card on 09-01 from the original "full vs partial"
> edge case (U-14) into an IN-MVP modeling directive. This doc records the
> resulting domain/persistence model and the committed design decisions.
>
> Scope discipline: **modeling only** — domain model, migration, persistence
> write/read paths, search index gate, and the boolean hand-cut filter refactor
> (sc-42 fold-in per founder decision #4). Full frontend edge-case UX is a
> follow-up, not part of this card.

## Founder re-scope (verbatim acceptance themes, 09-01)

1. Model **which items/meat are halal per listing** — not just a full/partial bit.
2. A **partial-halal place can still be verified** (it may hold a certificate).
   Verification status is orthogonal to halal scope.
3. **Cross-contamination is a HARD index gate:** if it occurs, or may/uncertain
   occur, the restaurant is **not included in our index**. Only a
   no-cross-contamination qualification is indexed.
4. **Hand-cut is an extra boolean filter** — no "machine-cut" terminology
   anywhere. The sc-42 `cuttingMethod` filter is refactored to a boolean
   hand-cut filter (MACHINE_CUT removed).

## Domain model (backend/domain — `restaurant` package)

### 1. Per-item halal scope

New value types:

```kotlin
enum class HalalScope { FULLY_HALAL, PARTIALLY_HALAL, NOT_DISCLOSED }

data class HalalItem(val name: String, val isHalal: Boolean) {
    init { require(name.isNotBlank()) { "Item name must not be blank" } }
}
```

`RestaurantListing` gains:
- `halalScope: HalalScope = NOT_DISCLOSED` — the top-level disclosure used by the
  verification tag ("does it imply the whole restaurant is halal? No if PARTIALLY").
- `halalItems: Set<HalalItem> = emptySet()` — WHICH items are halal.

The verification badge and halal scope are **independent**: `verificationStatus`
(VERIFIED/UNVERIFIED) certifies the *certificate/claim*, while `halalScope`
describes *coverage*. A PARTIALLY_HALAL place can be VERIFIED; the read surface
carries both so a "Verified" badge never implies the entire restaurant is halal
(amanah / trust-language requirement).

### 2. Cross-contamination — hard index gate

```kotlin
enum class CrossContamination {
    NO_CROSS_CONTAMINATION, // declared/qualified -> indexed
    PRESENT,                // cross-contamination occurs -> excluded
    UNCERTAIN;              // may/uncertain -> excluded

    fun isIndexQualified(): Boolean = this == NO_CROSS_CONTAMINATION
}
```

`RestaurantListing` gains `crossContamination: CrossContamination = UNCERTAIN`.
Gate rule (founder #3): only `NO_CROSS_CONTAMINATION` qualifies for `listing_search`.

### 3. Boolean hand-cut (sc-42 fold-in)

`CuttingMethod` enum (HAND_CUT/MACHINE_CUT/UNSPECIFIED) is **deleted**. The
listing carries `handCut: Boolean?` (`null` = unspecified/unknown, the old
UNSPECIFIED). `CuttingMethodFilter` (HAND_CUT/MACHINE_CUT/BOTH) is **deleted**;
`ListingSearchFilters.handCut: Boolean?` means `true` = hand-cut only, absent/`false`
= any. "machine-cut" appears nowhere.

## Persistence (backend/adapters/persistence)

New migration `V16__sc_119_partial_halal.sql`:

- `restaurant_listings`: `ADD hand_cut BOOLEAN`, `ADD halal_scope
  VARCHAR(32) NOT NULL DEFAULT 'NOT_DISCLOSED'`, `ADD cross_contamination
  VARCHAR(32) NOT NULL DEFAULT 'UNCERTAIN'`; `DROP COLUMN cutting_method`.
- Child table `restaurant_halal_items(listing_id FK CASCADE, name, is_halal,
  PK(listing_id,name))` — mirrors the `restaurant_listing_cuisines` precedent.
- `listing_search` (the **index**): `ADD hand_cut BOOLEAN`, `ADD halal_scope
  VARCHAR(32)`, `ADD cross_contamination VARCHAR(32)`; `DROP COLUMN cutting_method`.
- **Backfill decision (flagged to Adnan):** existing rows are backfilled to
  `cross_contamination = 'NO_CROSS_CONTAMINATION'` so the currently-curated seed
  index stays searchable (no silent search regression). New rows default
  `UNCERTAIN` -> not indexed until a NO_CROSS_CONTAMINATION qualification exists.
  `cutting_method` maps HAND_CUT -> `hand_cut = true`, else `NULL`.

| Column                    | Table                | Default         |
|---------------------------|----------------------|-----------------|
| `hand_cut`                | restaurant_listings  | NULL            |
| `halal_scope`             | restaurant_listings  | NOT_DISCLOSED   |
| `cross_contamination`     | restaurant_listings  | UNCERTAIN       |
| `restaurant_halal_items`  | child table          | —               |
| `hand_cut`                | listing_search       | (mirrored)      |
| `halal_scope`             | listing_search       | (mirrored)      |
| `cross_contamination`     | listing_search       | (mirrored)      |

### Index gate (write + read)

- **Write (mirror):** `JdbcRestaurantListingRepository.mirrorIntoListingSearch`
  inserts/updates listing_search ONLY when `listing.crossContamination.isIndexQualified()`;
  non-qualified listings are removed from listing_search (the index never holds
  PRESENT/UNCERTAIN rows).
- **Read (search):** `JdbcListingSearchQuery` additionally constrains
  `cross_contamination = 'NO_CROSS_CONTAMINATION'` as defence-in-depth, and the
  `handCut` filter maps to `hand_cut = true`.

## Search / read surface

- `ListingSearchFilters.handCut: Boolean?` replaces `cuttingMethod`.
- `ListingSearchResult` / browse/detail cards expose `handCut`, `halalScope` and
  `crossContamination` so the trust components (docs/design/trust-components.md —
  CutMethodIndicator will show hand-cut only) never overstate.
- OpenAPI spec regenerated to the new field names; MACHINE_CUT removed.

## Foundry seed handling

Foundry seed data (`V7`) supplies `UNSPECIFIED` cutting -> backfilled to
`hand_cut = NULL` (unspecified), and `NO_CROSS_CONTAMINATION` cross-contamination
(keeps the curated index populated). Halal scope defaults NOT_DISCLOSED for seeds.

## Out of scope (follow-up, per card "modeling only")

- Frontend UX: partial-halal display, CutMethodIndicator re-render, verification
  tag scope semantics (Maryam).
- Sc-118/other: no filter for `alcoholServed` was added here (already display-only).
- Any AI/model involvement in detecting partial halal from cert images.

## Acceptance checks to verify

1. Domain models per-item halal scope (`halalItems` + `halalScope`) and does not
   couple verification status to full halal-ness.
2. Cross-contamination gate: PRESENT/UNCERTAIN listings are excluded from the
   search index (mirror + query); NO_CROSS_CONTAMINATION is indexed.
3. Boolean hand-cut filter on search; no "machine-cut" string anywhere in backend.
4. Migration + repository round-trip all new fields; `./gradlew test` green.
5. `[sc-119]` commits; PR open with Author hamza.