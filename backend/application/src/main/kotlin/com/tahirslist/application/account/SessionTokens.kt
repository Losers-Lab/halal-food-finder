package com.tahirslist.application.account

/**
 * The token pair returned to the caller after a successful login or refresh.
 */
data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val accessTokenExpiresInSeconds: Long,
)