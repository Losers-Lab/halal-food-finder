package com.tahirslist.storage.s3

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import java.util.UUID

/**
 * MinIO-backed proof that the certification-image adapter (sc-46) persists under
 * its own `certifications/` key namespace and round-trips bytes + content type.
 * Runs in the containerized test (CI); no live storage is touched.
 */
class S3CertificationImageStorageTest : FunSpec() {

    private val s3: S3Client = run {
        val minio: MinIOContainer = MinIOContainer(
            DockerImageName.parse("minio/minio:RELEASE.2025-04-22T22-12-26Z"),
        )
        minio.start()
        S3Client.builder()
            .endpointOverride(java.net.URI.create(minio.s3URL))
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(minio.userName, minio.password)),
            )
            .region(Region.of("us-east-1"))
            .serviceConfiguration(
                software.amazon.awssdk.services.s3.S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build(),
            )
            .build()
    }

    private val bucket = "certification-test"
    private val storage = S3CertificationImageStorage(s3 = s3, bucket = bucket)

    init {
        test("save writes the certification image under the certifications/ namespace and round-trips it") {
            val listingId = UUID.randomUUID()
            val bytes = byteArrayOf(1, 2, 3, 4)

            storage.save(listingId, "image/jpeg", bytes)

            val listingObjects = s3.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucket).prefix("certifications/$listingId/").build(),
            ).contents()
            listingObjects.size shouldBe 1
            val key = listingObjects[0].key()
            key.startsWith("certifications/$listingId/") shouldBe true

            val got = s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())
            got.readAllBytes() shouldBe bytes
            got.response().contentType() shouldBe "image/jpeg"
        }

        test("each upload for the same listing gets a fresh key (history is not overwritten)") {
            val listingId = UUID.randomUUID()

            storage.save(listingId, "image/jpeg", byteArrayOf(1))
            storage.save(listingId, "image/png", byteArrayOf(2))

            val listingObjects = s3.listObjectsV2(
                ListObjectsV2Request.builder().bucket(bucket).prefix("certifications/$listingId/").build(),
            ).contents()
            listingObjects.size shouldBe 2
            listingObjects.map { it.key() }.toSet().size shouldBe 2
        }

        test("a missing certification image key yields NoSuchKeyException (no silent empty read)") {
            val phantom = "certifications/${UUID.randomUUID()}/${UUID.randomUUID()}"

            shouldThrow<NoSuchKeyException> {
                s3.getObject(GetObjectRequest.builder().bucket(bucket).key(phantom).build())
            }
        }

        test("loadLatest reads back the most recently saved certification image, round-tripping bytes + content type") {
            val listingId = UUID.randomUUID()

            storage.save(listingId, "image/png", byteArrayOf(9, 8, 7))

            val latest = storage.loadLatest(listingId)

            latest shouldNotBe null
            latest!!.contentType shouldBe "image/png"
            latest.bytes shouldBe byteArrayOf(9, 8, 7)
        }

        test("loadLatest returns non-null across re-claims (history kept) and null for a listing with no cert") {
            val listingId = UUID.randomUUID()

            storage.save(listingId, "image/jpeg", byteArrayOf(1, 2))
            storage.save(listingId, "image/png", byteArrayOf(3, 4))

            storage.loadLatest(listingId) shouldNotBe null
            storage.loadLatest(UUID.randomUUID()) shouldBe null
        }
    }
}