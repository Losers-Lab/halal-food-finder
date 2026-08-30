import type { CuttingMethod } from "./schemas";

/**
 * Read-model types for the search/browse + detail screens (docs/design/
 * search-browse.md, detail-page.md).
 *
 * NOTE — backend gap: the API currently exposes only POST /v1/listings (create),
 * auth, /v1/me and /v1/health. There is NO GET /v1/listings (search/browse) or
 * GET /v1/listings/{slug} (detail) endpoint yet. These types and the in-memory
 * repository below are the frontend read-model + a local seed so the UI can be
 * built and exercised now; the repository is the single seam that swaps to a
 * real HTTP call (api.searchListings / api.getListing) once Hamza lands those
 * endpoints. Flagged to engineering — not silently papered over.
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
  id: string;
  slug: string;
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