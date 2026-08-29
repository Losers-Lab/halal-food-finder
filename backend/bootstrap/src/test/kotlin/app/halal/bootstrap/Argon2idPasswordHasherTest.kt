package app.halal.bootstrap

import app.halal.application.account.PasswordHasher
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class Argon2idPasswordHasherTest : FunSpec({

    val hasher: PasswordHasher = Argon2idPasswordHasher()

    test("produced hash is not the plaintext password") {
        val hash = hasher.hash("s3cr3t-password")
        hash shouldNotBe "s3cr3t-password"
    }

    test("hash verifies against the original password") {
        val hash = hasher.hash("s3cr3t-password")
        hasher.verify("s3cr3t-password", hash) shouldBe true
    }

    test("hash does not verify against a different password") {
        val hash = hasher.hash("s3cr3t-password")
        hasher.verify("wrong-password", hash) shouldBe false
    }

    test("the same password produces a different hash each time (salted)") {
        val hash1 = hasher.hash("s3cr3t-password")
        val hash2 = hasher.hash("s3cr3t-password")
        hash1 shouldNotBe hash2
    }
})