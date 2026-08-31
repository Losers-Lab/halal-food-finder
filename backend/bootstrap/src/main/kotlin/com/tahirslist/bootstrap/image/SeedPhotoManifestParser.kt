package com.tahirslist.bootstrap.image

import com.tahirslist.application.image.SeedHeroPhoto
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component

/**
 * Parses Aisha's seed hero-photo manifest
 * (docs/research/seed-photos-2026-08-30.json) into [SeedHeroPhoto] rows for
 * ingest. Jackson tree model (like the Photon geocoder) — tolerant of the
 * research artifact's shape, extracting only the fields backend ingest needs.
 */
@Component
class SeedPhotoManifestParser(private val objectMapper: ObjectMapper) {

    /** @param json the raw manifest JSON text (not a path). */
    fun parse(json: String): List<SeedHeroPhoto> {
        val root: JsonNode = objectMapper.readTree(json)
        val seeds = root.path("seeds")
        check(seeds.isArray) { "manifest must contain a 'seeds' array" }
        return seeds.map { seed ->
            SeedHeroPhoto(
                name = seed.path("name").asText(),
                city = seed.path("city").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
                addressGiven = seed.path("address_given").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
                heroUrl = seed.path("hero_url").asText(),
            ).also {
                check(it.heroUrl.isNotBlank()) { "manifest row '${it.name}' has no hero_url" }
            }
        }
    }
}