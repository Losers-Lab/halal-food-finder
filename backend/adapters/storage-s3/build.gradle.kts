description = "S3-compatible object storage adapter (MinIO local / Cloudflare R2 future)."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    // AWS SDK v2 S3 client, pointed at a configurable endpoint. MinIO now, R2
    // later by endpoint/region change — no AWS-only features (signed requests only).
    implementation(libs.aws.sdk.s3)
    implementation(libs.aws.sdk.url.connection.client)

    // Spring beans (configuration + adapter wiring) picked up by component scan.
    implementation(libs.spring.context)
    implementation(libs.spring.boot.autoconfigure)

    // Contract spec shared from the application layer + Testcontainers MinIO for
    // the adapter-swap test.
    testImplementation(testFixtures(project(":application")))
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.minio)
    testRuntimeOnly(libs.slf4j.simple)
}