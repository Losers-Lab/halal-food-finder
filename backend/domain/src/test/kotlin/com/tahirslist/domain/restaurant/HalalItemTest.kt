package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class HalalItemTest : FunSpec({

    test("HalalItem carries a name and halal flag") {
        val item = HalalItem(name = "chicken tikka", isHalal = true)
        item.name shouldBe "chicken tikka"
        item.isHalal shouldBe true
    }

    test("HalalItem rejects a blank item name") {
        shouldThrow<IllegalArgumentException> { HalalItem(name = "   ", isHalal = true) }
        shouldThrow<IllegalArgumentException> { HalalItem(name = "", isHalal = true) }
    }

    test("HalalItem trims the item name") {
        HalalItem(name = "  lamb karahi  ", isHalal = false).name shouldBe "lamb karahi"
    }
})