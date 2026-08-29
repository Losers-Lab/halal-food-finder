package app.halal.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
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
 * End-to-end Log In + Refresh (sc-40) test against the full application graph:
 * web controller -> AuthenticateAccount / RefreshSession use cases ->
 * Argon2id hasher + JwtTokenIssuer + JdbcAccountRepository + JdbcRefreshTokenStore,
 * with the real Flyway V1+V2 migrations applied on boot.
 */
class LoginEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var dataSource: DataSource

    // The default TestRestTemplate client refuses to read a 401 ("cannot retry due
    // to server authentication, in streaming mode"), so auth calls use a plain
    // JDK HttpURLConnection client that returns 4xx responses as ordinary bodies.
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
        test("valid login returns access + refresh tokens and the access JWT carries the RBAC role") {
            val tokens = signupAndLogin("gina@example.com")

            tokens["accessToken"].toString().isNotBlank() shouldBe true
            tokens["refreshToken"].toString().isNotBlank() shouldBe true
            tokens["tokenType"] shouldBe "Bearer"
            tokens["expiresIn"] shouldBe 900
            tokens["accountId"].toString().isNotBlank() shouldBe true
            tokens["role"] shouldBe "USER"

            // The access token is an RS256 JWT whose payload embeds sub + role.
            val payload = decodeJwtPayload(tokens["accessToken"].toString())
            payload shouldContain "\"role\":\"USER\""
            payload shouldContain "\"sub\""

            // The refresh token is persisted only as a SHA-256 hash of the token.
            val storedHash = JdbcTemplate(dataSource).queryForObject(
                "SELECT token_hash FROM refresh_tokens WHERE token_hash = ?",
                String::class.java,
                sha256(tokens["refreshToken"].toString()),
            )
            storedHash shouldNotBe null
            storedHash shouldMatch "[0-9a-f]{64}"
            storedHash shouldNotBe tokens["refreshToken"].toString()
        }

        test("wrong password is rejected with a generic 401 invalid_credentials") {
            signup("helen@example.com", "s3cr3t-password")

            val resp = client.postForEntity(url("/v1/auth/login"), loginBody("helen@example.com", "wrong-password"), Map::class.java)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "invalid_credentials"
            resp.body!!["message"] shouldBe "Invalid email or password."
        }

        test("unknown email is rejected with the SAME generic 401 (no user enumeration)") {
            val resp = client.postForEntity(url("/v1/auth/login"), loginBody("nobody@example.com", "whatever"), Map::class.java)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "invalid_credentials"
            resp.body!!["message"] shouldBe "Invalid email or password."
        }

        test("missing login fields return 400") {
            val resp = client.postForEntity(url("/v1/auth/login"), loginBody("", ""), Map::class.java)
            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
        }

        test("refresh rotates: old token revoked, new token works for the next refresh") {
            val tokens = signupAndLogin("iris@example.com")
            val oldRefresh = tokens["refreshToken"].toString()

            val first = exchange(url("/v1/auth/refresh"), oldRefresh)
            first.statusCode shouldBe HttpStatus.OK
            val rotated = first.body!!
            rotated["accessToken"].toString().isNotBlank() shouldBe true
            val newRefresh = rotated["refreshToken"].toString()
            newRefresh.isNotBlank() shouldBe true
            newRefresh shouldNotBe oldRefresh
            rotated["role"] shouldBe "USER"

            // Old token was single-use: reusing it must be rejected.
            val replay = exchange(url("/v1/auth/refresh"), oldRefresh)
            replay.statusCode shouldBe HttpStatus.UNAUTHORIZED

            // The new token is still valid and rotates again.
            val second = exchange(url("/v1/auth/refresh"), newRefresh)
            second.statusCode shouldBe HttpStatus.OK
        }

        test("refresh with an unknown/garbage refresh token is rejected with 401") {
            val resp = exchange(url("/v1/auth/refresh"), "not-a-real-refresh-token")
            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "invalid_credentials"
        }

        test("refresh with a missing refresh token returns 400") {
            val resp = exchange(url("/v1/auth/refresh"), "")
            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }

    private fun exchange(path: String, refreshToken: String): org.springframework.http.ResponseEntity<Map<*, *>> =
        client.exchange(path, HttpMethod.POST, refreshBody(refreshToken), Map::class.java)

    private fun signupAndLogin(email: String): Map<*, *> {
        signup(email, "s3cr3t-password")
        val resp = client.postForEntity(url("/v1/auth/login"), loginBody(email, "s3cr3t-password"), Map::class.java)
        resp.statusCode shouldBe HttpStatus.OK
        return resp.body!!
    }

    private fun signup(email: String, password: String) {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        restTemplate.postForEntity(
            "/v1/auth/signup",
            HttpEntity(mapOf("email" to email, "password" to password), headers),
            Map::class.java,
        )
    }

    private fun loginBody(email: String, password: String): HttpEntity<Map<String, String>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(mapOf("email" to email, "password" to password), headers)
    }

    private fun refreshBody(refreshToken: String): HttpEntity<Map<String, String>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(mapOf("refreshToken" to refreshToken), headers)
    }

    private fun decodeJwtPayload(jwt: String): String {
        val payload = jwt.split(".")[1]
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        return String(java.util.Base64.getUrlDecoder().decode(padded))
    }

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}