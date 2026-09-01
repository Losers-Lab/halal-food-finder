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
  cardThumbFallback,
  cardThumbSource,
  verificationStatus,
} from "@/lib/listings/restaurants";

/**
 * Listing card — docs/design/search-browse.md §"Listing cards (grid)" and
 * trust-components.md §5 (composition). Photo (16:9, kraft placeholder) →
 * name + VerifiedBadge/UnverifiedTag (same slot, no layout shift) → meta row
 * (cuisine · distance · ★ rating) → CutMethodIndicator if known.
 *
 * Whole-card click is enhancement; the name link is the accessible primary path.
 *
 * Image (sc-183): the card sources from the WIDEST backend `imageSrcset` title
 * variant (via `cardThumbSource`), so next/image's responsive srcset —
 * generated from `sizes` — is downscaled from a high-res source and serves
 * sharp at every width (mobile 100vw @ DPR3 → desktop). `sizes="100vw"` follows
 * the FOUNDER directive (sc-183): the srcset sizing baseline is the monitor's
 * MAX resolution, not the live window/panel width — so on a 1920-wide display
 * the browser picks the max-monitor-context source at initial load, with NO
 * re-render / re-fetch on any resize (pure srcset behavior, no resize listeners).
 * Never requests the full-res original on cards. If the widest source 404s
 * (legacy listing ingested before the multi-width backend), RestaurantPhoto
 * steps down to `fallbackSrc` (the guaranteed ≤400px thumbnail) — a valid photo
 * always renders, per sc-157. See RestaurantPhoto.
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
      {/* Photo block — widest title variant sourced for a sharp srcset (sc-183) */}
      <div className="relative aspect-video bg-kraft-100">
        <RestaurantPhoto
          src={cardThumbSource(restaurant)}
          fallbackSrc={cardThumbFallback(restaurant)}
          alt={restaurant.name}
          sizes="100vw"
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