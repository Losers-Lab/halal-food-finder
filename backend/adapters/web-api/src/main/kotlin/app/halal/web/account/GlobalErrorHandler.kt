package app.halal.web.account

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.validation.ConstraintViolationException

/**
 * sc-134 error handling (security finding #6) — ratified whitelist by Adnan.
 *
 * A single global advice guarantees that NO raw exception message, stack trace,
 * or secret ever reaches a response body UNLESS the message is an explicitly-
 * allowable client-facing validation detail.
 *
 * Whitelist (all -> 400 `invalid_input`):
 *  - [MethodArgumentNotValidException]  — bean-validation on `@Valid` DTOs;
 *    per-field messages are preserved.
 *  - [ConstraintViolationException]     — validation on method params;
 *    per-field messages are preserved.
 *  - [HttpMessageNotReadableException]  — unreadable/malformed JSON body.
 *  - [IllegalArgumentException]         — other invalid client input.
 * Everything else -> 500 `internal_error`, message fixed, cause logged only.
 *
 * Endpoints with a business-approved envelope (ratified signup 409 /
 * weak-password 422 / invalid-credentials 401) keep their OWN `@ExceptionHandler`
 * for that exact exception type, which Spring resolves in preference here.
 */
@RestControllerAdvice
class GlobalErrorHandler {

    private val log = LoggerFactory.getLogger(GlobalErrorHandler::class.java)

    /** Bean validation on `@Valid` request-body DTOs; per-field messages preserved. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun onBeanValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        log.debug("Bean validation rejected input", ex)
        val detail = fieldMessages(ex)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("invalid_input", detail))
    }

    /** Validation on method/param constraints; per-field messages preserved. */
    @ExceptionHandler(ConstraintViolationException::class)
    fun onConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        log.debug("Constraint validation rejected input", ex)
        val detail = ex.constraintViolations.joinToString("; ") { it.message }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("invalid_input", detail))
    }

    /** Malformed / unreadable request body (e.g. invalid JSON). */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun onNotReadable(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        log.debug("Unreadable request body rejected", ex)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("invalid_input", "Request body is not readable."))
    }

    /** Other invalid client input: generic, never echo the raw reason. */
    @ExceptionHandler(IllegalArgumentException::class)
    fun onInvalidInput(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        log.debug("Invalid input rejected", ex)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("invalid_input", "Invalid input."))
    }

    /** Unexpected failure: fixed generic envelope; full cause is logged only. */
    @ExceptionHandler(Exception::class)
    fun onUnexpectedException(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unhandled exception; returning generic internal_error", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("internal_error", "An unexpected server error occurred."))
    }

    /** Concatenates field errors from a bean-validation failure (client-facing, safe). */
    private fun fieldMessages(ex: MethodArgumentNotValidException): String =
        ex.bindingResult.fieldErrors.joinToString("; ") { it.defaultMessage ?: "Invalid value" }
}