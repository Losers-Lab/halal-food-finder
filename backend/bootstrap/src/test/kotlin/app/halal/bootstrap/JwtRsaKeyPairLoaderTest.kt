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
 * unchanged; the prod-profile guard refuses to boot on known dev/default keys.
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

    // sc-134 follow-up: prod-profile key guard. Production must never boot with a
    // known dev/default JWT signing key. The guard compares the SHA-256 fingerprint
    // of the configured key's DER encoding against a denylist of known dev keys
    // (the committed test key) and fails fast, WITHOUT
    // echoing the secret value.

    test("prod profile refuses the committed dev test key") {
        val devKeyB64 = ClassPathResource("test-jwt-rsa-private.pem").inputStream.bufferedReader().use { it.readText() }.trim()
        val err = shouldThrow<IllegalStateException> {
            JwtRsaKeyPairLoader.loadKeyPair(devKeyB64, prodProfile = true)
        }
        err.message shouldContain "app.jwt.rsa-private-key-base64"
        err.message shouldContain "KNOWN DEVELOPMENT key"
        err.message shouldNotContain devKeyB64.take(20)
    }

    test("dev profile still accepts the committed dev test key") {
        val devKeyB64 = ClassPathResource("test-jwt-rsa-private.pem").inputStream.bufferedReader().use { it.readText() }.trim()
        val pair = JwtRsaKeyPairLoader.loadKeyPair(devKeyB64, prodProfile = false)
        pair.private.algorithm shouldBe "RSA"
    }

    test("prod profile refuses a blank key (no silent ephemeral key in prod)") {
        val err = shouldThrow<IllegalStateException> {
            JwtRsaKeyPairLoader.loadKeyPair("", prodProfile = true)
        }
        err.message shouldContain "app.jwt.rsa-private-key-base64"
        err.message shouldNotContain "\n"
    }

    test("prod profile accepts a freshly generated non-dev key") {
        val kg = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
        val fresh = kg.generateKeyPair()
        val b64 = java.util.Base64.getEncoder().encodeToString(fresh.private.encoded)
        val pair = JwtRsaKeyPairLoader.loadKeyPair(b64, prodProfile = true)
        pair.private.encoded shouldBe fresh.private.encoded
    }
})
