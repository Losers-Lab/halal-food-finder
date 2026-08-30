# Seed Restaurant Research Brief — NYC, Dallas, Austin, Toronto

- **Author:** Aisha (Research & Data Specialist)
- **Date:** 2026-08-30
- **Consumer:** Adnan (decides sc-155 ingest dispatch) → Hamza (backend/DB seed-ingest + Photon integration)
- **Feeds:** Shortcut story **sc-155** (Seed restaurant test data + Photon geocoding) — iteration 130, current sprint.
- **Scope:** CANDIDATE SEED LIST only. Nothing is ingested into the DB by this task.

## 0. Founder rulings / constraints in force (authoritative for seed-ingest)

| # | Ruling | Source |
|---|--------|--------|
| 1 | **Geocoder = Photon (OSM), NOT Google Places.** The earlier "Google autocomplete+geocoding hybrid" recommendation in `docs/research/external-services-brief-2026-08-29.md` is **superseded for MVP** by this founder ruling. | Arham, 2026-08-30 (relayed by Adnan) |
| 2 | **Seed source = OSM/Overpass** ($0), ODbL-attribution aware. | external-services brief §4 |
| 3 | **Google Places is never a seed corpus** (ToS: Place IDs only; live enrichment; cache ≤30 days). No scraping Google/Yelp, ever. | external-services brief §4 |
| 4 | **hand-cut / machine-cut are sparsely tagged in OSM — human-sourced cold start.** | external-services brief §4 |
| 5 | Every seed row starts **UNVERIFIED** with provenance `research-seed / photon-geocode` (listing-first product model). | notes_for_hamza (seed JSON) |

**Implication for seed-ingest:** the ingest task must (a) tag ODbL attribution as the data source, (b) seed only venues that resolve on Photon/OSM (no invented coordinates), (c) set `verification=unverified` + provenance on every row, and (d) treat cutting-method attributes as empty until human-sourced.

---

## 1. Deliverable summary

The candidate seed list (the machine-readable artifact) is `seed_restaurants_geocoded.json` at the repo root alongside this brief. It contains:

