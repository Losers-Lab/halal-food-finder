package com.tahirslist.domain.verification

/**
 * The certification image submitted for review — the payload carried across the
 * [com.tahirslist.application.verification.VerificationProvider] seam.
 *
 * [bytes] are the cert-only image (upload hygiene — downscale, EXIF-strip, cert
 * not proof-of-ownership composite — is applied upstream in sc-46 before storage
 * and submission). A blank content type or an empty payload is rejected: the
 * seam must never carry a garbage image to a paid provider.
 */
class CertificationImage(
    val contentType: String,
    val bytes: ByteArray,
) {
    init {
        require(contentType.isNotBlank()) { "Certification image content type must not be blank." }
        require(bytes.isNotEmpty()) { "Certification image bytes must not be empty." }
    }
}