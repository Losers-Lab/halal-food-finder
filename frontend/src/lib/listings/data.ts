import { api, ApiError, type BrowseListing, type ListingDetail } from "@/lib/api/client";
import type { Restaurant, VerificationStatus } from "./restaurants";
import type { CuttingMethod } from "./schemas";

export type BrowseFilter = "ALL" | CuttingMethod;

export type { Restaurant } from "./restaurants";

/**
 * Live listing read seam (sc-171) — the runtime source behind the browse +
 * detail screens. Replaces the in-memory SEED as the read source: GET
 * /v1/listings (browse cards) and GET /v1/listings/{id} (detail) via the shared
 * api client. `seed.ts` is now only a test fixture / mock source.
 *
 * The backend default profile has NO query/cutting filters on the browse
 * endpoint (it returns all cards), so the search/cutting filtering that the
 * UI drives stays client-side here, exactly as the shelling screens already did.
 */

/**
 * Pure client-side filter for the browse screens — mirrors the search behavior
 * that used to live on the seed: match name/cuisine/address against the query,
 * optionally restrict to a cutting method, then sort by distance.
 */
export function filterListings(
  restaurants: Restaurant[],
  query: string,
  cuttingMethod?: CuttingMethod,
): Restaurant[] {
  const q = query.trim().toLowerCase();
  const filtered = restaurants.filter((r) => {
    if (cuttingMethod && cuttingMethod !== "UNSPECIFIED") {
      if (r.cuttingMethod !== cuttingMethod) return false;
    }
    if (!q) return true;
    const haystack = `${r.name} ${r.cuisine} ${r.address}`.toLowerCase();
    return haystack.includes(q);
  });
  return filtered.sort((a, b) => (a.distanceMi ?? 0) - (b.distanceMi ?? 0));
}

/**
 * Normalize a backend image URL to the frontend's same-origin proxy path.
 *
 * The backend builds image URLs from its own context path and returns them as
 * ABSOLUTE cross-origin URLs (e.g. `http://localhost:8080/v1/listings/{id}/
 * image?variant=thumbnail`). The browser must load them through the frontend's
 * own origin so `next/image` and the `img-src 'self'` CSP are satisfied. Since
 * next.config.ts rewrites `/v1/*` to the backend, dropping the origin (keeping
 * the `/v1/...` path + query) serves the same bytes same-origin in every
 * environment — dev and prod. Non-/v1 or already-relative URLs pass through.
 */
function sameOriginPath(url: string): string {
  try {
    const parsed = new URL(url);
    if (parsed.pathname.startsWith("/v1/")) {
      return `${parsed.pathname}${parsed.search}`;
    }
    return url;
  } catch {
    return url;
  }
}

function toRestaurant(b: BrowseListing | ListingDetail): Restaurant {
  return {
    id: b.id,
    name: b.name,
    address: b.address,
    lat: b.lat,
    lng: b.lng,
    cuisine: b.cuisine ?? "",
    cuttingMethod: b.cuttingMethod as CuttingMethod,
    // sc-49: carry the backend's authoritative verification state through to the
    // read model so cards + detail render the real trust badge. Absent on
    // legacy payloads → `verificationStatus()` falls back to certificate-derived.
    ...(b.verificationStatus
      ? { verificationStatus: b.verificationStatus as VerificationStatus }
      : {}),
    imageThumbnailUrl: sameOriginPath(b.imageThumbnailUrl),
    // sc-183: normalize every srcset entry URL to the same-origin proxy path.
    ...(b.imageSrcset?.length
      ? {
          imageSrcset: b.imageSrcset.map((entry) => ({
            width: entry.width,
            url: sameOriginPath(entry.url),
          })),
        }
      : {}),
    ...("imageUrl" in b ? { imageUrl: sameOriginPath(b.imageUrl) } : {}),
    // sc-73 read surface: carry the certificate display facts (certifier /
    // reviewedOn / expiresOn / certificateUrl) through so the detail page's
    // CertificatePanel renders them from LIVE backend data. sameOriginPath
    // rewrites the certificateUrl's origin to our own proxy path, exactly as
    // the image URLs above. Absent payloads pass through as undefined.
    ...("certificate" in b && b.certificate
      ? {
          certificate: {
            certifier: b.certificate.certifier ?? "",
            reviewedOn: b.certificate.reviewedOn,
            expiresOn: b.certificate.expiresOn ?? "",
            certificateUrl: sameOriginPath(b.certificate.certificateUrl),
          },
        }
      : {}),
  };
}

/** GET /v1/listings → mapped + filtered browse cards. */
export async function searchListings(
  query: string,
  cuttingMethod?: CuttingMethod,
): Promise<Restaurant[]> {
  const cards = await api.getListings();
  return filterListings(cards.map(toRestaurant), query, cuttingMethod);
}

/**
 * GET /v1/listings/{id} → detail payload, or `undefined` when the backend
 * returns 404 (drives the not-found state, mirroring the previous by-slug read).
 */
export async function getRestaurant(id: string): Promise<Restaurant | undefined> {
  let detail: ListingDetail;
  try {
    detail = await api.getListing(id);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) return undefined;
    throw error;
  }
  return toRestaurant(detail);
}

/**
 * GET /v1/favorites — the authenticated user's favourited listings, mapped to
 * the same browse-card read-model as the search grid (sc-50). Requires a
 * session (401 otherwise); the caller gates on auth.
 */
export async function getFavorites(): Promise<Restaurant[]> {
  const cards = await api.getFavorites();
  return cards.map(toRestaurant);
}