package com.tahirslist.application.listing

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.image.ImageVariant
import com.tahirslist.application.image.InMemoryImagePort
import com.tahirslist.application.image.StoredImage
import com.tahirslist.application.verification.NotListingOwnerException
import com.tahirslist.domain.restaurant.CrossContamination
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.HalalScope
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import com.tahirslist.domain.restaurant.VerificationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * sc-54 owner remove listing image: [RemoveListingImage] owner-guards a removal
 * and deletes every variant (FULL + sc-183 thumbs). Non-owners get 403
 * semantics, missing listings 404, and removal is idempotent (a listing with no
 * image is not an error — the read surface's placeholder simply remains).
 */
class RemoveListingImageTest : FunSpec({

    lateinit var listings: RestaurantListingRepository
    lateinit var images: InMemoryImagePort
    lateinit var useCase: RemoveListingImage

    beforeTest {
        listings = mockk()
        images = InMemoryImagePort()
        useCase = RemoveListingImage(listings = listings, images = images)
    }

    fun existingListing(
        id: UUID = UUID.randomUUID(),
        ownerId: UUID = UUID.randomUUID(),
    ): RestaurantListing = RestaurantListing.fromStorage(
        id = id,
        name = "Halal Grill",
        address = "123 Main St",
        location = LatLng(40.7128, -74.0060),
        cuisine = Cuisine("mediterranean"),
        isHandCut = true,
        isDelivery = true,
        ownerId = ownerId,
        brandId = null,
        provenance = null,
        verificationStatus = VerificationStatus.UNVERIFIED,
        createdAt = java.time.Instant.parse("2026-01-01T00:00:00Z"),
        halalScope = HalalScope.NOT_DISCLOSED,
        crossContamination = CrossContamination.UNCERTAIN,
    )

    fun jpeg(width: Int = 3000, height: Int = 2000): ByteArray {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color(40, 90, 180)
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "jpeg", out)
        return out.toByteArray()
    }

    fun seedAllVariants(listingId: UUID) {
        images.save(listingId, ImageVariant.FULL, "image/jpeg", jpeg())
        ImageVariant.thumbnailVariants.forEach { images.save(listingId, it, "image/jpeg", jpeg(400, 300)) }
    }

    test("owner can remove the listing's image; every variant is deleted") {
        val ownerId = UUID.randomUUID()
        val listing = existingListing(ownerId = ownerId)
        every { listings.findById(listing.id) } returns listing
        seedAllVariants(listing.id)

        useCase.execute(listing.id, ownerId)

        images.load(listing.id, ImageVariant.FULL) shouldBe null
        ImageVariant.thumbnailVariants.forEach { images.load(listing.id, it) shouldBe null }
    }

    test("removing a listing with no stored image is a silent no-op, not an error") {
        val ownerId = UUID.randomUUID()
        val listing = existingListing(ownerId = ownerId)
        every { listings.findById(listing.id) } returns listing

        useCase.execute(listing.id, ownerId) // must not throw

        images.load(listing.id, ImageVariant.FULL) shouldBe null
    }

    test("a non-owner is unauthorized (403) and never deletes") {
        val ownerId = UUID.randomUUID()
        val listing = existingListing(ownerId = ownerId)
        every { listings.findById(listing.id) } returns listing
        seedAllVariants(listing.id)

        val ex = shouldThrow<NotListingOwnerException> {
            useCase.execute(listing.id, UUID.randomUUID()) // different account
        }
        ex.listingId shouldBe listing.id
        images.load(listing.id, ImageVariant.FULL) shouldNotBe null // untouched
    }

    test("unknown listing is not found (404)") {
        val id = UUID.randomUUID()
        every { listings.findById(id) } returns null

        val ex = shouldThrow<ListingNotFoundException> {
            useCase.execute(id, UUID.randomUUID())
        }
        ex.listingId shouldBe id
    }
})