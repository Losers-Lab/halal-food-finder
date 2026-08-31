package com.tahirslist.web.account

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.http.HttpStatus

/**
 * sc-134 error handling (security finding #6): a generic global error handler
 * must NEVER echo a raw exception message or allow stack traces / secrets to
 * reach the response body. Unknown exceptions yield a generic envelope, while
 * the actual cause goes only to the server-side log.
 */
class GlobalErrorHandlerTest : FunSpec({

    val handler = GlobalErrorHandler()

    test("an unexpected IllegalArgumentException never leaks its raw message") {
        val sensitive = IllegalArgumentException("Database password 'hunter2-secret' rejected")
        val resp = handler.onUnexpectedException(sensitive)

        resp.statusCode shouldBe HttpStatus.INTERNAL_SERVER_ERROR
        resp.body.shouldNotBeNull()
        resp.body!!.code shouldBe "internal_error"
        resp.body!!.message.shouldNotBeNull()
        resp.body!!.message shouldNotContain "hunter2-secret"
        resp.body!!.message shouldNotContain "IllegalArgumentException"
    }

    test("a validation IllegalArgumentException is answered generically, no raw message") {
        val resp = handler.onInvalidInput(IllegalArgumentException("refreshToken cookie is required"))
        resp.statusCode shouldBe HttpStatus.BAD_REQUEST
        resp.body!!.code shouldBe "invalid_input"
        resp.body!!.message shouldNotContain "refreshToken cookie is required"
    }
})