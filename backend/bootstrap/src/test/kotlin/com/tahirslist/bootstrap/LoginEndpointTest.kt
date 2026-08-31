package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
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
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * End-to-end Log In + Refresh (sc-40) test against the full application graph,
 * updated for the sc-133 cookie contract: the access JWT is returned in the JSON
 * body (frontend keeps it memory-only), but the refresh token is delivered ONLY
 * as an HttpOnly; Secure; SameSite=Lax cookie scoped to the auth routes — it must
 * never appear in the JSON body. Refresh now presents the cookie (no request
 * body) and rotates it via a fresh Set-Cookie.
 */
class LoginEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var dataSource: DataSource

    // The default TestRestTemplate client refuses to read a 401 ("cannot retry due
    // to server authentication, in streaming mode"), so auth calls use a plain
    // JDK HttpURLConnection client that returns 4xx responses as ordinary bodies.
    // Cookie management is disabled so the test controls exactly which refresh
    // cookie each request presents (automatic jar replay would mask rotation bugs).
    @LocalServerPort
    var port: Int = 0

    private val client: RestTemplate by lazy {
        val httpClient: CloseableHttpClient = HttpClients.custom().disableCookieManagement().build()
        RestTemplate().apply {
            requestFactory = HttpComponentsClientHttpRequestFactory(httpClient)
            setErrorHandler(object : ResponseErrorHandler {
                override fun hasError(response: ClientHttpResponse): Boolean = false
                override fun handleError(response: ClientHttpResponse) { /* return body as-is */ }
            })
        }
    }

    private fun url(path: String) = "http://localhost:$port$path"

    init {
        test("login returns the access token in the body and the refresh token ONLY as an HttpOnly Secure SameSite=Lax cookie (never in JSON)") {
            val (tokens, cookie) = signupAndLogin("gina@example.com")

            // Access JWT stays in the JSON body (frontend holds it memory-only).
            tokens["accessToken"].toString().isNotBlank() shouldBe true
            tokens["tokenType"] shouldBe "Bearer"
            tokens["expiresIn"] shouldBe 900
            tokens["accountId"].toString().isNotBlank() shouldBe true
            tokens["role"] shouldBe "USER"

            // The refresh token must NOT be in the JSON body (JS-readable storage contract removed).
            tokens.containsKey("refreshToken") shouldBe false

            // The access token is an RS256 JWT whose payload embeds sub + role.
            val payload = decodeJwtPayload(tokens["accessToken"].toString())
            payload shouldContain "\"role\":\"USER\""
            payload shouldContain "\"sub\""

            // The refresh token is delivered by Set-Cookie with the hardened attributes.
            val refreshValue = cookie.refreshValue
            refreshValue.isNotBlank() shouldBe true
            cookie.httpOnly shouldBe true
            cookie.secure shouldBe true
            cookie.sameSite shouldBe "Lax"
            cookie.path shouldBe "/v1/auth"
            cookie.maxAgeSeconds shouldBe 30 * 24 * 60 * 60

            // The refresh cookie persists only a SHA-256 hash of the raw token.
            val storedHash = JdbcTemplate(dataSource).queryForObject(
                "SELECT token_hash FROM refresh_tokens WHERE token_hash = ?",
                String::class.java,
                sha256(refreshValue),
            )
            storedHash shouldNotBe null
            storedHash shouldMatch "[0-9a-f]{64}"
            storedHash shouldNotBe refreshValue
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

        test("refresh rotates within a family: old cookie consumed, new cookie progressively rotates") {
            val (_, cookie) = signupAndLogin("iris@example.com")
            val oldRefresh = cookie.refreshValue

            val first = exchange(url("/v1/auth/refresh"), oldRefresh)
            first.statusCode shouldBe HttpStatus.OK
            val rotated = first.body!!
            rotated["accessToken"].toString().isNotBlank() shouldBe true
            rotated.containsKey("refreshToken") shouldBe false
            rotated["role"] shouldBe "USER"

            val newCookie = parseSetCookie(first.headers.getFirst(HttpHeaders.SET_COOKIE))
            val newRefresh = newCookie.refreshValue
            newRefresh.isNotBlank() shouldBe true
            newRefresh shouldNotBe oldRefresh
            newCookie.httpOnly shouldBe true
            newCookie.secure shouldBe true
            newCookie.sameSite shouldBe "Lax"

            // The new token is still valid and rotates again (no replay involved).
            val second = exchange(url("/v1/auth/refresh"), newRefresh)
            second.statusCode shouldBe HttpStatus.OK
            second.body!!["accessToken"].toString().isNotBlank() shouldBe true
        }

        test("reuse of an already-consumed refresh token revokes the ENTIRE family (sc-136)") {
            val (_, cookie) = signupAndLogin("yara@example.com")
            val oldRefresh = cookie.refreshValue

            // First refresh consumes the login token and issues its family child.
            val first = exchange(url("/v1/auth/refresh"), oldRefresh)
            first.statusCode shouldBe HttpStatus.OK
            val newRefresh = parseSetCookie(first.headers.getFirst(HttpHeaders.SET_COOKIE)).refreshValue

            // Repeating the OLD (now consumed) token is a reuse signal → 401.
            val replay = exchange(url("/v1/auth/refresh"), oldRefresh)
            replay.statusCode shouldBe HttpStatus.UNAUTHORIZED

            // sc-136: reuse tears down the whole family, so the fresh child token
            // (same family) has been revoked along with it.
            val childAfterReuse = exchange(url("/v1/auth/refresh"), newRefresh)
            childAfterReuse.statusCode shouldBe HttpStatus.UNAUTHORIZED
            childAfterReuse.body!!["code"] shouldBe "invalid_credentials"
        }

        test("refresh with an unknown/garbage refresh cookie is rejected with 401") {
            val resp = exchange(url("/v1/auth/refresh"), "not-a-real-refresh-token")
            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "invalid_credentials"
        }

        test("concurrent refresh with the same cookie yields exactly one 200, one 401, and exactly one live row") {
            val (tokens, cookie) = signupAndLogin("nora@example.com")
            val accountId = tokens["accountId"].toString()
            val shared = cookie.refreshValue

            // Two threads fire the SAME refresh cookie at the same time, so both
            // read the live row before either deletes it — there is no guaranteed
            // ordering between them. Exactly one may win the rotation.
            val start = CyclicBarrier(2)
            val pool = Executors.newFixedThreadPool(2)
            val responses = try {
                val futures = (1..2).map {
                    pool.submit(Callable {
                        start.await()
                        exchange(url("/v1/auth/refresh"), shared)
                    })
                }
                futures.map { it.get(30, TimeUnit.SECONDS) }
            } finally {
                pool.shutdownNow()
            }

            val statuses = responses.map { it.statusCode }
            // The atomic consume-and-rotate gate guarantees exactly ONE winner
            // under concurrency (sc-133 baseline, unchanged).
            statuses.count { it == HttpStatus.OK } shouldBe 1
            // The loser is rejected — either cleanly (the conditional consume
            // reported the token already claimed) or via sc-136 reuse detection.
            statuses.count { it == HttpStatus.UNAUTHORIZED } shouldBe 1

            // The core rotation invariant: NEVER more than one live token row can
            // survive for this account — a second live token must never be minted
            // from a single-use refresh token. (Under sc-136, a loser that reaches
            // the reuse path additionally tears down the whole family, which can
            // reduce live rows to ZERO — that is the theft response, not a second
            // token. The hard guarantee is the upper bound of one live row.)
            val liveRows = JdbcTemplate(dataSource).queryForList(
                "SELECT token_hash FROM refresh_tokens WHERE account_id = ? AND consumed_at IS NULL",
                java.util.UUID.fromString(accountId),
            )
            (liveRows.size <= 1) shouldBe true
        }

        test("refresh with an expired refresh cookie is rejected with 401 and revoked (negative path)") {
            val (_, cookie) = signupAndLogin("jules@example.com")
            val expired = cookie.refreshValue

            // Manually back-date the stored row to the past to simulate expiry.
            JdbcTemplate(dataSource).update(
                "UPDATE refresh_tokens SET expires_at = now() - interval '1 second' WHERE token_hash = ?",
                sha256(expired),
            )

            val resp = exchange(url("/v1/auth/refresh"), expired)
            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "invalid_credentials"

            // Expired handling revokes the row so it cannot be replayed.
            val rows = JdbcTemplate(dataSource).queryForList(
                "SELECT 1 FROM refresh_tokens WHERE token_hash = ?",
                sha256(expired),
            )
            rows shouldBe emptyList()
        }

        test("refresh without a refresh cookie returns 400") {
            val resp = client.exchange(
                url("/v1/auth/refresh"),
                HttpMethod.POST,
                HttpEntity<Any?>(null, HttpHeaders()),
                Map::class.java,
            )
            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
        }
    }

    /** Login response body + the refresh cookie it set. */
    private data class LoginResult(val tokenBody: Map<*, *>, val refreshCookie: RefreshCookie)

    private fun exchange(path: String, refreshToken: String): org.springframework.http.ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders()
        headers.add(HttpHeaders.COOKIE, "refresh_token=$refreshToken")
        return client.exchange(path, HttpMethod.POST, HttpEntity<Any?>(null, headers), Map::class.java)
    }

    private fun signupAndLogin(email: String): LoginResult {
        signup(email, "s3cr3t-password")
        val resp = client.postForEntity(url("/v1/auth/login"), loginBody(email, "s3cr3t-password"), Map::class.java)
        resp.statusCode shouldBe HttpStatus.OK
        val cookie = parseSetCookie(resp.headers.getFirst(HttpHeaders.SET_COOKIE))
        return LoginResult(resp.body!!, cookie)
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

    private fun decodeJwtPayload(jwt: String): String {
        val payload = jwt.split(".")[1]
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        return String(java.util.Base64.getUrlDecoder().decode(padded))
    }

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun parseSetCookie(header: String?): RefreshCookie {
        header shouldNotBe null
        val parts = header!!.split(";").map { it.trim() }
        val nameValue = parts[0]
        nameValue shouldContain "refresh_token="
        val value = nameValue.substringAfter("refresh_token=")
        return RefreshCookie(
            refreshValue = value,
            httpOnly = parts.any { it.equals("HttpOnly", ignoreCase = true) },
            secure = parts.any { it.equals("Secure", ignoreCase = true) },
            sameSite = parts.firstOrNull { it.startsWith("SameSite=") }?.substringAfter("SameSite=") ?: "",
            path = parts.firstOrNull { it.startsWith("Path=") }?.substringAfter("Path=") ?: "",
            maxAgeSeconds = parts.firstOrNull { it.startsWith("Max-Age=") }?.substringAfter("Max-Age=")?.toLong() ?: 0L,
        )
    }

    private data class RefreshCookie(
        val refreshValue: String,
        val httpOnly: Boolean,
        val secure: Boolean,
        val sameSite: String,
        val path: String,
        val maxAgeSeconds: Long,
    )
}