# SC-138 Gate Review — External Services for the Add-Listing Vertical

- **Author:** Aisha (Research & Data Specialist) — 2026-08-30
- **Consumer:** Adnan (decides sc-138 integration-architecture dispatch) → Hamza (backend/DB implementation)
- **Story:** SC-138 add-listing vertical. This gate ENDS here: after reading this, Adnan decides architecture dispatch.
- **No integration code** in this task — recommendation only.
- **Founder standing directives honored:** MVP cost ≈ **$0**; **premium-swappable seams** (everything behind a port so a paid provider is config-cheap later); **no vendor lock-in**.

**Gate purpose:** decide the *external* services for add-listing — geocoding (address → coordinates for `PostGIS geography(Point)`), POI/listing data sources (pre-fill + dedupe an existing restaurant), and any other APIs the flow needs.

Legend: 🔒 hard constraint · 🎯 preference · ⚠ founder decision / open flag.

---

## 1. Executive recommendation (TL;DR)

| Need | Recommendation (MVP, ~$0) | Pure-$0 fallback | Premium swap target |
|---|---|---|---|
| **Address autocomplete UX** (search-as-you-type in the add form) | **Photon** (free, no key, OSM) behind `GeocoderPort` | — (self-host Photon if query needs grow) | **Google Places Autocomplete** (~10k free/mo, per-SKU since 2025-03) |
| **Geocoding** (address → lat/lng, stored in PostGIS) | **Photon search** (free) OR **Google Geocoding** (~10k free/mo) | **OSM Nominatim** (free, 1 req/s cap) | **Google Geocoding** |
| **Reverse-geocode** current location label (optional) | Photon reverse (free) | Nominatim reverse (free, careful with 1 rps) | Google Reverse Geocoding |
| **POI pre-fill / dedupe** (is this restaurant already listed?) | **Local PostGIS** (own listings) + **Photon/OSM** as reference | **Overpass API** for one-off reference extracts | **Google Place Details / Yelp Fusion** (already ratified U-10, ~$0) |

🟢 **Bottom line:** Lean on **Photon as the default geocoder + autocomplete** (free, no key, OSM data, service lives independently of Google), all calls held **server-side** behind a `GeocoderPort`, with **Google as the config-cheap premium replacement** and **Nominatim as the no-card fallback**. The *hard work (coordinates + dedupe) happens in our own PostGIS*, which we own — external providers only resolve a name to a point and optionally attest a place. That keeps MVP at ~$0 and vendor lock-in near zero.

---

## 2. Decision matrix (≥2 options per need, honest)

### 2.1 Geocoding + autocomplete — primary input

| Option | Cost | Rate limits | Data / license | Autocomplete? | SLA | Verdict |
|---|---|---|---|---|---|---|
| **Photon** (komoot public: `photon.komoot.io`) | **$0**, no key | Soft; multiple mirrors; no hard published cap — treat as best-effort | OSM + OpenAddresses, **ODbL** (attribution + share-alike intent) | ✅ **built for it** (fuzzy autocomplete) | ❌ none (community) | 🎯 **Recommended default** |
| **Google Places Autocomplete + Geocoding** | ~**$0** at MVP (≈10k free/mo per SKU since 2025-03; card on file; ~$2.83–5/1k past cap) | 10k/mo free per-SKU | Google, proprietary; **ToS: must not store Place data except Place IDs** | ✅ excellent | ✅ (license/SLA) | Premium swap; best UX |
| **OSM Nominatim** (public) | **$0**, no key | 🔒 **max 1 req/s** overall; bulk ≥1 day = 4 req/min | OSM, **ODbL** | ❌ **Forbidden client-side autocomplete** (explicit in policy); server-side discouraged | ❌ none | No-card fallback only |

**Findings (from primary sources, fetched live this session):**

