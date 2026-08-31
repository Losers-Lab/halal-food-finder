package com.tahirslist.application.image

/**
 * Downloads a remote image (the hero_url from the seed photo manifest) as
 * bytes. Kept behind a seam so ingest tests stub the network and never hit real
 * URLs (docs/design/sc-157-image-variants.md — no network / licensed photos in
 * CI). Implemented by a JDK HttpClient adapter in bootstrap, mirroring the
 * Photon geocoder's JDK-HTTP approach.
 *
 * Contract:
 *  - 2xx response → the image bytes.
 *  - non-2xx, timeout, or I/O failure → throws [ImageFetchException]; the
 *    caller treats that as a per-row ingest failure, not a silent skip.
 */
fun interface ImageFetcher {
    fun fetch(url: String): ByteArray
}