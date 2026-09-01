package com.tahirslist.domain.restaurant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.util.UUID

/**
 * Domain modelling for sc-119 partial-halal. Covers the founder re-scope:
 *  - per-item halal scope (halalScope + halalItems), independent of verification;
 *  - cross-contamination as a listing attribute gating index qualification;
 *  - boolean hand-cut (cuttingMethod/CuttingMethod removed).
 */
class RestaurantListingPartialHalalTest : FunSpec({

    val owner = UUID.randomUUID()

    fun base() = RestaurantListing.new(
        name = "Mixed Grill",
        address = "1 Main St",
        location = LatLng(40.0, -74.0),
        cuisine = Cuisine("mediterranean"),
        ownerId = owner,
    )

    test("new() carries a boolean handCut and defaults to unspecified (null)") {
        val unspecified = base()
        unspecified.handCut shouldBe null

        val handCut = RestaurantListing.new(
            name = "A", address = "1", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), ownerId = owner, handCut = true,
        )
        handCut.handCut shouldBe true

        val notHandCut = RestaurantListing.new(
            name = "B", address = "2", location = LatLng(1.0, 1.0),
            cuisine = Cuisine("x"), ownerId = owner, handCut = false,
        )
        notHandCut.handCut shouldBe false
    }

    test("new() defaults halalScope to NOT_DISCLOSED and halalItems to empty") {
        val listing = base()
        listing.halalScope shouldBe HalalScope.NOT_DISCLOSED
        listing.halalItems shouldBe emptySet()
    }

    test("new() carries an explicit per-item halal scope") {
        val listing = RestaurantListing.new(
            name = "Partial Place",
            address = "1",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            ownerId = owner,
            halalScope = HalalScope.PARTIALLY_HALAL,
            halalItems = setOf(HalalItem("chicken", true), HalalItem("bacon", false)),
        )
        listing.halalScope shouldBe HalalScope.PARTIALLY_HALAL
        listing.halalItems shouldBe setOf(HalalItem("chicken", true), HalalItem("bacon", false))
    }

    test("verification is independent of halal scope: a partial place can be verified") {
        // Build a PARTIALLY_HALAL listing and promote it to VERIFIED — the founder
        // decision is that a partial-halal place CAN still be verified (it may hold
        // a certificate). This is the "verification-compatible-with-partial" rule.
        val partial = RestaurantListing.new(
            name = "Partial",
            address = "1",
            location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"),
            ownerId = owner,
            halalScope = HalalScope.PARTIALLY_HALAL,
            halalItems = setOf(HalalItem("chicken", true), HalalItem("beef - non-zabiha", false)),
        ).copy(verificationStatus = VerificationStatus.VERIFIED)

        partial.verificationStatus shouldBe VerificationStatus.VERIFIED
        partial.halalScope shouldBe HalalScope.PARTIALLY_HALAL
    }

    test("new() defaults crossContamination to UNCERTAIN (conservative index gate)") {
        base().crossContamination shouldBe CrossContamination.UNCERTAIN
        base().crossContamination.isIndexQualified() shouldBe false
    }

    test("new() carries an explicit crossContamination qualification") {
        val qualified = RestaurantListing.new(
            name = "Clean", address = "1", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), ownerId = owner,
            crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
        )
        qualified.crossContamination.isIndexQualified() shouldBe true

        val present = RestaurantListing.new(
            name = "Cross", address = "1", location = LatLng(0.0, 0.0),
            cuisine = Cuisine("x"), ownerId = owner,
            crossContamination = CrossContamination.PRESENT,
        )
        present.crossContamination.isIndexQualified() shouldBe false
    }

    test("fromStorage round-trips handCut, halalScope, halalItems and crossContamination") {
        val id = UUID.randomUUID()
        val now = java.time.Instant.now()
        val listing = RestaurantListing.fromStorage(
            id = id,
            name = "Stored",
            address = "1",
            location = LatLng(40.0, -74.0),
            cuisine = Cuisine("x"),
            ownerId = owner,
            brandId = null,
            provenance = null,
            verificationStatus = VerificationStatus.UNVERIFIED,
            createdAt = now,
            handCut = true,
            halalScope = HalalScope.PARTIALLY_HALAL,
            halalItems = setOf(HalalItem("lamb", true)),
            crossContamination = CrossContamination.NO_CROSS_CONTAMINATION,
        )
        listing.id shouldBe id
        listing.handCut shouldBe true
        listing.halalScope shouldBe HalalScope.PARTIALLY_HALAL
        listing.halalItems shouldBe setOf(HalalItem("lamb", true))
        listing.crossContamination shouldBe CrossContamination.NO_CROSS_CONTAMINATION
    }

    // Founder decision #4: hand-cut is a boolean, cuttingMethod/CuttingMethod are
    // gone. This body references only handCut (Boolean?) — there is no
    // CuttingMethod type anymore, so the file cannot compile against it.
    test("handCut is a nullable boolean on the listing, not a cutting-method enum") {
        RestaurantListing.new(
            name = "C", address = "3", location = LatLng(2.0, 2.0),
            cuisine = Cuisine("x"), ownerId = owner, handCut = true,
        ).handCut shouldBe true
    }
})