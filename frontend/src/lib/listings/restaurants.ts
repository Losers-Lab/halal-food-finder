import type { CuttingMethod } from "./schemas";

/**
 * Read-model types for the search/browse + detail screens (docs/design/
 * search-browse.md, detail-page.md).
 *
 * The live backend (ListingReadController, sc-171) serves GET /v1/listings
 * (browse cards) and GET /v1/listings/{id} (detail). Both are keyed by a UUID
 * `id` — the backend has no slug, so the frontend routes by `id` and the image
 * URLs arrive in the payload and are consumed verbatim (never rebuilt here).
 * The listing-read seam (`lib/listings/data.ts`) maps those payloads onto this
 * type; `seed.ts` is retained strictly as a test fixture / mock source.
 */

/** Human display hours; today's row emphasized on the detail page. */
export type DayHours = { day: string; value: string; closed?: boolean };

/** Verification certificate — the trust centerpiece (detail-page.md §1.2). */
export type Certificate = {
  certifier: string;
  reviewedOn: string; // ISO date
  expiresOn: string; // ISO date
  certificateUrl?: string;
};

export type Restaurant = {
  /** The listing's backend UUID (routes: /restaurants/{id}). */
  id: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
  cuisine: string;
  cuttingMethod: CuttingMethod;
  rating?: number;
  reviewCount?: number;
  distanceMi?: number;
  phone?: string;
  website?: string;
  /**
   * sc-157: browse/search card variant (≤400px thumbnail, low bandwidth).
   * Absent before a photo is ingested for the listing — render the quiet
   * placeholder, never a broken image. Cards request this ONLY (never imageUrl).
   */
  imageThumbnailUrl?: string;
  /**
   * sc-157: detail-page full-res original. The detail hero is the only surface
   * that requests this variant. Absent → same quiet placeholder as cards.
   */
  imageUrl?: string;
  /** Edge: absent when unknown — render without the primitive (never "N/A"). */
  hours?: DayHours[];
  certificate?: Certificate;
};

/** Which listing a card is: verified iff it has a non-expired certificate. */
export type VerificationStatus = "VERIFIED" | "UNVERIFIED";

export function verificationStatus(r: Restaurant): VerificationStatus {
  if (!r.certificate) return "UNVERIFIED";
  const expired = new Date(r.certificate.expiresOn).getTime() < Date.now();
  return expired ? "UNVERIFIED" : "VERIFIED";
}

/** Compute the detail page's cert expiry state (detail-page.md §1.2). */
export type ExpiryState = "valid" | "expiring" | "expired" | "none";

const EXPIRING_SOON_DAYS = 60;

export function expiryState(c?: Certificate): ExpiryState {
  if (!c) return "none";
  const now = Date.now();
  const expires = new Date(c.expiresOn).getTime();
  if (expires < now) return "expired";
  const daysLeft = Math.ceil((expires - now) / 86_400_000);
  if (daysLeft <= EXPIRING_SOON_DAYS) return "expiring";
  return "valid";
}

/** Human date, e.g. "Aug 12, 2026" — never raw ISO in UI. */
export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}