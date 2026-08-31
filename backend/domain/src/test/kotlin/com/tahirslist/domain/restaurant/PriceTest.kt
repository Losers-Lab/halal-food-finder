package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class PriceTest : FunSpec({

    test("Price stores a positive value") {
        Price(BigDecimal("15.50")).value shouldBe BigDecimal("15.50")
    }

    test("Price rejects zero and negative values") {
        shouldThrow<IllegalArgumentException> { Price(BigDecimal.ZERO) }
        shouldThrow<IllegalArgumentException> { Price(BigDecimal("-1")) }
    }

    test("Price rejects values above the upper bound") {
        shouldThrow<IllegalArgumentException> { Price(Price.MAX + BigDecimal.ONE) }
    }
})