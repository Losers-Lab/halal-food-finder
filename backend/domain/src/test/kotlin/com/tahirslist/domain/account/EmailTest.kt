package com.tahirslist.domain.account

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EmailTest : FunSpec({

    test("Email normalizes to lowercase for unique comparison") {
        Email(" User@Example.COM ").value shouldBe "user@example.com"
    }

    test("Email accepts a plain but valid address") {
        Email("a@b.co").value shouldBe "a@b.co"
    }

    test("blank Email is rejected") {
        shouldThrow<IllegalArgumentException> { Email("   ") }
    }

    test("Email without an @ is rejected") {
        shouldThrow<IllegalArgumentException> { Email("not-an-email") }
    }

    test("Email with no domain is rejected") {
        shouldThrow<IllegalArgumentException> { Email("user@") }
    }

    test("Email with no local part is rejected") {
        shouldThrow<IllegalArgumentException> { Email("@example.com") }
    }
})