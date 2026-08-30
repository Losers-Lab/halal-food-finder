description = "Web adapter: code-first REST controllers and OpenAPI (springdoc) configuration."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.springdoc)
    // Bean validation (@Valid/@NotBlank/@Email) for request DTOs.
    implementation(libs.spring.boot.starter.validation)
    // Kotlin data-class JSON serialization.
    implementation(libs.jackson.module.kotlin)
    // In-process token-bucket rate limiting for auth endpoints (sc-136).
    implementation(libs.bucket4j)
}