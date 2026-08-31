package com.tahirslist.domain.account

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RoleTest : FunSpec({

    test("Role exposes exactly the 6 MVP roles") {
        Role.entries.map { it.name }.toSet() shouldBe setOf(
            "USER",
            "RESTAURANT_OWNER",
            "VERIFIED_RESTAURANT_OWNER",
            "VERIFICATION_COMMITTEE",
            "ISSUES_COMMITTEE",
            "EXTENSION_TRIGGER",
        )
    }

    test("USER is the default role assigned to a newly created account") {
        Role.DEFAULT shouldBe Role.USER
    }
})