package com.tahirslist.application.image

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [ImagePort] used as the swappability proof and as a default bean
 * when no object store is configured (dev/test boot without MinIO).
 *
 * This is deliberately a test/dev escape hatch, NOT a production store: images
 * are lost on restart. A real deployment configures the S3 adapter instead. Its
 * presence also lets the boot context come up without MinIO so unrelated tests
 * (auth, listings) do not need object storage running.
 */
class InMemoryImagePort : ImagePort {

    private val store = ConcurrentHashMap<Key, StoredImage>()

    private data class Key(val listingId: UUID, val variant: ImageVariant)

    override fun save(listingId: UUID, variant: ImageVariant, contentType: String, bytes: ByteArray) {
        store[Key(listingId, variant)] = StoredImage(bytes, contentType)
    }

    override fun load(listingId: UUID, variant: ImageVariant): StoredImage? =
        store[Key(listingId, variant)]

    override fun delete(listingId: UUID, variant: ImageVariant) {
        store.remove(Key(listingId, variant))
    }
}