package app.halal.web.account

import app.halal.application.account.InvalidCredentialsException
import app.halal.application.account.RefreshSession
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
 * Refresh (sc-40) endpoint. Rotates a presented refresh token: the old token is
 * revoked (single-use) and a fresh access + refresh pair is issued. Unknown,
 * expired, or already-rotated tokens are rejected with a generic 401.
 */
@RestController
@RequestMapping("/v1/auth/refresh")
class RefreshController(private val refreshSession: RefreshSession) {

    @PostMapping
    @Operation(summary = "Refresh the session", description = "Rotates a refresh token: revokes it and issues a fresh access token + new refresh token.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Rotated; new token pair issued", content = [Content(schema = Schema(implementation = AuthResponse::class))]),
            ApiResponse(responseCode = "401", description = "Invalid, expired, or already-used refresh token"),
            ApiResponse(responseCode = "400", description = "Invalid input", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun refresh(@Valid @RequestBody request: RefreshRequest): ResponseEntity<AuthResponse> {
        val session = refreshSession.execute(request.refreshToken)
        return ResponseEntity.ok(AuthResponse.from(session))
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    @ApiResponse(responseCode = "401", description = "Invalid refresh token")
    fun onInvalidCredentials(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse("invalid_credentials", "Invalid email or password."))

    @ExceptionHandler(IllegalArgumentException::class)
    @ApiResponse(responseCode = "400", description = "Invalid input")
    fun onInvalidInput(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse("invalid_input", ex.message))

    data class RefreshRequest(
        @field:NotBlank(message = "refreshToken is required")
        val refreshToken: String,
    )
}