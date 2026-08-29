description = "Web adapter: code-first REST controllers and OpenAPI (springdoc) configuration."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    implementation(libs.spring.boot.starter.web)
    implementation(libs.springdoc)
}