package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class RatingTest : FunSpec({

    test("Rating accepts the 0..5 scale inclusive") {
        Rating(BigDecimal.ZERO).value shouldBe BigDecimal.ZERO
        Rating(BigDecimal("5.00")).value shouldBe BigDecimal("5.00")
        Rating(BigDecimal("4.5")).value shouldBe BigDecimal("4.5")
    }

    test("Rating rejects a negative value") {
        shouldThrow<IllegalArgumentException> { Rating(BigDecimal("-0.1")) }
    }

    test("Rating rejects a value above 5") {
        shouldThrow<IllegalArgumentException> { Rating(BigDecimal("5.01")) }
    }
})