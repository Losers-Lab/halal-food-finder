package app.halal.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldBeOneOf
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import javax.sql.DataSource

/**
 * End-to-end Create Account (sc-39) test against the full application graph:
 * web controller -> CreateAccount use case -> Argon2id hasher -> JdbcAccountRepository,
 * with the real Flyway users migration applied on boot.
 */
class SignupEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var dataSource: DataSource

    init {
        test("POST /v1/auth/signup persists an account with a hashed password and USER role") {
            val resp = restTemplate.postForEntity(
                "/v1/auth/signup",
                signupBody("alice@example.com", "s3cr3t-password"),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.CREATED
            val body = resp.body!!
            body["email"] shouldBe "alice@example.com"
            body["role"] shouldBe "USER"
            body["id"]!!.toString().isNotBlank() shouldBe true

            // Persisted: Argon2id hash present, plaintext never stored, default role.
            val row = JdbcTemplate(dataSource).queryForList(
                "SELECT password_hash, role FROM users WHERE email = ?",
                "alice@example.com",
            ).single()
            row["password_hash"]!!.toString() shouldContain "$argon2id$"
            row["password_hash"]!!.toString() shouldNotContain "s3cr3t-password"
            row["role"] shouldBe "USER"
        }

        test("POST /v1/auth/signup rejects a duplicate email with 409 and persists only one row") {
            restTemplate.postForEntity("/v1/auth/signup", signupBody("bob@example.com", "s3cr3t-password"), Map::class.java)

            val resp = restTemplate.postForEntity(
                "/v1/auth/signup",
                signupBody("bob@example.com", "another-password"),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.CONFLICT
            JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM users WHERE email = ?",
                Int::class.java,
                "bob@example.com",
            ) shouldBe 1
        }

        test("POST /v1/auth/signup rejects a weak password with 422 and persists nothing") {
            val resp = restTemplate.postForEntity(
                "/v1/auth/signup",
                signupBody("carol@example.com", "tiny"),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.UNPROCESSABLE_ENTITY
            JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM users WHERE email = ?",
                Int::class.java,
                "carol@example.com",
            ) shouldBe 0
        }

        test("POST /v1/auth/signup accepts a password of exactly the 8-char minimum (sc-135 Gap 5 boundary)") {
            val resp = restTemplate.postForEntity(
                "/v1/auth/signup",
                signupBody("debra@example.com", "pass1234"),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.CREATED
        }

        test("POST /v1/auth/signup rejects a password of exactly 7 chars with 422 and persists nothing (sc-135 Gap 5 boundary)") {
            val resp = restTemplate.postForEntity(
                "/v1/auth/signup",
                signupBody("erin@example.com", "pass123"),
                Map::class.java,
            )

            resp.statusCode shouldBe HttpStatus.UNPROCESSABLE_ENTITY
            JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM users WHERE email = ?",
                Int::class.java,
                "erin@example.com",
            ) shouldBe 0
        }

        test("concurrent duplicate signups never 500 and persist exactly one row (sc-135 Gap 6 / Defect 4a: unique-constraint race)") {
            val email = "race-http@example.com"
            val threads = 6
            val start = java.util.concurrent.CountDownLatch(1)
            val done = java.util.concurrent.CountDownLatch(threads)
            val statuses = java.util.concurrent.ConcurrentLinkedQueue<org.springframework.http.HttpStatusCode>()

            repeat(threads) {
                Thread {
                    start.await()
                    try {
                        val resp = restTemplate.postForEntity(
                            "/v1/auth/signup",
                            signupBody(email, "s3cr3t-password"),
                            Map::class.java,
                        )
                        statuses.add(resp.statusCode)
                    } finally {
                        done.countDown()
                    }
                }.start()
            }
            start.countDown()
            done.await(30, java.util.concurrent.TimeUnit.SECONDS)

            // The unique DB constraint is the backstop: no 500, exactly one 201, rest 409.
            statuses shouldHaveSize threads
            statuses.forEach { it shouldBeOneOf listOf(HttpStatus.CREATED, HttpStatus.CONFLICT) }
            statuses.count { it == HttpStatus.CREATED } shouldBe 1
            JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM users WHERE email = ?",
                Int::class.java,
                email,
            ) shouldBe 1
        }
    }

    private fun signupBody(email: String, password: String): HttpEntity<Map<String, String>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(mapOf("email" to email, "password" to password), headers)
    }
}

private const val argon2id = "argon2id"