package com.tahirslist.storage.s3

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI

/**
 * Configuration properties for the S3-compatible object store. MinIO local
 * defaults; Cloudflare R2 later is a config change (endpoint/region/creds) not
 * a code change. No AWS-only feature is relied upon.
 *
 * Enabled only when `app.storage.s3.endpoint` is set (i.e. a real object store
 * is configured). When absent, the bootstrap wiring falls back to
 * [com.tahirslist.application.image.InMemoryImagePort] so the app boots for dev/test
 * without MinIO.
 */
@ConfigurationProperties(prefix = "app.storage.s3")
data class S3StorageProperties(
    val endpoint: String? = null,
    val region: String = "us-east-1",
    val accessKey: String? = null,
    val secretKey: String? = null,
    val bucket: String = "halal-images",
)

@Configuration
@ConditionalOnProperty(prefix = "app.storage.s3", name = ["endpoint"])
@EnableConfigurationProperties(S3StorageProperties::class)
class S3ImagePortConfig {

    @Bean
    fun s3Client(props: S3StorageProperties): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(props.region))
            // Path-style addressing is required by MinIO (and R2) — it is not the
            // default virtual-hosted AWS behavior.
            .serviceConfiguration(
                software.amazon.awssdk.services.s3.S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build(),
            )
        if (props.endpoint != null) builder.endpointOverride(URI.create(props.endpoint))
        if (props.accessKey != null && props.secretKey != null) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey, props.secretKey)),
            )
        }
        return builder.build()
    }

    @Bean
    fun imagePort(s3: S3Client, props: S3StorageProperties): com.tahirslist.application.image.ImagePort =
        S3ImagePort(s3 = s3, bucket = props.bucket)
}