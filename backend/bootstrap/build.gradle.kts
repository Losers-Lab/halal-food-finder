description = "Bootstrap: Spring Boot application assembly and entrypoint."

plugins {
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":web-api"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.springdoc)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotest.spring)
}