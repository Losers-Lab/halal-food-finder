package com.tahirslist.persistence.verification

import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.CuttingMethod
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.verification.HalalCertificationReview
import com.tahirslist.domain.verification.SuggestionVerdict
import com.tahirslist.domain.verification.VerificationSuggestion
import com.tahirslist.persistence.account.JdbcAccountRepository
import com.tahirslist.persistence.listing.JdbcRestaurantListingRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.util.UUID

/**
 * Persistence adapter test: proves V13 adds the halal_certification_reviews table
 * and that [JdbcHalalCertificationReviewRepository] round-trips a review (state,
 * AI suggestion, and human decision) against a real PostGIS container, with the
 * aggregate's CHECK constraints and FKs enforced at the DB layer.
 */
class JdbcHalalCertificationReviewRepositoryTest : FunSpec() {

    private val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(
        DockerImageName.parse("postgis/postgis:17-3.4").asCompatibleSubstituteFor("postgres"),
    )
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test")

    private lateinit var jdbc: JdbcTemplate
    private lateinit var reviews: JdbcHalalCertificationReviewRepository
    private lateinit var accounts: JdbcAccountRepository
    private lateinit var listings: JdbcRestaurantListingRepository

    private val now = Instant.parse("2026-09-01T12:00:00Z")

    private fun newAccount(): UUID =
        accounts.save(Account.new(email = Email("review-${UUID.randomUUID()}@example.com"), passwordHash = "argon2id\$h")).id

    private fun aListing(ownerId: UUID): UUID =
        listings.save(
            RestaurantListing.new(
                name = "Halal Grill ${UUID.randomUUID()}",
                address = "123 Main St",
                location = LatLng(40.7128, -74.0060),
                cuisine = Cuisine("mediterranean"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = ownerId,
            ),
        ).id

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
            reviews = JdbcHalalCertificationReviewRepository(jdbc)
            accounts = JdbcAccountRepository(jdbc)
            listings = JdbcRestaurantListingRepository(
                jdbc,
                TransactionTemplate(DataSourceTransactionManager(dataSource)),
            )
        }
        afterSpec { postgres.stop() }

        test("V13 migration creates the halal_certification_reviews table") {
            val count = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'halal_certification_reviews'",
                Int::class.java,
            )
            count shouldBe 1
        }

        test("save persists an AI_SUGGESTED review with its suggestion") {
            val owner = newAccount()
            val listing = aListing(owner)
            val suggested = HalalCertificationReview.create(listing, owner, now)
                .beginAiReview(now)
                .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.NEEDS_REVIEW, 0.4, "unclear"), now)

            reviews.save(suggested)

            val row = loadRow(suggested.id)
            row.state shouldBe "AI_SUGGESTED"
            row.listingId shouldBe listing.toString()
            row.submittedBy shouldBe owner.toString()
            row.suggestionVerdict shouldBe "NEEDS_REVIEW"
            row.suggestionConfidence shouldBe 0.4
            row.suggestionReasoning shouldBe "unclear"
        }

        test("save persists a human-approved review with its decision") {
            val owner = newAccount()
            val listing = aListing(owner)
            val vc = newAccount()
            val approved = HalalCertificationReview.create(listing, owner, now)
                .beginAiReview(now)
                .recordAiSuggestion(VerificationSuggestion(SuggestionVerdict.APPROVE, 0.95), now)
                .beginHumanReview(now)
                .approve(decidedBy = vc, reason = "cert matches listing", now = now)

            reviews.save(approved)

            val row = loadRow(approved.id)
            row.state shouldBe "APPROVED"
            row.decisionOutcome shouldBe "APPROVED"
            row.decisionBy shouldBe vc.toString()
            row.decisionReason shouldBe "cert matches listing"
            row.decisionAt shouldBe "2026-09-01T12:00:00Z"
        }

        test("save persists a bare SUBMITTED review with null suggestion and decision") {
            val owner = newAccount()
            val listing = aListing(owner)
            val submitted = HalalCertificationReview.create(listing, owner, now)

            reviews.save(submitted)

            val row = loadRow(submitted.id)
            row.state shouldBe "SUBMITTED"
            row.suggestionVerdict.shouldBeNull()
            row.suggestionConfidence.shouldBeNull()
        }

        test("saving a review for a non-existent listing is rejected by the FK") {
            val owner = newAccount()
            val phantom = UUID.randomUUID()

            shouldThrow<DataIntegrityViolationException> {
                reviews.save(HalalCertificationReview.create(phantom, owner, now))
            }
        }

        test("an out-of-machine state is rejected by the CHECK constraint") {
            val owner = newAccount()
            val listing = aListing(owner)

            shouldThrow<DataIntegrityViolationException> {
                jdbc.update(
                    """
                    INSERT INTO halal_certification_reviews (id, listing_id, submitted_by, state)
                    VALUES (?, ?, ?, 'NOT_A_STATE')
                    """.trimIndent(),
                    UUID.randomUUID(), listing, owner,
                )
            }
        }
    }

    private data class Row(
        val state: String,
        val listingId: String,
        val submittedBy: String,
        val suggestionVerdict: String?,
        val suggestionConfidence: Double?,
        val suggestionReasoning: String?,
        val decisionOutcome: String?,
        val decisionBy: String?,
        val decisionReason: String?,
        val decisionAt: String?,
    )

    private fun loadRow(id: UUID): Row =
        jdbc.query(
            """
            SELECT state, listing_id, submitted_by, suggestion_verdict, suggestion_confidence,
                   suggestion_reasoning, decision_outcome, decision_by, decision_reason, decision_at
            FROM halal_certification_reviews
            WHERE id = ?
            """.trimIndent(),
            { rs, _ ->
                Row(
                    state = rs.getString("state"),
                    listingId = rs.getString("listing_id"),
                    submittedBy = rs.getString("submitted_by"),
                    suggestionVerdict = rs.getString("suggestion_verdict"),
                    suggestionConfidence = rs.getDouble("suggestion_confidence").let { if (rs.wasNull()) null else it },
                    suggestionReasoning = rs.getString("suggestion_reasoning"),
                    decisionOutcome = rs.getString("decision_outcome"),
                    decisionBy = rs.getString("decision_by"),
                    decisionReason = rs.getString("decision_reason"),
                    decisionAt = rs.getTimestamp("decision_at")?.toInstant()?.toString(),
                )
            },
            id,
        ).first()
}