- **Photon** responds `200` with geocoded GeoJSON, no key, free. There are several public mirrors (`photon.komoot.io`, plus EU/global mirror hosts), so single-mirror outage is mitigable, and it is fully **self-hostable** (Docker) — a clean later path if our query rate outgrows the public service. Data is OSM/OpenAddresses under **ODbL**.
- **Google Places (New)** — the ratified U-09 note already lands Google Autocomplete + Geocoding held server-side (~10k free/mo per SKU, per-SKU caps since 2025-03-01). I could not **live re-verify current per-SKU caps** this session (pricing pages are JS-shells; my web-search backend was down) — treat the ~10k/mo figure as *from the ratified ARCHITECTURE.md U-09 note*, not freshly confirmed. Marked as 🎯-for-verification below.
- **OSM Nominatim** public policy (fetched verbatim from `operations.osmfoundation.org/policies/nominatim/`): 🔒 **max 1 request per second** per app (sum of all users); **valid Referer/User-Agent required**; **ODbL, share-alike**; **auto-complete is NOT supported and must not be built client-side**; bulk/regular geocoding restricted (4 req/min); apps must be able to **switch service at OSMF's request** without a software update, and are advised to **proxy + cache**; reselling/primarily-geocoding apps must self-host. Public Nominatim is a donor-run, no-SLA service and can be withdrawn/blocked.

**Implication:** Nominatim is **not** a viable default for an autocomplete-addressing UX (explicitly disallowed), and even as a geocoder it's 1 rps and withdrawal-prone for a commercial app → keep it strictly as a no-card fallback, behind a port, with a proxy + cache. Photon gives autocomplete + geocoding for free without Nominatim's autocomplete ban and without Google's key/caps.

### 2.2 POI / listing data sources (pre-fill + dedupe)

The add-listing flow needs to (a) pre-fill known fields (name, address, phone) and (b) decide **"does this restaurant already exist in our listings?"** to avoid duplicates.

