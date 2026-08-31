package com.tahirslist.web.account

import com.tahirslist.application.account.CreateAccount
import com.tahirslist.application.account.EmailAlreadyExistsException
import com.tahirslist.application.account.WeakPasswordException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Create Account (sc-39) endpoint. Accepts an email + password, hashes the
 * password with Argon2id, persists the account with the default role, and
 * returns the created account. Never echoes the password.
 */
@RestController
@RequestMapping("/v1/auth/signup")
class SignupController(private val createAccount: CreateAccount) {

    @PostMapping
    @Operation(summary = "Create an account", description = "Validates the email/password, hashes the password with Argon2id, persists the account with the default role, and returns the created account.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Account created", content = [Content(schema = Schema(implementation = SignupResponse::class))]),
            ApiResponse(responseCode = "409", description = "Email already exists"),
            ApiResponse(responseCode = "422", description = "Password is too weak"),
            ApiResponse(responseCode = "400", description = "Invalid input"),
        ],
    )
    fun signup(@Valid @RequestBody request: SignupRequest): ResponseEntity<SignupResponse> {
        val account = createAccount.execute(
            emailRaw = request.email,
            password = request.password,
        )
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(SignupResponse(id = account.id, email = account.email.value, role = account.role.name))
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    @ApiResponse(responseCode = "409", description = "Email already exists")
    fun onEmailTaken(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("email_already_exists"))

    @ExceptionHandler(WeakPasswordException::class)
    @ApiResponse(responseCode = "422", description = "Password is too weak")
    fun onWeakPassword(ex: WeakPasswordException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(ErrorResponse("weak_password", ex.message))

    data class SignupRequest(
        @field:NotBlank(message = "email is required")
        @field:Email(message = "email must be a valid address")
        val email: String,

        @field:NotBlank(message = "password is required")
        val password: String,
    )

    data class SignupResponse(val id: UUID, val email: String, val role: String)
}