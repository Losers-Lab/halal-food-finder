description = "Verification adapter: hosted image-recognition model for certification photos. Default VerificationProvider = HostedVisionAdapter (Gemini 2.5 Flash paid tier, human-in-the-loop)."

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))

    // JSON tree parsing of the hosted provider's response (model verdict + confidence).
    implementation(libs.jackson.databind)
}