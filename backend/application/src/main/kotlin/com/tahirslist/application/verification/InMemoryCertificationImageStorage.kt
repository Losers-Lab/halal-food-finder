package com.tahirslist.application.verification

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [CertificationImageStorage] used only for dev/test boot when no real
 * object store is configured (mirrors [com.tahirslist.application.image.InMemoryImagePort]
 * for hero images). Never used in production — the S3 adapter wins whenever
 * `app.storage.s3.endpoint` is set.
 */
class InMemoryCertificationImageStorage : CertificationImageStorage {

    data class StoredCertificationImage(val contentType: String, val bytes: ByteArray)

    /** listingId -> certification images stored for it, in submission order. */
    val stored = ConcurrentHashMap<UUID, MutableList<StoredCertificationImage>>()

    override fun save(listingId: UUID, contentType: String, bytes: ByteArray) {
        stored.computeIfAbsent(listingId) { mutableListOf() }
            .add(StoredCertificationImage(contentType, bytes.copyOf()))
    }
}