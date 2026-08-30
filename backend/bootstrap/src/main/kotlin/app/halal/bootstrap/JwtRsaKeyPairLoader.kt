package app.halal.bootstrap

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
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
 */
object JwtRsaKeyPairLoader {

    private const val KEY_PROP = "app.jwt.rsa-private-key-base64"

    /**
     * Returns the configured key pair, or an ephemeral generated pair when the
     * property is blank (local dev only). Throws [IllegalStateException] (which
     * aborts Spring context startup) on any invalid provided value.
     */
    fun loadKeyPair(privateKeyB64: String): KeyPair {
        if (privateKeyB64.isBlank()) {
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
        val public = keyFactory.generatePublic(
            RSAPublicKeySpec(private.modulus, private.publicExponent),
        ) as RSAPublicKey
        return KeyPair(public, private)
    }
}
