package com.tahirslist.domain.account

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AccountTest : FunSpec({

    test("creating a new account assigns the USER role by default") {
        val account = Account.new(email = Email("a@example.com"), passwordHash = "hash")

        account.role shouldBe Role.USER
    }

    test("new account carries the email and password hash it was created with") {
        val account = Account.new(email = Email("a@example.com"), passwordHash = "argon2id-hash")

        account.email.value shouldBe "a@example.com"
        account.passwordHash shouldBe "argon2id-hash"
    }

    test("the persisted form of an account can restore a non-default role") {
        val account = Account.fromStorage(
            id = java.util.UUID.randomUUID(),
            email = Email("a@example.com"),
            passwordHash = "hash",
            role = Role.RESTAURANT_OWNER,
        )

        account.role shouldBe Role.RESTAURANT_OWNER
    }
})