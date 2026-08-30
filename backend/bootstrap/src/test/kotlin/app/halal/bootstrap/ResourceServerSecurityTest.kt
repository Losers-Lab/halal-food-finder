package app.halal.bootstrap

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Date

/**
 * sc-131 deny-by-default resource server: RS256 + iss + exp + role-claim
 * verification at the HTTP edge. The application boots with a fixed test RSA
 * key (read from classpath `test-jwt-rsa-private.pem`) so the test can mint
 * adversarial tokens with the matching private key, and every non-permitted
 * route requires a valid token.
 *
 * Negative paths (mandatory per docs/qa/auth-qa-assessment-and-testing-conventions.md,
 * Omar gate #8): expired, tampered, wrong-issuer, missing-role, and malformed
 * tokens must all yield a generic 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [ResourceServerSecurityTest.JwtKeyInitializer::class])
class ResourceServerSecurityTest : PostgresBootTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @LocalServerPort
    var port: Int = 0

    // HC5 request factory so the client can read 401 bodies (JDK client cannot in streaming mode).
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

        test("an unauthenticated request to a protected route returns a generic 401") {
            val resp = get(url("/v1/me"))

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "unauthorized"
        }

        test("a valid RS256 token with correct iss/exp/role is accepted") {
            val jwt = mint(role = "USER", sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = 3600)

            val resp = authenticatedGet(url("/v1/me"), jwt)

            resp.statusCode shouldBe HttpStatus.OK
            resp.body!!["subjectId"] shouldBe "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e"
            resp.body!!["role"] shouldBe "USER"
        }

        test("an expired token is rejected with a generic 401") {
            val jwt = mint(role = "USER", sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = -60)

            val resp = authenticatedGet(url("/v1/me"), jwt)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "unauthorized"
        }

        test("a tampered token is rejected with a generic 401") {
            val jwt = mint(role = "USER", sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = 3600)
            val tampered = tamper(jwt)

            val resp = authenticatedGet(url("/v1/me"), tampered)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "unauthorized"
        }

        test("a token with the wrong issuer is rejected with a generic 401") {
            val jwt = mint(role = "USER", sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", issuer = "evil-issuer", expSecondsAhead = 3600)

            val resp = authenticatedGet(url("/v1/me"), jwt)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "unauthorized"
        }

        test("a token missing the role claim is rejected with a generic 401") {
            val jwt = mint(role = null, sub = "2f3c1f9a-3f4b-4f6f-8bf7-2f8b1d3a9f5e", expSecondsAhead = 3600)

            val resp = authenticatedGet(url("/v1/me"), jwt)

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "unauthorized"
        }

        test("a malformed / non-JWT token is rejected with a generic 401") {
            val resp = authenticatedGet(url("/v1/me"), "not-a-jwt")

            resp.statusCode shouldBe HttpStatus.UNAUTHORIZED
            resp.body!!["code"] shouldBe "unauthorized"
        }

        test("public routes remain reachable without a token") {
            val resp = restTemplate.getForEntity("/v1/health", String::class.java)

            resp.statusCode shouldBe HttpStatus.OK
        }
    }

    private fun get(path: String): ResponseEntity<Map<*, *>> =
        client.exchange(path, HttpMethod.GET, null, Map::class.java)

    private fun authenticatedGet(path: String, bearerToken: String): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply { setBearerAuth(bearerToken) }
        return client.exchange(path, HttpMethod.GET, HttpEntity<Any>(headers), Map::class.java)
    }

    /** Mints and RS256-signs a token with the same test private key the app boots with. */
    private fun mint(
        role: String?,
        sub: String,
        issuer: String = "halal-food-finder",
        expSecondsAhead: Long,
    ): String {
        val builder = JWTClaimsSet.Builder()
            .subject(sub)
            .issuer(issuer)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(expSecondsAhead)))
        if (role != null) builder.claim("role", role)

        val signed = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(),
            builder.build(),
        )
        signed.sign(RSASSASigner(testPrivateKey()))
        return signed.serialize()
    }

    /** Corrupts a well-formed JWT's signature so signature verification must fail. */
    private fun tamper(jwt: String): String {
        val parts = jwt.split(".")
        require(parts.size == 3) { "expected a compact JWT" }
        val sig = parts[2].toCharArray()
        val flipped = if (sig[0] == 'A') 'B' else 'A'
        sig[0] = flipped
        return "${parts[0]}.${parts[1]}.${String(sig)}"
    }

    private fun testPrivateKey(): RSAPrivateKey {
        val der = Base64.getDecoder().decode(TEST_PRIVATE_KEY_B64)
        val factory = KeyFactory.getInstance("RSA")
        return factory.generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateKey
    }

    /** Point the app at the fixed test RSA key so issuer and verifier share the pair. */
    @TestConfiguration
    class JwtKeyInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(context: ConfigurableApplicationContext) {
            TestPropertyValues.of(
                "app.jwt.rsa-private-key-base64=$TEST_PRIVATE_KEY_B64",
                "app.jwt.issuer=halal-food-finder",
            ).applyTo(context.environment)
        }
    }

    companion object {
        /** Base64 PKCS#8 DER of the test RSA key (fixed at test time). */
        private val TEST_PRIVATE_KEY_B64: String by lazy {
            ClassPathResource("test-jwt-rsa-private.pem").inputStream.bufferedReader().use { it.readText() }.trim()
        }
    }
}