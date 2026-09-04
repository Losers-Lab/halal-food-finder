package com.tahirslist.storage.s3

import com.tahirslist.application.image.ImagePort
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.image.StoredImage
import java.util.UUID
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * S3-compatible [ImagePort] (MinIO now, Cloudflare R2 later — both speak the S3
 * API). The endpoint/region/credentials come from config; this class uses only
 * standard S3 operations (PutObject/GetObject/CreateBucket) so no AWS-only
 * feature is relied upon (docs/design/sc-157-image-variants.md).
 *
 * Storage layout (bucket owned by this adapter):
 *   `heroes/{listingId}/{variant}`  ->  object bytes  (content-type on object)
 *
 * The application never sees keys or the bucket; that isolation is what keeps
 * the adapter swappable. The bucket is created lazily on first [save] so a
 * fresh MinIO needs no pre-provisioning step.
 */
class S3ImagePort(
    private val s3: S3Client,
    private val bucket: String,
) : ImagePort {

    override fun save(listingId: UUID, variant: ImageVariant, contentType: String, bytes: ByteArray) {
        ensureBucket()
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key(listingId, variant))
            .contentType(contentType)
            .build()
        s3.putObject(request, RequestBody.fromBytes(bytes))
    }

    override fun load(listingId: UUID, variant: ImageVariant): StoredImage? {
        ensureBucket()
        val request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key(listingId, variant))
            .build()
        return try {
            val response = s3.getObject(request)
            val bytes = response.readAllBytes()
            val contentType = response.response().contentType().orEmpty().ifBlank { "application/octet-stream" }
            StoredImage(bytes = bytes, contentType = contentType)
        } catch (e: NoSuchKeyException) {
            null // listing has no stored image for this variant
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }
    }

    override fun delete(listingId: UUID, variant: ImageVariant) {
        ensureBucket()
        val request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key(listingId, variant))
            .build()
        // S3 DeleteObject of a non-existent key is an idempotent no-op (204),
        // matching the port's "delete of unsaved variant is silent" contract.
        s3.deleteObject(request)
    }

    private fun key(listingId: UUID, variant: ImageVariant): String =
        "heroes/$listingId/${variant.name}"

    private fun ensureBucket() {
        val exists = try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build())
            true
        } catch (e: NoSuchBucketException) {
            false
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) false else throw e
        }
        if (!exists) {
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build())
        }
    }
}