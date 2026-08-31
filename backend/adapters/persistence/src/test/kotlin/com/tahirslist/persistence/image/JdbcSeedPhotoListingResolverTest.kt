package com.tahirslist.persistence.image

import com.tahirslist.application.image.SeedHeroPhoto
import com.tahirslist.application.image.SeedPhotoResolution
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.flywaydb.core.Flyway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

/**
 * [JdbcSeedPhotoListingResolver] against a real PostGIS database with the full
 * seed migration applied (docs/design/sc-157-image-variants.md §"Manifest →
 * listing resolution"). Proves:
 *
 *  - an exact seed name resolves to that seeded listing;
 *  - the name match is normalized (case + whitespace) on both sides;
 *  - a multi-location name ("The Halal Guys", 3 seed rows) is AMBIGUOUS by name
 *    and stays Unresolved unless the address discriminates exactly one row;
 *  - the address fallback resolves a row whose manifest name differs from the
 *    seeded name, and an ambiguous address is itself Unresolved.
 *
 * Resolution is deliberate, never a guess: attaching a hero to the wrong
 * restaurant is worse than skipping (and logging) the row.
 */
class JdbcSeedPhotoListingResolverTest : FunSpec() {

    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    private lateinit var jdbc: JdbcTemplate
    private lateinit var resolver: JdbcSeedPhotoListingResolver

    private fun seededId(name: String): UUID =
        jdbc.queryForObject(
            "SELECT id FROM restaurant_listings WHERE name = ? LIMIT 1",
            UUID::class.java,
            name,
        ) ?: error("expected a seed row named '$name'")

    /** Resolves, asserts the result type, and returns the id (or fails the test). */
    private fun resolvedId(photo: SeedHeroPhoto): UUID {
        val resolution = resolver.resolve(photo)
        resolution.shouldBeInstanceOf<SeedPhotoResolution.Resolved>()
        return (resolution as SeedPhotoResolution.Resolved).listingId
    }

    private fun assertUnresolved(photo: SeedHeroPhoto) {
        resolver.resolve(photo).shouldBeInstanceOf<SeedPhotoResolution.Unresolved>()
    }

    init {
        beforeSpec {
            postgres.start()
            Flyway.configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .load()
                .migrate()

            val dataSource = DriverManagerDataSource().apply {
                setDriverClassName("org.postgresql.Driver")
                setUrl(postgres.jdbcUrl)
                setUsername(postgres.username)
                setPassword(postgres.password)
            }
            jdbc = JdbcTemplate(dataSource)
            resolver = JdbcSeedPhotoListingResolver(jdbc)
        }
        afterSpec { postgres.stop() }

        test("resolves a seeded row by its exact name") {
            val photo = SeedHeroPhoto(name = "Aroma Fine Indian Cuisine", city = "Toronto", addressGiven = null, heroUrl = "http://img/a")

            resolvedId(photo) shouldBe seededId("Aroma Fine Indian Cuisine")
        }

        test("name match is normalized (case + whitespace) on both sides") {
            val photo = SeedHeroPhoto(name = "   aroma fine indian cuisine  ", city = "Toronto", addressGiven = null, heroUrl = "http://img/a2")

            resolvedId(photo) shouldBe seededId("Aroma Fine Indian Cuisine")
        }

        test("a multi-location name with a non-discriminative address yields Unresolved") {
            // 'The Halal Guys' appears 3x (Toronto, NYC, Dallas); the name alone
            // cannot be unique, and 'Somewhere Else' matches no seed address.
            val photo = SeedHeroPhoto(name = "The Halal Guys", city = null, addressGiven = "Somewhere Else", heroUrl = "http://img/h")

            assertUnresolved(photo)
        }

        test("a multi-location name with a unique address resolves to exactly that location") {
            val photo = SeedHeroPhoto(name = "The Halal Guys", city = null, addressGiven = "563 Yonge St", heroUrl = "http://img/h2")

            val id = resolvedId(photo)

            // '563 Yonge St' is the Toronto location; the other two locations carry
            // different addresses, so this is unambiguous.
            val address = jdbc.queryForObject(
                "SELECT address FROM restaurant_listings WHERE id = ?",
                String::class.java,
                id,
            )
            address shouldBe "563 Yonge St"
        }

        test("address fallback resolves a row whose manifest name differs from the seeded name") {
            // e.g. a manifest "Iqbal Foods Birchmount"-style mismatch: the name is
            // unknown, but the address uniquely identifies the seed row.
            val photo = SeedHeroPhoto(name = "Unknown Display Name", city = null, addressGiven = "The Queensway", heroUrl = "http://img/p")

            resolvedId(photo) shouldBe seededId("Paramount Fine Foods")
        }

        test("an ambiguous address ('Overlea Blvd': two seed rows) yields Unresolved") {
            val photo = SeedHeroPhoto(name = "Unknown", city = null, addressGiven = "Overlea Blvd", heroUrl = "http://img/o")

            assertUnresolved(photo)
        }

        test("an unknown name and address yields Unresolved (never a guessed listing)") {
            val photo = SeedHeroPhoto(name = "Ghost Restaurant", city = null, addressGiven = "Nowhere Ave", heroUrl = "http://img/g")

            assertUnresolved(photo)
        }

        test("the resolved id names an existing seeded listing (sanity)") {
            val photo = SeedHeroPhoto(name = "Bamiyan Kabob", city = null, addressGiven = null, heroUrl = "http://img/b")

            val id = resolvedId(photo)

            jdbc.queryForObject(
                "SELECT count(*) FROM restaurant_listings WHERE id = ? AND provenance = 'research-seed / photon-geocode'",
                Int::class.java,
                id,
            ) shouldBe 1
        }
    }
}