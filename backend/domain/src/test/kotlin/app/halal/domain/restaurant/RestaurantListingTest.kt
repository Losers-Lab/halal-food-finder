package app.halal.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import java.util.UUID

class RestaurantListingTest : FunSpec({

    test("new() creates an unverified listing with the given fields") {
        val owner = UUID.randomUUID()
        val listing = RestaurantListing.new(
            name = "  Halal Grill  ",
            address = " 123 Main St  ",
            location = LatLng(40.7128, -74.0060),
            cuisine = Cuisine("Mediterranean"),
            cuttingMethod = CuttingMethod.HAND_CUT,
            ownerId = owner,
        )

        listing.name shouldBe "Halal Grill"                // trimmed
        listing.address shouldBe "123 Main St"             // trimmed
        listing.location shouldBe LatLng(40.7128, -74.0060)
        listing.cuisine shouldBe Cuisine("Mediterranean")
        listing.cuttingMethod shouldBe CuttingMethod.HAND_CUT
        listing.ownerId shouldBe owner
        listing.verificationStatus shouldBe VerificationStatus.UNVERIFIED // listing-first model
    }

    test("new() assigns a random id and a createdAt timestamp") {
        val a = RestaurantListing.new(
            name = "A", address = "1 St", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), cuttingMethod = CuttingMethod.MACHINE_CUT, ownerId = UUID.randomUUID(),
        )
        val b = RestaurantListing.new(
            name = "B", address = "2 St", location = LatLng(1.0, 1.0),
            cuisine = Cuisine("x"), cuttingMethod = CuttingMethod.MACHINE_CUT, ownerId = UUID.randomUUID(),
        )

        a.id shouldNotBe b.id
        a.createdAt shouldNotBe null
    }

    test("new() rejects a blank name") {
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.new(
                name = "   ",
                address = "1 St",
                location = LatLng(0.0, 0.0),
                cuisine = Cuisine("x"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = UUID.randomUUID(),
            )
        }
    }

    test("new() rejects a blank address") {
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.new(
                name = "Name",
                address = "",
                location = LatLng(0.0, 0.0),
                cuisine = Cuisine("x"),
                cuttingMethod = CuttingMethod.HAND_CUT,
                ownerId = UUID.randomUUID(),
            )
        }
    }

    test("Cuisine rejects blank and over-long values") {
        shouldThrow<IllegalArgumentException> { Cuisine("   ") }
        shouldThrow<IllegalArgumentException> { Cuisine("c".repeat(65)) }
    }

    test("LatLng validates latitude and longitude bounds") {
        shouldThrow<IllegalArgumentException> { LatLng(91.0, 0.0) }
        shouldThrow<IllegalArgumentException> { LatLng(-91.0, 0.0) }
        shouldThrow<IllegalArgumentException> { LatLng(0.0, 181.0) }
        shouldThrow<IllegalArgumentException> { LatLng(0.0, -181.0) }
        // Boundaries are permitted.
        LatLng(90.0, -180.0).lat shouldBe 90.0
    }

    test("Cuisine normalises to trimmed, lowercase value") {
        Cuisine(" Mediterranean ").value shouldBe "mediterranean"
    }
})
