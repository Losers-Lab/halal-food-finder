package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
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
import javax.sql.DataSource

/**
 * End-to-end Log Out (sc-133 cookie contract, building on sc-132) test against
 * the full application graph. Logout now reads the refresh token from the
 * HttpOnly cookie (it no longer accepts a request body), revokes it server-side,
 * and clears the cookie. Proves a later refresh with the revoked cookie is
 * rejected 401, and that logout stays idempotent — an unknown or absent cookie
 * still returns 204 and never leaks token validity.
 */
class LogoutEndpointTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var dataSource: DataSource

    // Same 401-tolerant, cookie-management-disabled client as LoginEndpointTest:
    // the test decides which refresh cookie each request presents.
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
        test("logout reads the refresh cookie, revokes it (row gone), and a later refresh with it is rejected 401") {
            val (_, cookie) = signupAndLogin("kim@example.com")
            val refreshToken = cookie.refreshValue
            refreshHash(refreshToken) shouldBe true

            val logoutResp = client.exchange(
                url("/v1/auth/logout"),
                HttpMethod.POST,
                cookieEntity(refreshToken),
                Map::class.java,
            )
            logoutResp.statusCode shouldBe HttpStatus.NO_CONTENT

            // The server clears the refresh cookie so the client session is ended.
            val clearCookie = parseSetCookie(logoutResp.headers.getFirst(HttpHeaders.SET_COOKIE))
            clearCookie.maxAgeSeconds shouldBe 0L

            // The row is gone from storage (token can no longer be resolved).
            refreshHash(refreshToken) shouldBe false

            // Refreshing with the revoked cookie must fail closed.
            val replay = exchange(url("/v1/auth/refresh"), refreshToken)
            replay.statusCode shouldBe HttpStatus.UNAUTHORIZED
            replay.body!!["code"] shouldBe "invalid_credentials"
        }

        test("logout is idempotent: an unknown/never-issued refresh cookie still returns 204") {
            val resp = client.exchange(
                url("/v1/auth/logout"),
                HttpMethod.POST,
                cookieEntity("never-issued-refresh-token"),
                Map::class.java,
            )
            resp.statusCode shouldBe HttpStatus.NO_CONTENT
        }

        test("logout is idempotent: calling it twice with the same cookie succeeds both times") {
            val (_, cookie) = signupAndLogin("leon@example.com")
            val refreshToken = cookie.refreshValue

            val first = client.exchange(url("/v1/auth/logout"), HttpMethod.POST, cookieEntity(refreshToken), Map::class.java)
            val second = client.exchange(url("/v1/auth/logout"), HttpMethod.POST, cookieEntity(refreshToken), Map::class.java)

            first.statusCode shouldBe HttpStatus.NO_CONTENT
            second.statusCode shouldBe HttpStatus.NO_CONTENT
        }

        test("logout without a refresh cookie still returns 204 (idempotent, no body required)") {
            val resp = client.exchange(
                url("/v1/auth/logout"),
                HttpMethod.POST,
                HttpEntity<Any?>(null, HttpHeaders()),
                Map::class.java,
            )
            resp.statusCode shouldBe HttpStatus.NO_CONTENT
        }
    }

    private fun exchange(path: String, refreshToken: String): org.springframework.http.ResponseEntity<Map<*, *>> =
        client.exchange(path, HttpMethod.POST, cookieEntity(refreshToken), Map::class.java)

    private fun cookieEntity(refreshToken: String): HttpEntity<Any?> {
        val headers = HttpHeaders()
        headers.add(HttpHeaders.COOKIE, "refresh_token=$refreshToken")
        return HttpEntity<Any?>(null, headers)
    }

    private fun signupAndLogin(email: String): Pair<Map<*, *>, RefreshCookie> {
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
        val cookie = parseSetCookie(resp.headers.getFirst(HttpHeaders.SET_COOKIE))
        return resp.body!! to cookie
    }

    private fun loginBody(email: String, password: String): HttpEntity<Map<String, String>> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return HttpEntity(mapOf("email" to email, "password" to password), headers)
    }

    /** True if a row for this refresh token's SHA-256 hash still exists in storage. */
    private fun refreshHash(token: String): Boolean {
        val rows = JdbcTemplate(dataSource).queryForList(
            "SELECT 1 FROM refresh_tokens WHERE token_hash = ?",
            sha256(token),
        )
        return !rows.isEmpty()
    }

    private fun parseSetCookie(header: String?): RefreshCookie {
        header shouldNotBe null
        val parts = header!!.split(";").map { it.trim() }
        val nameValue = parts[0]
        nameValue shouldContain "refresh_token="
        return RefreshCookie(
            refreshValue = nameValue.substringAfter("refresh_token="),
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

    private fun sha256(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}