package com.tahirslist.verification.ai

/**
 * Configuration for the hosted vision provider.
 *
 * [endpoint] is the full provider URL the transport POSTs to ([RestVisionModelClient]).
 * [modelName] selects the model (ratified default gemini-2.5-flash; Claude Haiku
 * 4.5 is the documented alternative). [apiKey] is OPTIONAL here and never a baked
 * literal — the bootstrap/app wiring supplies it from a secret source at runtime;
 * null is valid for tests/local servers. [timeoutMillis] bounds a call.
 *
 * Wiring this into a Spring bean and reading the key from an env/secret manager
 * is sc-46 (bootstrap) work — deliberately not done in sc-117.
 */
data class HostedVisionConfig(
    val endpoint: String,
    val modelName: String = "gemini-2.5-flash",
    val apiKey: String? = null,
    val timeoutMillis: Long = 5_000,
) {
    init {
        require(endpoint.isNotBlank()) { "Endpoint must not be blank." }
        require(timeoutMillis > 0) { "Timeout must be positive." }
    }
}