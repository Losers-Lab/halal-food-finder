description = "Persistence adapter: PostgreSQL/PostGIS via Spring Data JDBC + jOOQ + Flyway migrations."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.boot.starter.jdbc)

    // Test-only harness: Testcontainers PostGIS smoke test proves the TDD harness.
    testImplementation(libs.postgresql)
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.slf4j.simple)
}