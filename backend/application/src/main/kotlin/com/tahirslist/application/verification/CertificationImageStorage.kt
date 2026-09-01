package com.tahirslist.application.verification

import java.util.UUID

/**
 * Object-storage seam for certification images (hexagonal "out" port), mirroring
 * the [com.tahirslist.application.image.ImagePort] shape for hero images but
 * for the verification vertical.
 *
 * Provider-agnostic by design: implemented by the S3/MinIO adapter now (same
 * object store as hero images) and swappable; keys/bucket/prefix stay the
 * adapter's concern and never leak into the application layer.
 *
 * [save] stores the cert image for a listing. sc-46 only writes (the image is
 * submitted to the AI seam as bytes in-memory, then retained as evidence);
 * reading it back for the Verification Committee is sc-73.
 */
interface CertificationImageStorage {
    fun save(listingId: UUID, contentType: String, bytes: ByteArray)
}