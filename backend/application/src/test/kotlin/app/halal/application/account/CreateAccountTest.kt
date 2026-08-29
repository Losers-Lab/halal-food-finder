package app.halal.application.account

import app.halal.domain.account.Email
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class CreateAccountTest : FunSpec({

    val repository = mockk<AccountRepository>()
    val hasher = mockk<PasswordHasher>()

    val createAccount = CreateAccount(repository = repository, hasher = hasher)

    test("creates no account when the email is already taken") {
        every { repository.findByEmail(Email("taken@example.com")) } returns
            AccountFixture.someAccount(email = "taken@example.com")
        every { repository.save(any()) } returns AccountFixture.someAccount()

        val ex = shouldThrow<EmailAlreadyExistsException> { createAccount.execute("taken@example.com", "password123") }

        ex.email.value shouldBe "taken@example.com"
        verify(exactly = 0) { repository.save(any()) }
        verify(exactly = 0) { hasher.hash(any()) }
    }

    test("rejects a weak password before hashing or saving") {
        every { repository.findByEmail(any()) } returns null
        every { hasher.hash(any()) } returns "hash"
        every { repository.save(any()) } returns AccountFixture.someAccount()

        shouldThrow<WeakPasswordException> { createAccount.execute("a@example.com", "tiny") }

        verify(exactly = 0) { hasher.hash(any()) }
        verify(exactly = 0) { repository.save(any()) }
    }

    test("hashes the password, never persisting it plaintext") {
        every { repository.findByEmail(any()) } returns null
        every { hasher.hash("password123") } returns "argon2id\$abc"
        every { repository.save(any()) } answers { firstArg() }

        createAccount.execute("a@example.com", "password123")

        verify { hasher.hash("password123") }
        verify { repository.save(match { it.passwordHash == "argon2id\$abc" }) }
    }

    test("persists the account with the default USER role") {
        every { repository.findByEmail(any()) } returns null
        every { hasher.hash(any()) } returns "argon2id\$abc"
        every { repository.save(any()) } answers { firstArg() }

        createAccount.execute("a@example.com", "password123")

        verify { repository.save(match { it.role.name == "USER" }) }
    }

    test("normalises the email to lowercase before persistence") {
        every { repository.findByEmail(any()) } returns null
        every { hasher.hash(any()) } returns "hash"
        every { repository.save(any()) } answers { firstArg() }

        createAccount.execute("A.USer@Example.COM", "password123")

        verify { repository.save(match { it.email.value == "a.user@example.com" }) }
    }
})

object AccountFixture {
    fun someAccount(email: String = "a@example.com") =
        app.halal.domain.account.Account.new(
            email = Email(email),
            passwordHash = "argon2id\$some-hash",
        )
}