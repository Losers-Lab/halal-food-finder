import {
  api,
  type VerificationReview,
} from "@/lib/api/client";
import { getRestaurant } from "@/lib/listings/data";
import type { Restaurant } from "@/lib/listings/restaurants";

/**
 * sc-73 Verification Committee review read seam. The backend's pending-workqueue
 * read (GET /v1/verification-committee/reviews) returns ONLY the review
 * aggregate — no listing name/address. The committee decides on the listing,
 * so this seam enriches each review with its resolved listing details via
 * GET /v1/listings/{id}.
 *
 * A review whose listing 404s (deleted between creation and decision) does not
 * kill the whole queue: it is kept with `listing: undefined`, which the UI
 * renders as a de-emphasized "listing unavailable" card — the committee still
 * sees there IS a pending review and can deny it.
 */

/** A pending review enriched with the listing's live details (for the UI). */
export type ReviewWithListing = VerificationReview & {
  /** Resolved listing details; undefined when the listing no longer exists. */
  listing?: Restaurant;
};

/**
 * GET the VC pending queue and enrich each review with its listing details.
 * Listing reads resolve independently (Promise.all) so one missing listing
 * never fails the whole queue/enrichment.
 */
export async function fetchVerificationQueue(): Promise<ReviewWithListing[]> {
  const reviews = await api.getVerificationReviews();
  const withListings = await Promise.all(
    reviews.map(async (review): Promise<ReviewWithListing> => {
      try {
        const listing = await getRestaurant(review.listingId);
        return { ...review, listing };
      } catch {
        // Treat an unresolvable listing as "listing unavailable" rather than
        // dropping its review from the queue (or failing the whole batch).
        return { ...review, listing: undefined };
      }
    }),
  );
  return withListings;
}