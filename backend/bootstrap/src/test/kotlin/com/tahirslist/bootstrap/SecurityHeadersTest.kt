package com.tahirslist.bootstrap

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.core.env.Environment
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * sc-134 security headers + error containment at the HTTP edge.
 *
 * Security headers (finding #8): every response carrier set by the security
 * filter chain (nosniff, frame-deny, CSP) is present on public routes. HSTS is
 * configured for TLS transports and is asserted via the filter config rather
 * than an HTTP test (the test transport is plain HTTP, where Spring suppresses
 * HSTS).
 *
 * Error containment (finding #6): the bean-validation 400 path for the auth
 * DTOs must still return a 400 with the generic `invalid_input` envelope (and
 * per-field detail) — never a 500 — now that the raw `ex.message` handlers were
 * replaced by a catch-all advice with a ratified whitelist.
 */
class SecurityHeadersTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var environment: Environment

    init {
        test("health details are hidden outside the dev profile (finding #8)") {
            // PostgresBootTest runs with the default profile; health internals must
            // NOT be exposed. The dev-only override flips this to 'always'.
            environment.getProperty("management.endpoint.health.show-details") shouldBe "never"
        }

        test("public /v1/health response carries baseline edge security headers") {
            val resp: ResponseEntity<String> = restTemplate.getForEntity("/v1/health", String::class.java)

            resp.statusCode shouldBe HttpStatus.OK
            resp.headers["X-Content-Type-Options"]!!.single() shouldBe "nosniff"
            // API serves no embedded pages: deny frames outright, never 'self'.
            resp.headers["Content-Security-Policy"]!!.single()
                .shouldContain("frame-ancestors 'none'")
        }

        test("frame embedding is denied on the public route") {
            val resp: ResponseEntity<String> = restTemplate.getForEntity("/v1/health", String::class.java)

            val frameOptions = resp.headers["X-Frame-Options"]?.single().orEmpty()
            val csp = resp.headers["Content-Security-Policy"]?.single().orEmpty()
            (frameOptions.isNotBlank() || csp.contains("frame-ancestors 'none'")) shouldBe true
        }

        test("bean-validation 400 on login stays 400 invalid_input with field detail, not a 500") {
            // Empty-string email/password fails @NotBlank/@Email bean validation server-side.
            val resp = jsonPost("/v1/auth/login", """{"email":"","password":""}""")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            resp.body!!.shouldContain("\"code\":\"invalid_input\"")
            resp.body!!.shouldContain("email is required")
            resp.body!!.shouldContain("password is required")
        }

        test("signup empty-password validation stays 400 invalid_input, not a 500") {
            val resp = jsonPost("/v1/auth/signup", """{"email":"x@example.com","password":""}""")

            resp.statusCode shouldBe HttpStatus.BAD_REQUEST
            resp.body!!.shouldContain("\"code\":\"invalid_input\"")
        }
    }

    private fun jsonPost(path: String, jsonBody: String): ResponseEntity<String> {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        return restTemplate.postForEntity(path, HttpEntity(jsonBody, headers), String::class.java)
    }
}