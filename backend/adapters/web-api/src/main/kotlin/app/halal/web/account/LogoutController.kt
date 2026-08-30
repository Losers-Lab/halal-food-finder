package app.halal.web.account

import app.halal.application.account.LogoutSession
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Log Out (sc-132) endpoint. Revokes the presented refresh token server-side so
 * the session can no longer be refreshed — "logged out" means the persisted
 * session is actually invalidated, not just cleared on the client. Idempotent:
 * revoking an unknown or already-revoked token still returns 204, so the
 * endpoint never reveals whether a refresh token was ever valid.
 */
@RestController
@RequestMapping("/v1/auth/logout")
class LogoutController(private val logoutSession: LogoutSession) {

    @PostMapping
    @Operation(summary = "Log out", description = "Revokes the presented refresh token so it can no longer be used to obtain a fresh access token. Idempotent: always succeeds regardless of whether the token was ever valid.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Refresh token revoked"),
            ApiResponse(responseCode = "400", description = "Invalid input", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun logout(@Valid @RequestBody request: LogoutRequest): ResponseEntity<Void> {
        logoutSession.execute(request.refreshToken)
        return ResponseEntity.noContent().build()
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ApiResponse(responseCode = "400", description = "Invalid input")
    fun onInvalidInput(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("invalid_input", ex.message))

    data class LogoutRequest(
        @field:NotBlank(message = "refreshToken is required")
        val refreshToken: String,
    )
}