package com.tahirslist.domain.restaurant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import java.math.BigDecimal
import java.util.UUID

class RestaurantListingTest : FunSpec({

    test("new() creates an unverified listing with the given fields") {
        val owner = UUID.randomUUID()
        val listing = RestaurantListing.new(
            name = "  Halal Grill  ",
            address = " 123 Main St  ",
            location = LatLng(40.7128, -74.0060),
            cuisine = Cuisine("Mediterranean"),
            isHandCut = true,
            ownerId = owner,
        )

        listing.name shouldBe "Halal Grill"                // trimmed
        listing.address shouldBe "123 Main St"             // trimmed
        listing.location shouldBe LatLng(40.7128, -74.0060)
        listing.cuisine shouldBe Cuisine("Mediterranean")
        listing.isHandCut shouldBe true
        listing.ownerId shouldBe owner
        listing.verificationStatus shouldBe VerificationStatus.UNVERIFIED // listing-first model
    }

    test("new() defaults isHandCut to null (unknown / not claimed)") {
        // sc-42: hand-cut is an EXTRA boolean, not an either/or choice. A new
        // listing that does not claim hand-cut records null (unknown) by default.
        val listing = RestaurantListing.new(
            name = "Plain Grill",
            address = "9 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            ownerId = UUID.randomUUID(),
        )
        listing.isHandCut shouldBe null
    }

    test("isHandCut is a plain boolean tri-state, not a cutting-method enum") {
        // sc-42: there is no machine-cut concept — only hand-cut, not-hand-cut,
        // or unknown(null). Assert all three states on the domain model.
        fun build(handCut: Boolean?) = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = handCut,
            ownerId = UUID.randomUUID(),
        )
        build(true).isHandCut shouldBe true
        build(false).isHandCut shouldBe false
        build(null).isHandCut shouldBe null
    }

    test("new() assigns a random id and a createdAt timestamp") {
        val a = RestaurantListing.new(
            name = "A", address = "1 St", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), isHandCut = false, ownerId = UUID.randomUUID(),
        )
        val b = RestaurantListing.new(
            name = "B", address = "2 St", location = LatLng(1.0, 1.0),
            cuisine = Cuisine("x"), isHandCut = false, ownerId = UUID.randomUUID(),
        )

        a.id shouldNotBe b.id
        a.createdAt shouldNotBe null
    }

    test("new() carries an optional price") {
        val listing = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = true,
            ownerId = UUID.randomUUID(),
            price = Price(BigDecimal("9.99")),
        )

        listing.price?.value shouldBe BigDecimal("9.99")
    }

    test("new() carries an optional rating") {
        val listing = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = true,
            ownerId = UUID.randomUUID(),
            rating = Rating(BigDecimal("4.5")),
        )

        listing.rating?.value shouldBe BigDecimal("4.5")
        // Default is no rating (null).
        RestaurantListing.new(
            name = "Y", address = "2 St", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), isHandCut = false, ownerId = UUID.randomUUID(),
        ).rating shouldBe null
    }

    test("new() defaults alcoholServed to false") {
        val listing = RestaurantListing.new(
            name = "Y", address = "2 St", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), isHandCut = false, ownerId = UUID.randomUUID(),
        )
        listing.alcoholServed shouldBe false
    }

    test("new() carries an explicit alcoholServed flag") {
        val listing = RestaurantListing.new(
            name = "X",
            address = "1 St",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            isHandCut = true,
            ownerId = UUID.randomUUID(),
            alcoholServed = true,
        )
        listing.alcoholServed shouldBe true
    }

    test("new() rejects a blank name") {
        shouldThrow<IllegalArgumentException> {
            RestaurantListing.new(
                name = "   ",
                address = "1 St",
                location = LatLng(0.0, 0.0),
                cuisine = Cuisine("x"),
                isHandCut = true,
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
                isHandCut = true,
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

    test("Provenance accepts the closed seed vocabulary") {
        Provenance(" research-seed ").value shouldBe "research-seed"
        Provenance("photon-geocode") shouldBe Provenance.PHOTON_GEOCODE
        Provenance("research-seed / photon-geocode") shouldBe Provenance.RESEARCH_SEED_PHOTON_GEOCODE
    }

    test("Provenance rejects an out-of-vocabulary value") {
        shouldThrow<IllegalArgumentException> { Provenance("some-other-source") }
        shouldThrow<IllegalArgumentException> { Provenance("") }
    }

    test("fromStorage materialises a seed-style row with nullable cuisine/owner and provenance") {
        val seedId = UUID.randomUUID()
        val brandId = UUID.randomUUID()
        val listing = RestaurantListing.fromStorage(
            id = seedId,
            name = "The Halal Guys",
            address = "307 E 14th St",
            location = LatLng(40.732288, -73.984423),
            cuisine = null,
            isHandCut = null,
            ownerId = null,
            brandId = brandId,
            provenance = Provenance.RESEARCH_SEED_PHOTON_GEOCODE,
            verificationStatus = VerificationStatus.UNVERIFIED,
            createdAt = java.time.Instant.now(),
        )

        listing.id shouldBe seedId
        listing.cuisine shouldBe null
        listing.ownerId shouldBe null
        listing.brandId shouldBe brandId
        listing.provenance shouldBe Provenance.RESEARCH_SEED_PHOTON_GEOCODE
    }

    test("fromStorage reconstitutes the alcoholServed flag") {
        val listing = RestaurantListing.fromStorage(
            id = UUID.randomUUID(),
            name = "Steak House",
            address = "1 Grill Ave",
            location = LatLng(40.0, -74.0),
            cuisine = null,
            isHandCut = true,
            ownerId = null,
            brandId = null,
            provenance = null,
            verificationStatus = VerificationStatus.UNVERIFIED,
            createdAt = java.time.Instant.now(),
            alcoholServed = true,
        )

        listing.alcoholServed shouldBe true
    }
})
