package app.halal.bootstrap

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.core.io.ClassPathResource

/**
 * sc-134 fail-fast config: the HTTP app must not silently start (or fail later
 * with an obscure crypto exception) when a JWT signing key IS provided but is
 * invalid. Loading an invalid configured key must fail fast with a clear,
 * actionable message that names the property without echoing the (secret) value.
 *
 * The deliberate long-jump around ephemeral key generation for local dev is
 * unchanged (the full prod-profile key-guard is a separate follow-up).
 */
class JwtRsaKeyPairLoaderTest : FunSpec({

    test("a malformed (non-base64) configured key fails fast with a clear message") {
        val err = shouldThrow<IllegalStateException> {
            JwtRsaKeyPairLoader.loadKeyPair("!!!not-base64!!!")
        }
        err.message shouldContain "app.jwt.rsa-private-key-base64"
        err.message shouldNotContain "!!!not-base64!!!"
    }

    test("base64 that is not a valid PKCS#8 RSA private key fails fast") {
        // Valid base64, but decodes to arbitrary bytes, not a PKCS#8 RSA key.
        val garbage = java.util.Base64.getEncoder().encodeToString("definitely-not-an-rsa-key".toByteArray())
        val err = shouldThrow<IllegalStateException> {
            JwtRsaKeyPairLoader.loadKeyPair(garbage)
        }
        err.message shouldContain "app.jwt.rsa-private-key-base64"
        err.message shouldNotContain garbage
    }

    test("a valid configured PKCS#8 RSA key loads successfully") {
        val b64 = ClassPathResource("test-jwt-rsa-private.pem").inputStream.bufferedReader().use { it.readText() }.trim()
        val pair = JwtRsaKeyPairLoader.loadKeyPair(b64)
        pair.private.algorithm shouldBe "RSA"
    }

    test("a blank key falls back to an ephemeral generated pair for local dev") {
        val pair = JwtRsaKeyPairLoader.loadKeyPair("")
        pair.private.algorithm shouldBe "RSA"
        pair.public.algorithm shouldBe "RSA"
    }
})
