package com.tahirslist.bootstrap

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPublicKeySpec
import java.util.Base64

/**
 * sc-134 fail-fast config: loads the RS256 JWT signing key pair from the
 * `app.jwt.rsa-private-key-base64` property (base64 of a PKCS#8 DER private
 * key) and FAILS FAST with a clear, actionable message when a value is provided
 * but cannot be decoded / is not a valid RSA private key.
 *
 * The message names the misconfigured property but deliberately never echoes
 * the (secret) configured value, so the failure is diagnosable without dumping
 * the key into logs. Only a blank value falls back to an ephemeral local-dev key.
 *
 * Prod-profile key guard (sc-134 follow-up): when [loadKeyPair] runs with
 * `prodProfile = true`, the loader additionally refuses to boot when
 *
 *  1. the property is blank (an ephemeral per-boot key is local-dev only —
 *     every restart would invalidate all issued access tokens), or
 *  2. the configured key's SHA-256 fingerprint matches a KNOWN DEV key —
 *     currently the committed test fixture `test-jwt-rsa-private.pem`
 *     (fingerprint of its PKCS#8 DER encoding). A prod deployment carrying a
 *     key that is public in the repo is equivalent to having no signing key at
 *     all: anyone with repo access can forge admin tokens.
 *
 * In the `dev` profile the guard is bypassed so local workflows keep working.
 */
object JwtRsaKeyPairLoader {

    private const val KEY_PROP = "app.jwt.rsa-private-key-base64"

    /**
     * SHA-256 fingerprints (hex) of known dev/default JWT signing keys, keyed by
     * the PKCS#8 DER encoding of the private key. The committed test fixture
     * `bootstrap/src/test/resources/test-jwt-rsa-private.pem` is public in the
     * repository and must never sign tokens in production.
     */
    private val KNOWN_DEV_KEY_FINGERPRINTS: Set<String> = setOf(
        // test-jwt-rsa-private.pem (committed test fixture)
        "766b33dea3ccd7c432c4e365d3a9864f121cccda577b903960115e9ef55c7dd8",
    )

    /**
     * Returns the configured key pair, or an ephemeral generated pair when the
     * property is blank AND [prodProfile] is false (local dev only). Throws
     * [IllegalStateException] (which aborts Spring context startup) on any
     * invalid provided value, and in prod also on blank values and on any key
     * whose fingerprint matches a known dev/default key.
     */
    fun loadKeyPair(privateKeyB64: String, prodProfile: Boolean = false): KeyPair {
        if (privateKeyB64.isBlank()) {
            if (prodProfile) {
                throw IllegalStateException(
                    "Misconfigured $KEY_PROP: no signing key is configured and the " +
                        "active profile is a production profile. An ephemeral per-boot key " +
                        "is local-development only (every restart invalidates issued tokens). " +
                        "Supply a base64-encoded PKCS#8 DER RSA private key that is NOT a " +
                        "key committed to this repository.",
                )
            }
            val keyGen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            return keyGen.generateKeyPair()
        }
        val der = try {
            Base64.getDecoder().decode(privateKeyB64)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException(
                "Misconfigured $KEY_PROP: value is not valid base64. " +
                    "Supply a base64-encoded PKCS#8 DER RSA private key.",
                e,
            )
        }
        val keyFactory = KeyFactory.getInstance("RSA")
        val private = try {
            keyFactory.generatePrivate(PKCS8EncodedKeySpec(der)) as RSAPrivateCrtKey
        } catch (e: java.security.GeneralSecurityException) {
            throw IllegalStateException(
                "Misconfigured $KEY_PROP: value is not a valid PKCS#8 RSA private key. " +
                    "Supply a base64-encoded PKCS#8 DER RSA private key.",
                e,
            )
        }
        if (prodProfile) {
            val fingerprint = MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }
            if (fingerprint in KNOWN_DEV_KEY_FINGERPRINTS) {
                throw IllegalStateException(
                    "Misconfigured $KEY_PROP: the configured signing key is a KNOWN " +
                        "DEVELOPMENT key (its fingerprint matches a key committed to this " +
                        "repository). A production deployment must never sign tokens with a " +
                        "key that is public in the repo — anyone with repo access could forge " +
                        "tokens for any account, including admin roles. Generate a fresh " +
                        "private key for production and provision it out-of-band " +
                        "(e.g. the platform secret manager).",
                )
            }
        }
        val public = keyFactory.generatePublic(
            RSAPublicKeySpec(private.modulus, private.publicExponent),
        ) as RSAPublicKey
        return KeyPair(public, private)
    }
}
