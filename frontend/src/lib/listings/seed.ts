import {
  type Restaurant,
  verificationStatus,
} from "./restaurants";

/**
 * In-memory read fixture (sc-171). The runtime browse + detail screens now read
 * from the live backend via `data.ts` (GET /v1/listings / {id}). This module is
 * retained as a pure TEST FIXTURE / mock source only — rich seed listings
 * covering every card/detail state (verified/unverified, hand-cut / not /
 * unknown, valid/expiring/expired certs) so the UI is buildable and testable
 * without the live backend. Nothing in the runtime path imports it as its
 * source.
 */

/** The current date used for expiry-state calculations (test seam). */
export const NOW = () => Date.now();

type Shares = {
  /** years ahead for annual expiries, etc. */
  expiryInYears: (yearsFromReview: number) => string;
  reviewedOnAgoDays: (days: number) => string;
};

const seedHelpers: Shares = {
  expiryInYears: (years: number) =>
    new Date(NOW() + years * 365 * 86_400_000).toISOString(),
  reviewedOnAgoDays: (days: number) =>
    new Date(NOW() - days * 86_400_000).toISOString(),
};

/**
 * Seed listings mirroring approved sketches (006-stamps-search, 007-detail-stamps).
 * Deliberately covers every card/detail state: verified vs unverified, hand-cut vs
 * not vs unknown (isHandCut true/false/null), and — on the detail tier — valid /
 * expiring-soon / expired certs.
 *
 * sc-157: each listing carries the two bundled image variants served by the
 * backend ImagePort (`GET /v1/listings/{id}/image?variant=thumbnail|full`,
 * docs/design/sc-157-image-variants.md). Cards use `imageThumbnailUrl` (≤400px)
 * ONLY; the detail hero uses `imageUrl` (full-res).
 */
