import Link from "next/link";
import {
  HandCutIndicator,
  MachineCutIndicator,
  UnverifiedTag,
  VerifiedBadge,
} from "@/components/trust";
import { RestaurantPhoto } from "./RestaurantPhoto";
import { FavoriteButton } from "./FavoriteButton";
import type { Restaurant } from "@/lib/listings/restaurants";
import {
  verificationStatus,
} from "@/lib/listings/seed";

/**
 * Listing card — docs/design/search-browse.md §"Listing cards (grid)" and
 * trust-components.md §5 (composition). Photo (16:9, kraft placeholder) →
 * name + VerifiedBadge/UnverifiedTag (same slot, no layout shift) → meta row
 * (cuisine · distance · ★ rating) → CutMethodIndicator if known.
 *
 * Whole-card click is enhancement; the name link is the accessible primary path.
 *
 * Image (sc-157): the card requests the SMALL thumbnail variant ONLY, lazy —
 * never the full-res original ("no oversized fetch on cards").
 */
export function ListingCard({ restaurant }: { restaurant: Restaurant }) {
  const verified = verificationStatus(restaurant) === "VERIFIED";
  const meta = [
    restaurant.cuisine,
    restaurant.distanceMi != null ? `${restaurant.distanceMi.toFixed(1)} mi` : null,
    restaurant.rating != null
      ? `★ ${restaurant.rating.toFixed(1)}${restaurant.reviewCount != null ? ` (${restaurant.reviewCount})` : ""}`
      : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <article className="group relative overflow-hidden rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 shadow-card transition-transform duration-200 ease-out hover:-translate-y-0.5 hover:shadow-pop motion-reduce:transition-none motion-reduce:hover:translate-y-0">
      {/* Photo block — thumbnail variant only (sc-157) */}
      <div className="relative aspect-video bg-kraft-100">
        <RestaurantPhoto
          src={restaurant.imageThumbnailUrl}
          alt={restaurant.name}
          sizes="(min-width: 1280px) 25vw, (min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw"
        />
        {/* Save heart (top-left) + verified mark (top-right) never overlap. */}
        <div className="absolute left-3 top-3">
          <FavoriteButton
            listingId={restaurant.id}
            restaurant={restaurant}
            variant="card"
          />
        </div>
        {verified ? (
          <div className="absolute right-3 top-3">
            <VerifiedBadge variant="on-photo" />
          </div>
        ) : null}
      </div>

      <div className="p-5">
        <div className="flex items-start justify-between gap-2">
          <h3 className="text-heading text-ink-900">
            <Link
              href={`/restaurants/${restaurant.id}`}
              className="after:absolute after:inset-0 after:content-[''] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
            >
              {restaurant.name}
            </Link>
          </h3>
          <span className="shrink-0">
            {verified ? <VerifiedBadge /> : <UnverifiedTag />}
          </span>
        </div>

        {meta ? <p className="mt-1 text-small text-ink-500">{meta}</p> : null}

        {restaurant.cuttingMethod === "HAND_CUT" ? (
          <div className="mt-3">
            <HandCutIndicator />
          </div>
        ) : restaurant.cuttingMethod === "MACHINE_CUT" ? (
          <div className="mt-3">
            <MachineCutIndicator />
          </div>
        ) : null}
      </div>
    </article>
  );
}