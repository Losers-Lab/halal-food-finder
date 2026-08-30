# SC-157 — Restaurant hero images: MinIO storage + width variants (backend)

Status: PINNED contract (backend owner, 2026-08-30)
Stories: SC-157 (backend `t_89186e8c`; frontend `t_186e5378` gated on this)

## Goals

1. Store each restaurant's hero **original** in MinIO (S3 SDK, MinIO now → Cloudflare
   R2 later by config change; **no AWS-only features** — ratified in
   `docs/research/external-services-brief-2026-08-29.md`).
2. Serve **width variants**: a small **thumbnail** (~400px wide) for browse/search
   cards and the **full-res original** for the detail page. Cards MUST NOT fetch
   the full-res image ("no oversized fetch on cards").
3. Make storage provider-swappable behind a single seam (`ImagePort`), **proven by
   a contract test that runs against both an in-memory fake and real MinIO**
   (TDD, Kotest + Testcontainers MinIO).
4. Ingest Aisha's seed photo manifest
   (`docs/research/seed-photos-2026-08-30.json`) once it lands.

## Architecture

```
application layer (framework-free):            storage-s3 adapter:
  ImagePort     <-- port (save/load variant)     S3ImagePort  (AWS SDK v2, MinIO endpoint)
  ImageResizer  <-- pure JDK ImageIO resize      (resizes to ~400px thumbnail at ingest)
  ImageFetcher  <-- port (download hero_url)     HttpImageFetcher (JDK HttpClient)
  IngestHeroImage / IngestSeedHeroPhotos use cases
```

### Port: `ImagePort` (application)

The seam the whole variant story hangs on. Provider-agnostic; MinIO and R2 both
implement it. Storage **keys and bucket are entirely the adapter's concern** — the
application never sees them.

```kotlin
enum class ImageVariant(val widthPx: Int?) { THUMBNAIL(400), FULL(null) }
data class StoredImage(val bytes: ByteArray, val contentType: String)

interface ImagePort {
    fun save(listingId: UUID, variant: ImageVariant, contentType: String, bytes: ByteArray)
    fun load(listingId: UUID, variant: ImageVariant): StoredImage?
}
```

**Thumbnail sizing policy:** variants are **pre-generated at ingest time** (the
original is resized to ≤ min(original width, 400px) wide, aspect preserved) and
stored as two objects. The serving endpoint just reads the exact variant object —
there is no on-the-fly resize on the read path, so cards never trigger expensive
processing and never receive oversized bytes. This is the boring, reliable choice.

### Contract (swap) test — `ImagePortContractSpec`

An abstract Kotest `FunSpec` asserting the port contract: save→load round-trips
bytes + contentType; missing variant → null; variants are independent (writes to
one never leak into the other); concurrent/overwrite is last-write-wins.

It is run against **both**:
- `InMemoryImagePort` (a trivial map fake, application test), and
- `S3ImagePort` (real MinIO via Testcontainers `MinIOContainer`).

The same spec passing against both is what "adapter swap test green" means.

### Ingest use cases (application)

- `ImageResizer.resizeToThumb(original, contentType): StoredImage` — JDK ImageIO;
  throws on unreadable/unsupported input; never upscales.
- `IngestHeroImage(listingId, contentType, original)` — save FULL, resize, save THUMBNAIL.
- `IngestSeedHeroPhotos(port, fetcher, resizer, listingResolver)` — for each manifest
  row: resolve listing → fetch hero_url (via `ImageFetcher`, stubbed in tests) →
  `IngestHeroImage`. Per-row errors are isolated (one bad row doesn't abort the run).

### Manifest → listing resolution (honest, no guessing)

The manifest's `name` does not always equal the seeded DB `name` (e.g.
`Iqbal Foods Birchmount` vs seed `Iqbal Foods`; `Halal Corner and Halal Wings` vs
`Halal Wings`; `The Halal Guys` appears 3× at different locations). Live resolution
therefore uses **normalised (name, city) match first, address-normalised match as
fallback**, and **skips + logs** a row it cannot resolve unambiguously rather than
attaching a photo to the wrong listing. Resolution counts are reported honestly.

## URL / serving contract (the part the frontend must code against)

All image bytes are served through a **same-origin proxy** so the existing CSP
(`img-src 'self' data: blob:`) and `next.config.ts` need no `remotePatterns` change.
No auth required on image GETs (an `<img>` tag cannot send a Bearer header).