- **verified_seeds** — **30 restaurants** confirmed to resolve on Photon/OSM under their real name in the correct city/state: Toronto **11**, New York **9**, Jackson Heights **2** (founder's home area), Dallas **5**, Austin **3**.
- **resolved_on_photon_but_halal_status_unconfirmed** — 3 Austin venues that resolve geographically but whose halal status is **not** confirmed. Kept separate; **do not seed until confirmed** (amanah).
- **not_on_photon_popular_but_not_seedable** — 9 famous candidates (incl. Adel's Famous Halal Food, Jackson Diner) that did **not** resolve on Photon under their real identity (name-alias false positives or no match). Excluded per the Photon-only ruling. They need an official-sources address pass IF/WHEN a non-Photon geocoder or manual entry is supported.

**OSM coverage is thin in Toronto and Austin**, as flagged in this task's scope: Toronto's richest coverage is the Overlea/Mississauga corridor (Afghan/Pakistani kabob cluster), and Austin has only 3 genuinely-confirmed venues. NYC/Dallas coverage is stronger for the selected Mediterranean/southern-Asian venues.

---

## 2. Candidate seed list by metro

Verified-on-Photon rows. Cuisine is **assigned from domain knowledge to enrich the seed** (OSM `cuisine` tags are sparse and were empty for most rows) — confirm each at ingest/claim; the OSM node itself establishes name+location only.

### 2.1 Toronto (non-US locale test — GTA, Canadian postal format)
| Venue | Neighborhood/Address (approx) | Cuisine (brief-assigned) | Source confidence | Verified/noteworthy signal |
|---|---|---|---|---|
| Osmow's | St. Clair Ave W (Toronto) | Mediterranean / Canadian bowl chain | High (Photon) | Popular fast-casual halal chain (GTA-origin) |
| Paramount Fine Foods | The Queensway (Etobicoke, Toronto) | Lebanese | High (Photon) | Nationally known halal Lebanese chain; multiple GTA branches |
| The Halal Guys | 563 Yonge St (Toronto) | Halal platters / gyro | High (Photon + Overpass `diet:halal=yes`) | Franchise flagship; OSM node tagged `diet:halal=yes` |
| Iqbal Foods | Birchmount Rd (Scarborough) | Halal grocery / meat + takeaway | High (Photon) | Well-known South Asian halal grocer |
| Karahi Point | Overlea Blvd (Toronto) | Pakistani | High (Photon) | Popular karahi/kabob spot in the Overlea halal cluster |
| Lazeez Shawarma | Rogers Rd (Toronto) | Shawarma / Mediterranean | High (Photon) | Large regional shawarma chain |
| Aroma Fine Indian Cuisine | 287 King St W (downtown) | Indian | High (Photon) | Downtown halal Indian |
| Sultan of Samosas | O'Connor Dr (Toronto) | South Asian samosas/tandoori | High (Photon) | Well-loved samosa brand |
| Bamiyan Kabob | Overlea Blvd (Toronto) | Afghan | High (Photon) | Afghan kabobhouse in the Overlea cluster |
| Watan Kabob | Mississauga (GTA) | Afghan/Pakistani | High (Photon) | GTA halal kabobhouse |
| Madina Naan & Kabob | Overlea Blvd (Toronto) | Afghan/Pakistani | High (Photon) | Overlea cluster kabob + naan |

### 2.2 New York City
| Venue | Neighborhood/Address (approx) | Cuisine (brief-assigned) | Source confidence | Verified/noteworthy signal |
|---|---|---|---|---|
| The Halal Guys | 307 E 14th St (Manhattan) | Halal platters / gyro | High (Photon + Overpass `diet:halal=yes`) | The iconic halal cart brand; OSM `diet:halal=yes` |
| Sami's Kabab House | Crescent St, Astoria (Queens) | South Asian (Pakistani/Bengali) | High (Photon) | Long-running halal kababhouse |
| Ayat | Hull Ave (Staten Island) | Palestinian / halal meat+seafood | High (Photon) | Well-reviewed Palestinian spot |
| Tanoreen | 3rd Ave (Bay Ridge, Brooklyn) | Palestinian | High (Photon) | Emmy-nominated chef-run Palestinian restaurant |
| Bedouin Tent | Atlantic Ave (Boerum Hill, Brooklyn) | Palestinian/Mediterranean | High (Photon) | Beloved neighborhood halal spot |
| Mamoun's Falafel | St Marks Pl (Manhattan) | Middle Eastern / falafel | High (Photon + Overpass) | NYC's oldest falafel shop; Overpass has 3 nodes (one `diet:halal=yes`) |
| The Kati Roll Company | MacDougal St (Manhattan) | Indian street food | High (Photon + Overpass) | Halal kati rolls; Overpass `diet:halal=yes` (JC branch noted) |
| Yemen Cafe | Atlantic Ave (Brooklyn) | Yemeni | High (Photon) | Popular Yemeni halal spot |
| Punjabi Deli | E 1st St (Manhattan) | Indian (halal meats + veg) | High (Photon) | East Village halal deli; confirm halal at claim |

### 2.3 Jackson Heights, Queens (founder's home area — # flag)
| Venue | Neighborhood/Address | Cuisine | Source confidence | Verified/noteworthy |
|---|---|---|---|---|
| Kabab King | 37th Rd, Jackson Heights | Pakistani BBQ | High (Photon) | Famous Pakistani street kebab |
| Dera | Broadway, Jackson Heights | Pakistani | High (Photon) | Long-standing Pakistani halal fixture |

> Note: the even more famous **Jackson Diner** (Indian) is NOT seedable via Photon — Photon only returns "Jax Inn Diner" (a different venue), a name-alias false positive → kept in the exclusion list. It stays out until a manual/non-Photon path exists.

### 2.4 Dallas (DFW)
| Venue | Neighborhood/Address | Cuisine | Source confidence | Verified/noteworthy |
|---|---|---|---|---|
| The Halal Guys | Lemmon Ave (Dallas) | Halal platters / gyro | High (Photon + Overpass `diet:halal=yes`) | Chain location; OSM `diet:halal=yes` |
| Al-Amir Lebanese Restaurant & Club | Belt Line Rd (Addison) | Lebanese | High (Photon) | Long-running DFW Lebanese venue |
| Afrah | E Main St (Richardson) | Middle Eastern | High (Photon) | Richardson halal favorite |
| Andalous Mediterranean Buffet | N Central Expwy (Richardson) | Mediterranean buffet | High (Photon) | Large halal-friendly Mediterranean buffet |
| Ali Baba Mediterranean | N Central Expwy (Richardson) | Mediterranean | High (Photon) | Richardson sibling of Andalous |

### 2.5 Austin (thin — flagged)
| Venue | Neighborhood/Address | Cuisine | Source confidence | Verified/noteworthy |
|---|---|---|---|---|
| Halal Bros | N FM 620 (Austin) | Middle Eastern / Mediterranean | High (Photon + Overpass) | Local chain; Overpass has 5 nodes |
| Halal Wings | Barbara Jordan Blvd (Austin) | Halal wings / comfort | High (Photon) | Popular halal wings spot |
| Caspian Grill | Research Blvd (Austin) | Persian / Mediterranean | High (Photon) | Halal grill; only 3 confirmed venues in Austin |

**Held back (halal-unconfirmed but on Photon, all Austin):** The Kebab Shop (E 5th St), Arpeggio Grill (Airport Blvd), Santorini Cafe (N Lamar). Resolve geographically but halal status unconfirmed → **not** in verified_seeds; approve before seeding if Austin needs more density.

---

## 3. OSM/Overpass corroboration (live spot-checks, this dispatch)

To ground the "source confidence" column, I ran Overpass queries (mirror `overpass.openstreetmap.fr`, live 2026-08-30) against the OSM corpus for a subset of venues. Results agree with the Photon verification:

- **The Halal Guys** — NYC/Dallas/Toronto all resolve; nodes carry `amenity=fast_food`, `cuisine=chicken;falafel;gyros`, and **`diet:halal=yes`** → a genuine halal tag already present in OSM (not just the name).
- **Mamoun's Falafel** (NYC) — 3 OSM nodes, one tagged `diet:halal=yes`.
- **The Kati Roll Company** (NYC) — 3 OSM nodes; the Jersey City branch carries `diet:halal=yes` (+ veg/vegan).
- **Halal Bros** (Austin) — 5 OSM restaurant nodes.
- **Paramount Fine Foods** (Toronto) — 7 OSM restaurant nodes; multiple GTA branches resolved.
- **Lazeez Shawarma** (Toronto) — 5 OSM nodes.
- **Sahadi's** (NYC), a candidate I independently considered, also resolves in OSM (2 nodes) — useful as an optional additional NYC seed if more density is wanted.
- **Karahi Boys / Adde's / Bijan's (NYC)** did NOT resolve in OSM under their real identity → not seedable via OSM alone; kept out.

Note on endpoint reliability (process note for Hamza/sc-155): the primary `overpass-api.de` endpoint rate-limited/returned empty under a tight loop this session; I fell back to the `overpass.openstreetmap.fr` mirror, which returned consistently. For any bulk seed-extract in sc-155, plan for endpoint fallback + throttling.

---

## 4. Venue/source & founder decisions the seed-ingest task needs

**Venue/source lineage**
- Data source: **OpenStreetMap via Photon/Overpass**, ODbL 1.0. Coordinates WGS84 (OSM node locations), good for seeding + search-bounding, **not** formally verified business coordinates.
- Project provenance to stamp on every row: `research-seed / photon-geocode`.
- Brand vs location: model **locations keyed by geolocation**, brand separate (The Halal Guys / Osmow's / Paramount appear in multiple cities; the JSON lists each branch independently). Multiple addresses per brand is expected.

**Founder decisions a seed-ingest card must have before/as it runs**
1. **ODbL attribution policy (⚠ decision):** OSM/Photon-derived listings are ODbL. The product needs an explicit attribution display + a stance on share-alike of OSM-derived POI fields. Already flagged in `docs/reviews/sc-138-external-services.md` §5 and external-services brief §4 — still open. **Seed-ingest must not drop this.**
2. **Postal/locale validation (decision):** Toronto uses Canadian postal format (A1A 1A1, `CA`/`ON`) — do **not** assume US ZIP (`NNNNN`) in any ingest validation (sc-155 will cover 4 locales).
3. **Austin density (decision):** Austin has only 3 confirmed venues. Product/engineering choice: seed with 3 now, or hold Austin until the halal-confirmed pass clears Kebab Shop/Arpeggio/Santorini. **Aisha recommends seeding with the 3 confirmed** (search still demonstrates the locale) and adding the held-back 3 only after halal confirmation — but this is a product call, not mine.
4. **Unverified cold-start (decision, in-line with product model):** all 30 start UNVERIFIED (`listing-first`). Confirmed for sc-155.

---

## 5. Open flags / what I did NOT do

- I did **not** scrape Google or Yelp; no Google-derived corpus (founder Photon ruling respected).
- Cutting-method (hand-cut/machine-cut) attributes: **not determinable from OSM** — all 30 start with no cutting-method value (human-sourced cold start).
- Cuisines in §2 are brief-assigned enrichment; confirm per venue at ingest/claim (OSM cuisine tags were empty for most).
- Halal status for the 3 held-back Austin venues is unconfirmed — kept out of the verified seed set on purpose.

---

## 6. Sources

- Companion data artifact: `seed_restaurants_geocoded.json` (repo root, this PR) — 30 verified-on-Photon seeds + exclusion lists.
- Finder data source: OpenStreetMap via Photon (Komoot) — https://photon.komoot.io (ODbL 1.0).
- OSM corpus spot-check: Overpass API — https://overpass.openstreetmap.fr/api/interpreter (live 2026-08-30).
- Prior constraints: `docs/research/external-services-brief-2026-08-29.md` §1 (geocoding) & §4 (seed data); `docs/reviews/sc-138-external-services.md` (Photon-to-default ruling context + ODbL/open flags).
- Founder rulings (Photon-not-Google for MVP; seed = OSM/Overpass; cold-start cutting-method): Arham via Adnan, 2026-08-30.

*Research only. See seed_restaurants_geocoded.json for the machine-readable ingest source; Adnan dispatches sc-155 seed-ingest + Photon integration from this brief.*