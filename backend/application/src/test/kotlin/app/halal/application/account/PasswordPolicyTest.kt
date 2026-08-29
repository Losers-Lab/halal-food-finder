package app.halal.application.account

import app.halal.application.account.PasswordPolicy
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf

class PasswordPolicyTest : FunSpec({

    test("accepts a password at the minimum strength") {
        shouldNotThrow<RuntimeException> { PasswordPolicy.validate("password123") }
    }

    test("rejects a password that is too short") {
        shouldThrow<WeakPasswordException> { PasswordPolicy.validate("short") }
            .shouldBeInstanceOf<WeakPasswordException>()
    }

    test("rejects a blank password") {
        shouldThrow<WeakPasswordException> { PasswordPolicy.validate("   ") }
    }
})