| Option | Cost | Nature | License / ToS | Verdict |
|---|---|---|---|---|
| **Our own PostGIS listings** | $0 | Primary dedupe (name+address+lat/lng match) | ours — no restriction | 🔒 **Primary dedupe authority** |
| **Photon / OSM** | $0 | Reference pre-fill (does a POI exist here, what's its name) | **ODbL** — attribution; share-alike stance on derived data ⚠ | 🎯 Reference source |
| **Overpass API** | $0 | One-off OSM POI extracts | ODbL | One-off reference only (slot-throttled, peak-slow) |
| **Google Place Details / Yelp Fusion** | ~$0 (U-10 ratified: ~1k free/mo Google, ~500/day Yelp) | External rating/count/review link enrichment | Google: **non-retention, Place IDs only**; Yelp: **non-retention, ToS-restricted** | Enrichment only (already ratified), **never for storage** |

**Honest gaps (falafel-grade):**
- **Halal/cutting-method + address precision** are **sparse in OSM**. Hand-cut vs machine-cut starts **human-sourced** (product cold-start, not a vendor feed). No scraping of Google/Yelp — **ever** (ToS/DMCA).
- **ODbL share-alike** on OSM-derived POI data is an **unsettled policy decision** (⚠ founder) — whether fields we pull from OSM/Photon and store must be released share-alike. Flagging, not deciding.
- **Dedupe is fuzzy and error-prone** (spellings, chains, address variants). Recommend a conservative local match + let owners claim (existing claim flow) resolve collisions. Do not pretend ML-grade dedupe at MVP.

### 2.3 Other external APIs the add-listing flow touches

- **Geocoding/autocomplete** — covered in 2.1.
- **Verification upload** (`VerificationProvider`) — already ratified (U-11, Gemini 2.5 Flash paid tier). Reuses the cert-upload/consent flow; not new spend.
- **Image storage** — MinIO S3 (ratified U-08/M1), already $0.
- **Reviews/ratings display** — Google Place Details + Yelp Fusion (ratified U-10), for enrichment/link only.
- **No new paid external dependency** beyond what's already ratified. Add-listing itself adds only **geocoding + POI reference**, both $0.

---

## 3. Ports / seam (what Hamza needs to know)

Recommendation is **implementation-agnostic** here (no code in this task), but the seams are the point of the gate:

1. **`GeocoderPort`** — `geocode(address) → (lat, lon, displayName, providerRef)`, reverse variant, and an **autocomplete/search** variant. Adapters: `PhotonAdapter` (default, $0), `GoogleGeocodingAdapter` (premium), `NominatimAdapter` (no-card fallback). **Swap = config, not code.** Proven swapable by test (Hamza AC, reuse M1 pattern).
2. **`POIReferencePort`** — optional pre-fill + external reference. Adapters: Photon/OSM ($0), Google Place Details / Yelp (enrichment). **Keep pre-fill optional and non-blocking** — the listing must be saveable without any external success.
3. **Dedupe lives locally in PostGIS** (name/address/geo match against `listing_search`), **not** against a vendor. Google/Yelp data is only ever **read-and-linked**, never persisted beyond menu/price as allowed — 🎯 confirm exact storage scope with Hamza.
4. **Server-side only** for all geo/place keys. 🔒 No client key exposure.

---

## 4. Cost roll-up (MVP, add-listing slice)

| Line item | Runtime cost |
|---|---|
| Photon public (default geocoder/autocomplete) | **$0** |
| Google Places (autocomplete+geocoding, alt/premium) | ~**$0** (<10k/mo each) |
| OSM Nominatim fallback | **$0** |
| PostGIS dedupe (ours) | **$0** |
| Verification (Gemini 2.5 Flash, ratified U-11) | <$1/mo |
| Reviews enrichment (U-10) | ~**$0** |
| Storage (MinIO, ratified) | **$0** |
| **Add-listing external-services total** | **≈ $0–1/mo** (bounded by slack when scaling past free caps) |

No new recurring vendor for the add-listing slice beyond already-ratified hosted AI.

---

## 5. Open flags for Adnan / founder

1. ⚠ **Google vs Photon as the starred default.** Photon is free/no-key/no-lock-in and covers autocomplete; Google has best UX + SLA but a card + per-SKU caps. My recommendation: **Photon default, Google as the config-cheap premium** (matches "$0 + swappable seams"). Adnan/backer confirms the headline.
2. ⚠ **ODbL share-alike stance** on OSM/Photon-derived pre-fill fields (policy, not architecture).
3. 🎯 **Verify current Google per-SKU free caps** before finalizing premium adapter budget — web research backend was down this session; figures here carry the ratified U-09 provenance, **not** a fresh 2026-08-30 check.
4. 🎯 Reverse-geocode "current location" label vs generic "Near you" (from M1) — still open.
5. ⚠ **Dedupe confidence threshold** (how aggressive to pre-fill / warn "this looks like an existing listing") — product call.

---

## 6. Sources

- OSM Nominatim Usage Policy — `operations.osmfoundation.org/policies/nominatim/` (fetched 2026-08-30, verbatim): 1 req/s, valid UA required, **autocomplete disallowed client-side**, ODbL, bulk limited, switchable-service requirement, proxy/cache advised.
- Photon (komoot) — `photon.komoot.io` live-geocoded a query, status 200, no key; self-hostable; OSM/OpenAddresses data (ODbL).
- Overpass API status — `overpass-api.de/api/status` live, slot-throttled/best-effort (no SLA).
- ARCHITECTURE.md — U-08 (hosting), **U-09 (Maps/geo: Google Autocomplete+Geocoding server-side, OSM fallback, ~10k/mo per-SKU)**, **U-10 (reviews: Google Place Details + Yelp Fusion)**, **U-11 (Gemini verification)** — all ratified 2026-08-29.
- Prior: `docs/research/external-services-brief-2026-08-29.md` (M1 broad brief this doc refines for add-listing).

**Uncertainty note (honesty):** Google pricing pages could not be scraped this session (JS-shells) and the configured web-search backend was down; the Google per-SKU free-cap figures are carried from the ratified U-09 note, not independently re-verified today. OSM/Photon facts were verified live.

*Recommendation only. Ends the SC-138 gate for external services; Adnan decides the integration-architecture dispatch, then Hamza implements behind the seams above.*