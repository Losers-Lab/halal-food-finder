package com.tahirslist.application.favorite

import com.tahirslist.application.listing.RestaurantListingRepository
import com.tahirslist.domain.restaurant.Cuisine
import com.tahirslist.domain.restaurant.LatLng
import com.tahirslist.domain.restaurant.RestaurantListing
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.util.UUID

/** Favorite a listing (sc-50): idempotent add, with a 404-style guard for a missing listing. */
class FavoriteListingTest : FunSpec({

    val favorites = mockk<FavoritesRepository>()
    val listings = mockk<RestaurantListingRepository>()
    val favorite = FavoriteListing(favorites, listings)

    val userId = UUID.randomUUID()
    val listingId = UUID.randomUUID()

    beforeTest { clearMocks(favorites, listings) }

    test("favourites a listing that exists") {
        every { listings.findById(listingId) } returns aListing(listingId)
        every { favorites.add(userId, listingId) } returns Unit

        favorite.execute(userId, listingId)

        verify { favorites.add(userId, listingId) }
    }

    test("throws ListingNotFoundException when the listing does not exist and adds nothing") {
        every { listings.findById(listingId) } returns null

        shouldThrow<ListingNotFoundException> { favorite.execute(userId, listingId) }

        verify(exactly = 0) { favorites.add(any(), any()) }
    }
})

/** Unfavorite a listing (sc-51): idempotent remove. */
class UnfavoriteListingTest : FunSpec({

    val favorites = mockk<FavoritesRepository>()
    val unfavorite = UnfavoriteListing(favorites)

    val userId = UUID.randomUUID()
    val listingId = UUID.randomUUID()

    test("removes the user-listing relation") {
        every { favorites.remove(userId, listingId) } returns Unit

        unfavorite.execute(userId, listingId)

        verify { favorites.remove(userId, listingId) }
    }
})

/** List the user's favourites (sc-52): returns the favourited listing rows. */
class ListFavoritesTest : FunSpec({

    val favorites = mockk<FavoritesRepository>()
    val listFavorites = ListFavorites(favorites)

    val userId = UUID.randomUUID()

    test("returns the authenticated user's favourited listings") {
        val expected = listOf(aListing(UUID.randomUUID()), aListing(UUID.randomUUID()))
        every { favorites.findFavoriteListings(userId) } returns expected

        val result = listFavorites.execute(userId)

        result shouldBe expected
        verify { favorites.findFavoriteListings(userId) }
    }

    test("returns an empty list when the user has no favourites") {
        every { favorites.findFavoriteListings(userId) } returns emptyList()

        listFavorites.execute(userId) shouldBe emptyList()
    }
})

private fun aListing(id: UUID): RestaurantListing = RestaurantListing.fromStorage(
    id = id,
    name = "Halal Grill",
    address = "123 Main St",
    location = LatLng(40.7128, -74.0060),
    cuisine = Cuisine("mediterranean"),
    isHandCut = true,
    ownerId = UUID.randomUUID(),
    brandId = null,
    provenance = null,
    verificationStatus = com.tahirslist.domain.restaurant.VerificationStatus.UNVERIFIED,
    createdAt = Instant.now(),
)