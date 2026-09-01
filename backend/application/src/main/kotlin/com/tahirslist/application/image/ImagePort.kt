package com.tahirslist.application.image

import java.util.UUID

/**
 * The object-storage seam for restaurant hero images (hexagonal "out" port).
 *
 * Provider-agnostic by design: implemented by the S3/MinIO adapter now and by
 * the Cloudflare R2 adapter later (docs/design/sc-157-image-variants.md), with
 * the same contract — storage keys, bucket, and endpoint are entirely the
 * adapter's concern and never leak into the application layer.
 *
 * **Swappability is proven by contract test:** [ImagePortContractSpec] runs
 * against BOTH an in-memory fake and the real MinIO Testcontainer; the same
 * assertions must pass on both, which is what "adapter swap test green" means.
 *
 * Contract:
 *  - [save] stores the given variant's bytes, overwriting any previous value
 *    for the same (listing, variant) (last-write-wins).
 *  - [load] returns the stored variant, or `null` if none was ever saved.
 *  - Save/load must round-trip bytes + contentType exactly.
 *  - Variants are independent: writing one thumbnail width (or FULL) never
 *    affects any other variant.
 */
interface ImagePort {
    fun save(listingId: UUID, variant: ImageVariant, contentType: String, bytes: ByteArray)

    fun load(listingId: UUID, variant: ImageVariant): StoredImage?
}
