import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
}

// Version-catalog accessors are generated for the root script scope; capture the
// BOM version here so subprojects can reference it without the `libs` extension.
val springBootVersion = libs.versions.spring.boot.get()
// Same for the shared test deps applied to every subproject below.
val testKotestRunner = libs.kotest.runner.junit5
val testKotestAssertions = libs.kotest.assertions.core
val testMockk = libs.mockk

allprojects {
    group = "com.tahirslist"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    // Kotlin-spring (all-open) preconfigured: opens classes annotated with
    // @Component/@Configuration/@SpringBootApplication etc. so CGLIB proxying works.
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    // Enables the Spring Boot dependencyManagement BOM import below in each module.
    apply(plugin = "io.spring.dependency-management")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // Import the Spring Boot BOM so library versions are managed centrally.
    dependencyManagement {
        imports {
            mavenBom("org.springframework.boot:spring-boot-dependencies:$springBootVersion")
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xjsr305=strict")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        // Docker Desktop 4.52+ ships Engine 29.x, which rejects docker-java's
        // default negotiated API version (1.32) with HTTP 400. Pin a modern API
        // version the engine accepts so Testcontainers can connect in this
        // environment. See testcontainers-java #11212 / #11235.
        systemProperty("api.version", "1.44")
        testLogging {
            events("passed", "skipped", "failed")
            // Surface Testcontainers' docker-client diagnostics (which it writes to
            // stderr) so harness failures are explainable rather than opaque.
            showStandardStreams = true
            showStackTraces = true
        }
    }

    dependencies {
        "testImplementation"(kotlin("test"))
        "testImplementation"(testKotestRunner)
        "testImplementation"(testKotestAssertions)
        "testImplementation"(testMockk)
    }
}