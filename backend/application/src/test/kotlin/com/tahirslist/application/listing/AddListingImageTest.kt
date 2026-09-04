package com.tahirslist.application.listing

import com.tahirslist.application.favorite.ListingNotFoundException
import com.tahirslist.application.image.ImagePort
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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.imageio.ImageIO

/**
 * sc-53 owner add/replace hero image: [AddListingImage] owner-guards an upload
 * and persists every variant (FULL + sc-183 thumbs) via the [ImagePort].
 * Non-owners get 403 semantics, missing listings 404. The underlying variant
 * generation is already pinned by IngestHeroImageTest; here we pin the
 * authorization flow + that a successful upload overwrites (last-write-wins).
 */
class AddListingImageTest : FunSpec({

    lateinit var listings: RestaurantListingRepository
    lateinit var images: InMemoryImagePort
    lateinit var useCase: AddListingImage

    beforeTest {
        listings = mockk()
        images = InMemoryImagePort()
        useCase = AddListingImage(listings = listings, images = images)
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

    test("owner can set the listing's hero image; FULL + every thumb is stored") {
        val ownerId = UUID.randomUUID()
        val listing = existingListing(ownerId = ownerId)
        every { listings.findById(listing.id) } returns listing

        useCase.execute(listing.id, ownerId, StoredImage(jpeg(), "image/jpeg"))

        val full = images.load(listing.id, ImageVariant.FULL).shouldNotBeNull()
        full.contentType shouldBe "image/jpeg"
        ImageVariant.thumbnailVariants.forEach { images.load(listing.id, it).shouldNotBeNull() }
    }

    test("re-uploading overwrites the earlier hero (last-write-wins)") {
        val ownerId = UUID.randomUUID()
        val listing = existingListing(ownerId = ownerId)
        every { listings.findById(listing.id) } returns listing

        useCase.execute(listing.id, ownerId, StoredImage(jpeg(400, 300), "image/jpeg"))
        val second = jpeg(2000, 1500)
        useCase.execute(listing.id, ownerId, StoredImage(second, "image/jpeg"))

        images.load(listing.id, ImageVariant.FULL)!!.bytes.contentEquals(second) shouldBe true
    }

    test("a non-owner is unauthorized (403) and never persists") {
        val listing = existingListing(ownerId = UUID.randomUUID()) // owner is someone else
        every { listings.findById(listing.id) } returns listing

        val ex = shouldThrow<NotListingOwnerException> {
            useCase.execute(listing.id, UUID.randomUUID(), StoredImage(jpeg(), "image/jpeg"))
        }
        ex.listingId shouldBe listing.id
        images.load(listing.id, ImageVariant.FULL) shouldBe null
    }

    test("unknown listing is not found (404)") {
        val id = UUID.randomUUID()
        every { listings.findById(id) } returns null

        val ex = shouldThrow<ListingNotFoundException> {
            useCase.execute(id, UUID.randomUUID(), StoredImage(jpeg(), "image/jpeg"))
        }
        ex.listingId shouldBe id
        images.load(id, ImageVariant.FULL) shouldBe null
    }

    test("undecodable upload throws before anything is persisted") {
        val ownerId = UUID.randomUUID()
        val listing = existingListing(ownerId = ownerId)
        every { listings.findById(listing.id) } returns listing

        shouldThrow<IllegalArgumentException> {
            useCase.execute(listing.id, ownerId, StoredImage("garbage".toByteArray(), "image/jpeg"))
        }
        images.load(listing.id, ImageVariant.FULL) shouldBe null
        ImageVariant.thumbnailVariants.forEach { images.load(listing.id, it) shouldBe null }
    }
})