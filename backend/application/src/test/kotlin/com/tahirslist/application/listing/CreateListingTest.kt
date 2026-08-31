package com.tahirslist.application.listing

import com.tahirslist.application.account.AccountRepository
import com.tahirslist.domain.account.Account
import com.tahirslist.domain.account.Email
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.CuttingMethod
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID

class CreateListingTest : FunSpec({

    // Fresh mocks per test: mockk call counts are cumulative across a spec, so a
    // shared instance would make `verify(exactly = 0)` order-dependent.
    lateinit var listings: RestaurantListingRepository
    lateinit var accounts: AccountRepository
    lateinit var createListing: CreateListing

    beforeTest {
        listings = mockk()
        accounts = mockk()
        createListing = CreateListing(listings = listings, accounts = accounts)
    }

    fun registeredOwner(ownerId: UUID = UUID.randomUUID()): UUID {
        every { accounts.findById(ownerId) } returns
            Account.new(email = Email("owner@example.com"), passwordHash = "argon2id\$hash")
        return ownerId
    }

    test("creates and persists an unverified listing") {
        val ownerId = registeredOwner()
        every { listings.save(any()) } answers { firstArg() }

        val listing = createListing.execute(
            name = "Halal Grill",
            address = "123 Main St",
            location = LatLng(40.7128, -74.0060),
            cuisine = Cuisine("mediterranean"),
            cuttingMethod = CuttingMethod.HAND_CUT,
            ownerId = ownerId,
        )

        listing.name shouldBe "Halal Grill"
        listing.verificationStatus shouldBe VerificationStatus.UNVERIFIED
        verify { listings.save(match { it.ownerId == ownerId && it.location == LatLng(40.7128, -74.0060) }) }
        // Exactly one owner lookup and one save — no re-checking, no double-writes.
        verify(exactly = 1) { accounts.findById(ownerId) }
        verify(exactly = 1) { listings.save(any()) }
    }

    test("trims name and address before persistence") {
        val ownerId = registeredOwner()
        every { listings.save(any()) } answers { firstArg() }

        createListing.execute(
            name = "  Halal Grill  ",
            address = "  123 Main St  ",
            location = LatLng(1.0, 2.0),
            cuisine = Cuisine("x"),
            cuttingMethod = CuttingMethod.MACHINE_CUT,
            ownerId = ownerId,
        )

        verify { listings.save(match { it.name == "Halal Grill" && it.address == "123 Main St" }) }
    }

    test("passes an explicit alcoholServed flag to the saved listing") {
        val ownerId = registeredOwner()
        every { listings.save(any()) } answers { firstArg() }

        val listing = createListing.execute(
            name = "Halal Grill",
            address = "123 Main St",
            location = LatLng(1.0, 2.0),
            cuisine = Cuisine("x"),
            cuttingMethod = CuttingMethod.HAND_CUT,
            ownerId = ownerId,
            alcoholServed = true,
        )

        listing.alcoholServed shouldBe true
        verify { listings.save(match { it.alcoholServed }) }
    }

    test("defaults alcoholServed to false when not supplied") {
        val ownerId = registeredOwner()
        every { listings.save(any()) } answers { firstArg() }

        val listing = createListing.execute(
            name = "Halal Grill",
            address = "123 Main St",
            location = LatLng(1.0, 2.0),
            cuisine = Cuisine("x"),
            cuttingMethod = CuttingMethod.HAND_CUT,
            ownerId = ownerId,
        )

        listing.alcoholServed shouldBe false
        verify { listings.save(match { !it.alcoholServed }) }
    }

    test("rejects a blank name before touching the owner or repository") {
        every { accounts.findById(any()) } returns null
        every { listings.save(any()) } answers { firstArg() }

        shouldThrow<IllegalArgumentException> {
            createListing.execute(
                name = "   ",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = UUID.randomUUID(),
            )
        }
        verify(exactly = 0) { listings.save(any()) }
        verify(exactly = 0) { accounts.findById(any()) }
    }

    test("rejects a blank address before any owner lookup or save") {
        every { listings.save(any()) } answers { firstArg() }

        shouldThrow<IllegalArgumentException> {
            createListing.execute(
                name = "Halal Grill",
                address = "   ",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = UUID.randomUUID(),
            )
        }
        verify(exactly = 0) { listings.save(any()) }
        verify(exactly = 0) { accounts.findById(any()) }
    }

    test("requires the owning account to exist") {
        val ownerId = UUID.randomUUID()
        every { accounts.findById(ownerId) } returns null // unknown owner
        every { listings.save(any()) } answers { firstArg() }

        val ex = shouldThrow<ListingOwnerNotFoundException> {
            createListing.execute(
                name = "Ghost Kitchen",
                address = "123 Main St",
                location = LatLng(1.0, 2.0),
                cuisine = Cuisine("x"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = ownerId,
            )
        }

        ex.ownerId shouldBe ownerId
        verify(exactly = 0) { listings.save(any()) }
    }
})
