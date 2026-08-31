package com.tahirslist.web.account

import com.tahirslist.application.account.LogoutSession
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Log Out (sc-132) endpoint on the sc-133 cookie contract. Reads the refresh
 * token from the HttpOnly [RefreshTokenCookie] (there is NO request body),
 * revokes it server-side so the session can no longer be refreshed, and clears
 * the cookie — "logged out" means the persisted session is actually invalidated
 * AND the client no longer holds a cookie. Idempotent: revoking an unknown or
 * already-revoked token (or receiving no cookie at all) still returns 204, so
 * the endpoint never reveals whether a refresh token was ever valid.
 */
@RestController
@RequestMapping("/v1/auth/logout")
class LogoutController(private val logoutSession: LogoutSession) {

    @PostMapping
    @Operation(summary = "Log out", description = "Revokes the refresh token presented in the HttpOnly cookie so it can no longer be used to obtain a fresh access token, and clears the cookie. Idempotent: always succeeds regardless of whether the token was ever valid.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Refresh token revoked; cookie cleared"),
        ],
    )
    fun logout(
        @CookieValue(value = RefreshTokenCookie.NAME, required = false) refreshToken: String?,
    ): ResponseEntity<Void> {
        if (!refreshToken.isNullOrBlank()) {
            logoutSession.execute(refreshToken)
        }
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
            .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.clear())
            .build()
    }
}