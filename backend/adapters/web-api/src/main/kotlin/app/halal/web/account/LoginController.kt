package app.halal.web.account

import app.halal.application.account.AuthenticateAccount
import app.halal.application.account.InvalidCredentialsException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Log In (sc-40) endpoint. Verifies the credentials (generic rejection — the
 * same 401 for an unknown email and a wrong password, so the response is not
 * user-enumeration friendly) and returns a short access token in the JSON body
 * plus a rotating refresh token delivered ONLY as an HttpOnly cookie (sc-133).
 */
@RestController
@RequestMapping("/v1/auth/login")
class LoginController(private val authenticateAccount: AuthenticateAccount) {

    @PostMapping
    @Operation(summary = "Log in", description = "Verifies the email/password against the stored Argon2id hash and returns a short-lived access token (RS256 JWT with the account's RBAC role) in the body; the rotating refresh token is set as an HttpOnly; Secure; SameSite=Lax cookie scoped to the auth routes and is never returned in JSON.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Authenticated; token pair issued", content = [Content(schema = Schema(implementation = AuthResponse::class))]),
            ApiResponse(responseCode = "401", description = "Invalid email or password (generic, no user enumeration)"),
            ApiResponse(responseCode = "400", description = "Invalid input", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ],
    )
    fun login(@Valid @RequestBody request: LoginRequest): ResponseEntity<AuthResponse> {
        val session = authenticateAccount.execute(request.email, request.password)
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.issue(session.tokens.refreshToken))
            .body(AuthResponse.from(session))
    }

    @ExceptionHandler(InvalidCredentialsException::class)
    @ApiResponse(responseCode = "401", description = "Invalid email or password")
    fun onInvalidCredentials(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse("invalid_credentials", "Invalid email or password."))

    data class LoginRequest(
        @field:NotBlank(message = "email is required")
        @field:Email(message = "email must be a valid address")
        val email: String,

        @field:NotBlank(message = "password is required")
        val password: String,
    )
}