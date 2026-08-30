package app.halal.bootstrap.image

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * [SeedPhotoManifestParser] extracts the backend-facing fields
 * (name / city / address_given / hero_url) from Aisha's seed photo manifest
 * (docs/research/seed-photos-2026-08-30.json). Only the fields ingest needs are
 * modeled; the richer research fields (license, vision ratings, address
 * confidence) are intentionally ignored. Null fields tolerate the artifact's
 * shape; a row without a hero_url is rejected loudly, not silently skipped.
 */
class SeedPhotoManifestParserTest : FunSpec() {

    private val parser = SeedPhotoManifestParser(ObjectMapper())

    init {
        test("parses seed rows into SeedHeroPhoto with the backend-facing fields") {
            val photos = parser.parse(
                """
                {
                  "purpose": "rich research fields are ignored",
                  "count": 2,
                  "seeds": [
                    {
                      "name": "Aroma Fine Indian Cuisine",
                      "city": "Toronto",
                      "address_given": "287 King St W",
                      "address_found": "287 King St W, Toronto ON M5V 1J5",
                      "license": "© uploader (Google listing)",
                      "hero_url": "https://lh3.googleusercontent.com/w1200-k-no"
                    },
                    {
                      "name": "Bamiyan Kabob",
                      "city": null,
                      "address_given": null,
                      "hero_url": "https://commons.wikimedia.org/x.png"
                    }
                  ]
                }
                """.trimIndent(),
            )

            photos.size shouldBe 2

            val first = photos[0]
            first.name shouldBe "Aroma Fine Indian Cuisine"
            first.city shouldBe "Toronto"
            first.addressGiven shouldBe "287 King St W"
            first.heroUrl shouldBe "https://lh3.googleusercontent.com/w1200-k-no"

            val second = photos[1]
            second.name shouldBe "Bamiyan Kabob"
            second.city shouldBe null
            second.addressGiven shouldBe null
            second.heroUrl shouldBe "https://commons.wikimedia.org/x.png"
        }

        test("a row with a blank hero_url is rejected loudly") {
            shouldThrow<IllegalStateException> {
                parser.parse(
                    """
                    { "seeds": [ { "name": "Missing Photo", "city": "Austin", "hero_url": "   " } ] }
                    """.trimIndent(),
                )
            }
        }

        test("a manifest without a 'seeds' array is rejected") {
            shouldThrow<IllegalStateException> {
                parser.parse("""{ "hello": "world" }""")
            }
        }

        test("an empty seeds array parses to no rows") {
            parser.parse("""{ "seeds": [] }""").size shouldBe 0
        }
    }
}