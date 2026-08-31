package com.tahirslist.web.account

import com.tahirslist.application.account.InvalidCredentialsException
import com.tahirslist.application.account.RefreshSession
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Refresh (sc-40) endpoint, migrated to the sc-133 cookie contract. It reads the
 * refresh token from the HttpOnly [RefreshTokenCookie] (there is NO request
 * body) and rotates it: the old token is revoked (single-use) and a fresh access
 * token + new refresh cookie is issued. Unknown, expired, or already-rotated
 * tokens are rejected with a generic 401, and the stale cookie is cleared.
 */
@RestController
@RequestMapping("/v1/auth/refresh")
class RefreshController(private val refreshSession: RefreshSession) {

    @PostMapping
    @Operation(summary = "Refresh the session", description = "Rotates the refresh token presented in the HttpOnly cookie: revokes it and issues a fresh access token in the body plus a new refresh cookie.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Rotated; new access token + refresh cookie issued", content = [Content(schema = Schema(implementation = AuthResponse::class))]),
            ApiResponse(responseCode = "401", description = "Invalid, expired, or already-used refresh token"),
            ApiResponse(responseCode = "400", description = "Missing refresh cookie", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun refresh(
        @CookieValue(value = RefreshTokenCookie.NAME, required = false) refreshToken: String?,
    ): ResponseEntity<AuthResponse> {
        if (refreshToken.isNullOrBlank()) {
            throw IllegalArgumentException("refreshToken cookie is required")
        }
        val session = refreshSession.execute(refreshToken)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.issue(session.tokens.refreshToken))
            .body(AuthResponse.from(session))
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    fun onInvalidCredentials(response: HttpServletResponse): ResponseEntity<ErrorResponse> {
        // The presented cookie is dead (expired/rotated/unknown): clear it so the
        // browser stops resubmitting a token that can never succeed.
        response.addHeader(HttpHeaders.SET_COOKIE, RefreshTokenCookie.clear())
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse("invalid_credentials", "Invalid email or password."))
    }
}