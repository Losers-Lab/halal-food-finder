package com.tahirslist.persistence.favorite

import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.CuttingMethod
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import com.tahirslist.persistence.account.JdbcAccountRepository
import com.tahirslist.persistence.listing.JdbcRestaurantListingRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.flywaydb.core.Flyway
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

/**
 * Persistence adapter test: proves V12 adds the favorites table and that
 * [JdbcFavoritesRepository] round-trips the user↔listing relation against a
 * real PostGIS container, enforcing the idempotent POST/DELETE contract and the
 * FK integrity at the DB layer.
 */
class JdbcFavoritesRepositoryTest : FunSpec() {

    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    private lateinit var jdbc: JdbcTemplate
    private lateinit var favorites: JdbcFavoritesRepository
    private lateinit var accounts: JdbcAccountRepository
    private lateinit var listings: JdbcRestaurantListingRepository

    private fun newOwner(): UUID =
        accounts.save(Account.new(email = Email("owner-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h")).id

    private fun aListing(ownerId: UUID): UUID =
        listings.save(RestaurantListing.new(
            name = "Halal Grill ${UUID.randomUUID()}",
            address = "123 Main St",
            location = LatLng(40.7128, -74.0060),
            cuisine = Cuisine("mediterranean"),
            cuttingMethod = CuttingMethod.HAND_CUT,
            ownerId = ownerId,
        )).id

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
            favorites = JdbcFavoritesRepository(jdbc)
            accounts = JdbcAccountRepository(jdbc)
            listings = JdbcRestaurantListingRepository(
                jdbc,
                TransactionTemplate(DataSourceTransactionManager(dataSource)),
            )
        }
        afterSpec { postgres.stop() }

        test("V12 migration creates the favorites table") {
            val count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'favorites'",
                Int::class.java,
            )
            count shouldBe 1
        }

        test("add is idempotent: favouriting the same listing twice persists one row") {
            val user = newOwner()
            val listing = aListing(user)

            favorites.add(user, listing)
            favorites.add(user, listing) // duplicate — must be a no-op

            val rows = countRows(user)
            rows shouldBe 1
        }

        test("remove is idempotent: unfavouriting twice leaves zero rows and does not throw") {
            val user = newOwner()
            val listing = aListing(user)
            favorites.add(user, listing)

            favorites.remove(user, listing)
            favorites.remove(user, listing) // already gone — no-op

            countRows(user) shouldBe 0
        }

        test("removing a listing that was never favourited is a no-op") {
            val user = newOwner()
            val listing = aListing(user)

            favorites.remove(user, listing)
            favorites.remove(user, listing)

            countRows(user) shouldBe 0
        }

        test("findFavoriteListings returns the favourited listing rows for that user only") {
            val user = newOwner()
            val otherUser = newOwner()
            val listingA = aListing(user)
            val listingB = aListing(user)
            val otherListing = aListing(user)

            favorites.add(user, listingA)
            favorites.add(user, listingB)
            // The second user favourites only the third listing.
            favorites.add(otherUser, otherListing)

            val mine = favorites.findFavoriteListings(user)

            val ids = mine.map { it.id }
            ids.toSet() shouldNotBe emptySet<UUID>()
            ids.toSet() shouldBe setOf(listingA, listingB)
            mine.all { it.verificationStatus == VerificationStatus.UNVERIFIED } shouldBe true
            mine.all { it.name.startsWith("Halal Grill") } shouldBe true
            // The other user's favourite is not leaked.
            favorites.findFavoriteListings(otherUser).map { it.id }.toSet() shouldBe setOf(otherListing)
        }

        test("favouriting a non-existent listing is rejected by the FK") {
            val user = newOwner()
            val phantom = UUID.randomUUID()

            shouldThrow<DataIntegrityViolationException> {
                favorites.add(user, phantom)
            }
        }
    }

    private fun countRows(userId: UUID): Int =
        jdbc.queryForObject(
            "SELECT count(*) FROM favorites WHERE user_id = ?",
            Int::class.java,
            userId,
        )
}