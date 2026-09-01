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
 * [save] stores the cert image for a listing. sc-46 writes (the image is
 * submitted to the AI seam as bytes in-memory, then retained as evidence).
 * [loadLatest] reads back the most recently saved cert image for a listing —
 * the sc-73 read-surface follow-up so the CertificatePanel's
 * "View certificate" can surface the archived image. Re-claims keep history
 * (each save is a new object), so "latest" means most-recent submission.
 */
interface CertificationImageStorage {
    fun save(listingId: UUID, contentType: String, bytes: ByteArray)

    /** The most recently saved certification image for [listingId], or null if none. */
    fun loadLatest(listingId: UUID): StoredCertificationImage?

    /** The certification image as read back from storage. */
    class StoredCertificationImage(
        val contentType: String,
        val bytes: ByteArray,
    )
}