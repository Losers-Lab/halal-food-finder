description = "Bootstrap: Spring Boot application assembly and entrypoint."

plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":web-api"))
    // Persistence adapter on the runtime classpath so its @Repository beans and the
    // Flyway auto-migration (users table) are picked up by component scan.
    implementation(project(":persistence"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc)
    // Argon2id password hashing (ratified auth stack).
    implementation(libs.spring.security.crypto)
    implementation(libs.bouncycastle)
    // RS256 JWT access-token issuance (ratified auth stack, short access JWT).
    implementation(libs.nimbus.jose.jwt)
    // OAuth2 resource server: verifies the RS256 access JWT at the HTTP edge and
    // enforces deny-by-default (sc-131). Brings in Spring Security + request matcher support.
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    // PostgreSQL JDBC driver at runtime (datasource for the users table).
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotest.spring)
    // JdbcTemplate for asserting persisted rows in integration tests.
    testImplementation(libs.spring.boot.starter.jdbc)
    // Testcontainers PostGIS for full-context boot tests (see PostgresBootTest).
    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.postgresql)
    // Apache HC5 client so tests can read 401 responses (JDK client cannot).
    testImplementation(libs.httpclient5)
}