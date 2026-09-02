package com.tahirslist.domain.restaurant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CrossContaminationTest : FunSpec({

    test("NO_CROSS_CONTAMINATION is the only index-qualified state") {
        CrossContamination.NO_CROSS_CONTAMINATION.isIndexQualified() shouldBe true
        CrossContamination.PRESENT.isIndexQualified() shouldBe false
        CrossContamination.UNCERTAIN.isIndexQualified() shouldBe false
    }
})