export const SEED: Restaurant[] = [
  {
    id: "l-1",
    name: "Al-Amir Grill",
    address: "112 Atlantic Ave, Brooklyn, NY",
    lat: 40.6916,
    lng: -73.9788,
    cuisine: "Middle Eastern",
    isHandCut: true,
    rating: 4.6,
    reviewCount: 89,
    distanceMi: 1.2,
    phone: "+17185551234",
    website: "https://example.com/al-amir",
    imageThumbnailUrl: "/v1/listings/l-1/image?variant=thumbnail",
    imageUrl: "/v1/listings/l-1/image?variant=full",
    hours: [
      { day: "Mon", value: "11 AM – 10 PM" },
      { day: "Tue", value: "11 AM – 10 PM" },
      { day: "Wed", value: "11 AM – 10 PM" },
      { day: "Thu", value: "11 AM – 10 PM" },
      { day: "Fri", value: "11 AM – 10 PM" },
      { day: "Sat", value: "11 AM – 11 PM" },
      { day: "Sun", value: "11 AM – 11 PM" },
    ],
    certificate: {
      certifier: "HFSAA",
      reviewedOn: seedHelpers.reviewedOnAgoDays(200),
      expiresOn: seedHelpers.expiryInYears(1),
      certificateUrl: "https://example.com/cert/al-amir.pdf",
    },
  },
  {
    id: "l-2",
    name: "Karachi Kitchen",
    address: "240 Brighton Beach Ave, Brooklyn, NY",
    lat: 40.5774,
    lng: -73.9596,
    cuisine: "Pakistani",
    isHandCut: true,
    rating: 4.8,
    reviewCount: 612,
    distanceMi: 0.4,
    phone: "+17185559876",
    imageThumbnailUrl: "/v1/listings/l-2/image?variant=thumbnail",
    imageUrl: "/v1/listings/l-2/image?variant=full",
    hours: [
      { day: "Mon", value: "11 AM – 10 PM" },
      { day: "Tue", value: "11 AM – 10 PM" },
      { day: "Wed", value: "11 AM – 10 PM" },
      { day: "Thu", value: "11 AM – 10 PM" },
      { day: "Fri", value: "11 AM – 10 PM" },
      { day: "Sat", value: "11 AM – 11 PM" },
      { day: "Sun", value: "11 AM – 11 PM" },
    ],
    certificate: {
      certifier: "HFSAA",
      reviewedOn: seedHelpers.reviewedOnAgoDays(30),
      expiresOn: seedHelpers.expiryInYears(0), // < 60 days to expiry → "expiring soon"
      certificateUrl: "https://example.com/cert/karachi.pdf",
    },
  },
  {
    id: "l-3",
    name: "Shawarma Brothers",
    address: "85 Washington St, Brooklyn, NY",
    lat: 40.7027,
    lng: -73.9895,
    cuisine: "Middle Eastern",
    isHandCut: true,
    rating: 4.6,
    reviewCount: 1204,
    distanceMi: 1.1,
    imageThumbnailUrl: "/v1/listings/l-3/image?variant=thumbnail",
    imageUrl: "/v1/listings/l-3/image?variant=full",
    certificate: {
      certifier: "IFANCA",
      reviewedOn: seedHelpers.reviewedOnAgoDays(400),
      expiresOn: new Date(NOW() - 10 * 86_400_000).toISOString(), // expired
      certificateUrl: "https://example.com/cert/shawarma.pdf",
    },
  },
  {
    id: "l-4",
    name: "Dave's Hot Chicken",
    address: "902 Utica Ave, Brooklyn, NY",
    lat: 40.6519,
    lng: -73.9302,
    cuisine: "American",
    isHandCut: false,
    rating: 4.4,
    reviewCount: 2377,
    distanceMi: 2.3,
    imageThumbnailUrl: "/v1/listings/l-4/image?variant=thumbnail",
    imageUrl: "/v1/listings/l-4/image?variant=full",
  },
  {
    id: "l-5",
    name: "Al-Sultan Grill",
    address: "571 Nostrand Ave, Brooklyn, NY",
    lat: 40.6777,
    lng: -73.9497,
    cuisine: "Lebanese",
    isHandCut: true,
    rating: 4.7,
    reviewCount: 540,
    distanceMi: 1.8,
    imageThumbnailUrl: "/v1/listings/l-5/image?variant=thumbnail",
    imageUrl: "/v1/listings/l-5/image?variant=full",
    certificate: {
      certifier: "IFANCA",
      reviewedOn: seedHelpers.reviewedOnAgoDays(150),
      expiresOn: seedHelpers.expiryInYears(1),
      certificateUrl: "https://example.com/cert/al-sultan.pdf",
    },
  },
  {
    id: "l-6",
    name: "The Halal Guys",
    address: "310 Court St, Brooklyn, NY",
    lat: 40.684,
    lng: -73.9914,
    cuisine: "American",
    isHandCut: false,
    rating: 4.5,
    reviewCount: 8912,
    distanceMi: 2.6,
    imageThumbnailUrl: "/v1/listings/l-6/image?variant=thumbnail",
    imageUrl: "/v1/listings/l-6/image?variant=full",
  },
  {
    id: "l-7",
    name: "Cafe Acai",
    address: "447 Graham Ave, Brooklyn, NY",
    lat: 40.7143,
    lng: -73.9447,
    cuisine: "Brazilian",
    isHandCut: null,
    rating: 4.9,
    reviewCount: 321,
    distanceMi: 3.2,
    imageThumbnailUrl: "/v1/listings/l-7/image?variant=thumbnail",
    imageUrl: "/v1/listings/l-7/image?variant=full",
  },
];

export function searchListings(
  query: string,
  handCutOnly?: boolean,
): Restaurant[] {
  const q = query.trim().toLowerCase();
  return SEED.filter((r) => {
    if (handCutOnly) {
      if (r.isHandCut !== true) return false;
    }
    if (!q) return true;
    const haystack = `${r.name} ${r.cuisine} ${r.address}`.toLowerCase();
    return haystack.includes(q);
  }).sort((a, b) => (a.distanceMi ?? 0) - (b.distanceMi ?? 0));
}

/** Exposed for cards: reusable verification check. */
export { verificationStatus };

export type { Restaurant, VerificationStatus } from "./restaurants";