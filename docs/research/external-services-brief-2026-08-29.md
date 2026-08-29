# External Services & Data-Source Requirements Brief — M1

**Author:** Aisha (Research & Integrations) · **Date:** 2026-08-29 · **Consumer:** Hamza (integration architecture)
**Standing founder directive:** MVP infrastructure must be ~$0; build solid, premium-swappable seams so scaling up later is config, not re-architecture.

Legend: 🔒 hard constraint · 🎯 preference · ⚠ founder decision.

## 0. Requirements from M1 epics

| Epic | Implied external dependency |
|---|---|
| Core Search & Discovery | Geocoding/autocomplete for "Near <place>"; PostGIS does radius math |
| Trust & Verification | Hosted multimodal vision API behind `VerificationProvider`; image storage; transactional email |
| Accounts & User Features | Transactional email (signup/verify/reset) |
| Owner Tools | Cert upload storage; verification/claim emails |
| Browser Extensions | Chrome Web Store + AMO distribution; extension↔backend API keys |
| All | Hosting (Next.js + Spring Boot + Postgres/PostGIS); seed data |

## 1. Maps / geocoding

1. **Google Places API (New), server-side only** ✅ RATIFIED (U-09): Autocomplete ~10k free/mo, Geocoding 10k free/mo ($2.83–5/1k after; per-SKU caps since 2025-03). Card on file required.
2. OSM stack (Photon/Nominatim) — $0 but 🔒 1 rps on Nominatim, no SLA, ODbL attribution.
3. **Hybrid recommended:** Google autocomplete+geocoding held server-side; PostGIS `ST_DWithin` for radius; fallback adapter behind `GeocodingPort`.

🔒 Browser never sees the Google key (server proxy only).
⚠ Set billing hard cap + alert at project creation.
🎯 Reverse-geocode "current location" label: free-cap Google vs "Near you" text (open).

## 2. Hosting

1. **Hetzner CX-line VPS (~$7/mo, Docker Compose everything)** ✅ RATIFIED (U-08). Use cost-optimized CX/CAX line, not CPX.
2. Neon free-tier Postgres (PostGIS supported) — fine for dev/preview; 🔒 0.5 GB + scale-to-zero unsuitable for always-on prod.
3. Rejected: Fly/Railway (vendor adds nothing), cloud managed (~$60–300/mo).

🔒 **No managed backups on self-hosted Postgres — nightly `pg_dump` to R2/MinIO + restore rehearsal is mandatory engineering.**
🔒 Single box = SPOF; honest uptime statement for M1. Next.js on same box behind Caddy/Traefik (no Vercel dependency).
⚠ Domain purchase (~$10–12/yr) needs founder sign-off.

## 3. Verification provider (hosted multimodal AI)

1. **Gemini 2.5 Flash paid tier** ✅ RATIFIED (U-11): $0.15/$0.60 per 1M in/out → ~$0.0002–0.0005/verification. 🔒 Never the free AI Studio tier (data used for training). Note 55-day prompt retention (disclose in consent).
2. Claude Haiku 4.5 — cleanest data posture; documented fallback adapter (~$0.001/verification).
3. xAI/Grok: do-not-use (data posture).

🔒 Human-in-the-loop mandatory (provider ToS + product). 🔒 Upload hygiene: downscale ~1024px, strip EXIF, cert-only, consent recorded.
⚠ Models judge plausibility, not authenticity — VC spot-check sampling rate needs founder policy (recommend 100% until stats exist).
⚠ Hamza AC: provider port must be proven swappable by test.

## 4. Seed data

1. **OSM via Overpass — $0, recommended seed source.** 🔒 ODbL attribution in UI; share-alike stance on derived listings needs explicit policy (⚠ founder).
2. Google Places — 🔒 **ToS prohibits storing Place data except Place IDs. Never a seed corpus; live enrichment only, cache ≤30 days.**
3. Yelp Fusion (5k calls/day free) — 🔒 same non-retention posture; enrichment only.

🎯 Halal/cutting-method attributes are sparse in OSM — hand-cut/machine-cut starts human-sourced (product cold-start, not vendor). 🔒 No scraping Google/Yelp, ever.

## 5. Other integrations

| Integration | Recommendation | Cost | Flag |
|---|---|---|---|
| Transactional email | Resend free tier (3k/mo) + domain SPF/DKIM | $0 | ⚠ provider + sending domain |
| Extension stores | Chrome Web Store $5 one-time; AMO free; review latency in schedule | $5 | ⚠ confirm both in M1 |
| Image storage | MinIO now → R2 later, S3 SDK behind one port ✅ RATIFIED | $0 | 🔒 no AWS-only features |
| Backups/DR | nightly pg_dump → R2/MinIO + restore test | ~$0 | 🔒 mandatory |
| TLS/CDN | Caddy/Traefik + Let's Encrypt on-box | $0 | 🔒 |
| Uptime | UptimeRobot free | $0 | 🎯 |
| Cert-body directories | Manual VC reference, not automated ingestion | $0 | 🎯 |

## 6. Cost roll-up (recommended defaults)

Hetzner ~$7 + domain ~$1 + maps ~$0 + Gemini <$1 + email $0 + storage $0 (+ $5 one-time Chrome fee) ⇒ **≈ $8–10/mo M1 run-rate.**

## 7. Founder decisions outstanding

1. ⚠ Google billing guardrails (cap + alert) + server-side-only key handling.
2. ⚠ ODbL attribution display + stance on OSM-derived listings.
3. ⚠ Backup/restore policy (frequency, off-box target, rehearsal owner).
4. ⚠ VC human-review sampling rate + PII-redaction/consent copy line.
5. ⚠ Domain purchase + email provider/sending domain (Resend default).
6. 🎯 Reverse-geocode label vs "Near you".
7. ⚠ Confirm both Chrome + Firefox extensions in M1.
