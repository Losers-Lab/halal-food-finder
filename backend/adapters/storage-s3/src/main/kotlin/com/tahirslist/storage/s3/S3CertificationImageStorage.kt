package com.tahirslist.storage.s3

import com.tahirslist.application.verification.CertificationImageStorage
import java.util.UUID
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception

/**
 * S3-compatible [CertificationImageStorage] (MinIO now, Cloudflare R2 later) — the
 * certification image archive for the owner-claim vertical (sc-46). Shares the
 * same S3 client/bucket as [S3ImagePort] but a distinct key namespace, so a cert
 * image can never be mistaken for a hero variant.
 *
 * Storage layout (bucket owned like the hero adapter):
 *   `certifications/{listingId}/{uuid}`  ->  object bytes  (content-type on object)
 *
 * Keys live entirely inside this adapter (never leaked to the application layer),
 * and each upload gets a fresh UUID so re-claims never silently overwrite earlier
 * evidence. The bucket is created lazily on first save, mirroring [S3ImagePort].
 */
class S3CertificationImageStorage(
    private val s3: S3Client,
    private val bucket: String,
) : CertificationImageStorage {

    override fun save(listingId: UUID, contentType: String, bytes: ByteArray) {
        ensureBucket()
        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key("certifications/$listingId/${UUID.randomUUID()}")
            .contentType(contentType)
            .build()
        s3.putObject(request, RequestBody.fromBytes(bytes))
    }

    override fun loadLatest(listingId: UUID): CertificationImageStorage.StoredCertificationImage? {
        ensureBucket()
        val objects = s3.listObjectsV2(
            ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix("certifications/$listingId/")
                .build(),
        ).contents()
        // Keys carry no timestamps; history keeps one object per submission, so
        // "latest" is the most recently modified of the listing's objects.
        val latest = objects.maxByOrNull { it.lastModified() } ?: return null
        val got = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(latest.key()).build())
        val bytes = got.readAllBytes()
        return CertificationImageStorage.StoredCertificationImage(
            contentType = got.response().contentType() ?: "application/octet-stream",
            bytes = bytes,
        )
    }

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