- **Serve a variant:** `GET /v1/listings/{listingId}/image?variant=thumbnail|full`
  → image bytes + correct `Content-Type` + `Cache-Control: public, max-age=86400`.
  Unknown/non-listed `variant` → `400`; no stored image → `404`.
- **Browse card response** (`GET /v1/listings`, search cards) carries
  `imageThumbnailUrl` **only** — full-res is never in a card payload.
- **Detail response** (`GET /v1/listings/{listingId}`) carries `imageUrl` (full)
  and MAY carry `imageThumbnailUrl`. The frontend `<Image>` uses
  `imageThumbnailUrl` on cards and `imageUrl` on the detail hero.

These browse/detail read endpoints are **minimal** for sc-157 (list per seed;
verification/rating/filter surfaces are later stories). They are **public reads**
(search/browse is the core public UX) — `GET /v1/listings/**` added to the
permit-list in `ResourceServerSecurityConfig`. This is a security-posture change
and is flagged for Omar's review in the PR.

## Acceptance criteria mapping

| Acceptance | How it is met |
|---|---|
| browse response points cards at thumbnail-only | browse DTO exposes only `imageThumbnailUrl`; object at that URL is the ≤400px variant |
| detail endpoint serves full | detail DTO + `?variant=full` return the original object |
| no oversized fetch on cards | cards only ever receive / request the thumbnail variant object |
| adapter swap test green | same `ImagePortContractSpec` passes against in-memory fake AND MinIO Testcontainer |

## Library choices (founder directive — library-first, recorded)

Every capability in this story was checked against the maintained-OSS landscape
before hand-rolling. The actual analysis, not a boilerplate line:

- **Image resize: JDK ImageIO + Graphics2D (chosen).** The thumbnail step is a
  single high-quality downscale to ≤400 px wide with aspect preserved. Candidates:
  - *Thumbnailator* (`net.coobird:thumbnailator`, last release 0.4.x, 2022) —
    well-maintained wrapper around ImageIO adding batch/watermark/format helpers;
    unneeded for one 15-line downscale, and pulling it in puts a third-party
    dependency back into the deliberately **framework-free application layer**.
  - *imgscalr* (`org.imgscalr:imgscalr-lib`) — effectively unmaintained (last
    release ~2014); we avoid an abandoned dependency for a JPEG-sized operation.
  - *TwelveMonkeys ImageIO plugins* (`com.twelvemonkeys.imageio:imageio-*`) —
    actively maintained, but they are format **decoders** (WebP/TIFF/BMP), not a
    resize library, and the seed sources are JPEG/PNG which stock ImageIO already
    decodes. If a future feed adds WebP/TIFF, adding a TwelveMonkeys plugin pair
    is a config-level change — noted as the escalation path, not done now.
  - Decision: `javax.imageio.ImageIO` + `java.awt.Graphics2D` with bilinear
    interpolation and render-quality hints stays in the app layer with zero
    dependencies. Output re-encodes to JPEG (q=0.85) or keeps PNG for alpha.
- **Object storage: AWS SDK v2 (`software.amazon.awssdk:s3`) — the maintained
  library.** MinIO now and Cloudflare R2 later both speak the S3 API, so the
  *vendor* SDK (MinIO's Java client) is redundant for the same wire protocol and
  would couple the adapter to one vendor. Only standard signed S3 operations are
  used (CreateBucket/PutObject/GetObject); path-style addressing is enabled
  explicitly (required by MinIO and R2; not AWS's default). No AWS-only feature
  is relied upon (ratified in `docs/research/external-services-brief-2026-08-29.md`).
  The URL-connection HTTP client (`url-connection-client`) stays transport-minimal.
- **Hero fetch: JDK `java.net.http.HttpClient` (chosen).** A single GET with a
  20 s timeout and NORMAL redirect handling. Candidates (OkHttp, Apache HC5) add
  a dependency for the same call; the repo already standardizes on JDK HttpClient
  in the Photon geocoder adapter, so this mirrors the existing pattern. Zero
  additional dependency.
- **Manifest parsing: Jackson `ObjectMapper` tree model (existing repo dep).**
  Tolerant of the research artifact's shape; only the fields backend ingest needs
  are modeled.

## Open / unresolved

- Production image source should prefer **owner-uploaded** photos after claim
  (manifest `image_policy`); Google listing photos are seed/dev only.
- Certification images & gallery thumbnails are future work, not this port.