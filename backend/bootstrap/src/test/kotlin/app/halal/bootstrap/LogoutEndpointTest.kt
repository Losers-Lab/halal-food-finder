package app.halal.bootstrap

import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import javax.sql.DataSource

/**
 * End-to-end Log Out (sc-132) test against the full application graph: web
 * controller -> LogoutSession use case -> JdbcRefreshTokenStore, with the real
 * Flyway V1+V2 migrations applied on boot. Proves that logging out revokes the
 * presented refresh token server-side (so a subsequent refresh with it is
 * rejected with 401) and that logout is idempotent — revoking an unknown or
 * already-revoked token still succeeds, never leaking token validity.
 */
class LogoutEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var dataSource: DataSource

    // Same 401-tolerant client pattern as LoginEndpointTest.
    @LocalServerPort
    var port: Int = 0

    private val client: RestTemplate by lazy {
        RestTemplate().apply {
            requestFactory = HttpComponentsClientHttpRequestFactory()
            setErrorHandler(object : ResponseErrorHandler {
                override fun hasError(response: ClientHttpResponse): Boolean = false
                override fun handleError(response: ClientHttpResponse) { /* return body as-is */ }
            })
        }
    }

    private fun url(path: String) = "http://localhost:$port$path"

    init {
        test("logout revokes the refresh token: a later refresh with it is rejected 401") {
            val tokens = signupAndLogin("kim@example.com")
            val refreshToken = tokens["refreshToken"].toString()
            val hashBefore = refreshHash(refreshToken)
            hashBefore shouldBe true

            val logoutResp = client.postForEntity(
                url("/v1/auth/logout"), refreshBody(refreshToken), Map::class.java,
            )
            logoutResp.statusCode shouldBe HttpStatus.NO_CONTENT

            // The row is gone from storage (token can no longer be found/resolved).
            refreshHash(refreshToken) shouldBe false

            // Refreshing with the revoked token must fail closed.
            val replay = exchange(url("/v1/auth/refresh"), refreshToken)
            replay.statusCode shouldBe HttpStatus.UNAUTHORIZED
            replay.body!!["code"] shouldBe "invalid_credentials"
        }

        test("logout is idempotent: unknown/never-issued tokens still return 204") {
            val resp = client.postForEntity(
                url("/v1/auth/logout"), refreshBody("never-issued-refresh-token"), Map::class.java,
            )
            resp.statusCode shouldBe HttpStatus.NO_CONTENT
        }

        test("logout is idempotent: calling it twice with the same token succeeds both times") {
            val tokens = signupAndLogin("leon@example.com")
            val refreshToken = tokens["refreshToken"].toString()

            val first = client.postForEntity(url("/v1/auth/logout"), refreshBody(refreshToken), Map::class.java)
            val second = client.postForEntity(url("/v1/auth/logout"), refreshBody(refreshToken), Map::class.java)

            first.statusCode shouldBe HttpStatus.NO_CONTENT
            second.statusCode shouldBe HttpStatus.NO_CONTENT
        }

        test("logout with a missing/blank refresh token returns 400") {
            val resp = client.postForEntity(
                url("/v1/auth/logout"), refreshBody(""), Map::class.java,
            )
            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }

    private fun exchange(path: String, refreshToken: String): org.springframework.http.ResponseEntity<Map<*, *>> =
        client.exchange(path, HttpMethod.POST, refreshBody(refreshToken), Map::class.java)

    private fun signupAndLogin(email: String): Map<*, *> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        restTemplate.postForEntity(
            "/v1/auth/signup",
            HttpEntity(mapOf("email" to email, "password" to "s3cr3t-password"), headers),
            Map::class.java,
        )
        val resp = client.postForEntity(
            url("/v1/auth/login"), loginBody(email, "s3cr3t-password"), Map::class.java,
        )
        resp.statusCode shouldBe HttpStatus.OK
        return resp.body!!
    }

    private fun loginBody(email: String, password: String): HttpEntity<Map<String, String>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(mapOf("email" to email, "password" to password), headers)
    }

    private fun refreshBody(refreshToken: String): HttpEntity<Map<String, String>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(mapOf("refreshToken" to refreshToken), headers)
    }

    /** True if a row for this refresh token's SHA-256 hash still exists in storage. */
    private fun refreshHash(token: String): Boolean {
        val rows = JdbcTemplate(dataSource).queryForList(
            "SELECT 1 FROM refresh_tokens WHERE token_hash = ?",
            sha256(token),
        )
        return !rows.isEmpty()
    }

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}