import {
  type Restaurant,
  verificationStatus,
} from "./restaurants";
import type { CuttingMethod } from "./schemas";

/**
 * In-memory read repository for the search/browse + detail screens.
 *
 * BACKEND GAP (see restaurants.ts): there is no GET search / GET detail endpoint
 * yet. Until Hamza lands them, this module serves a typed seed so the UI is
 * buildable and testable. Provide a `listings` source (defaults to SEED) so the
 * swap to an HTTP-backed source is a one-line change here.
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
 * Deliberately covers every card/detail state: verified vs unverified, hand vs
 * machine cut, and — on the detail tier — valid / expiring-soon / expired certs.
 */
export const SEED: Restaurant[] = [
  {
    id: "l-1",
    slug: "al-amir-grill",
    name: "Al-Amir Grill",
    address: "112 Atlantic Ave, Brooklyn, NY",
    lat: 40.6916,
    lng: -73.9788,
    cuisine: "Middle Eastern",
    cuttingMethod: "HAND_CUT",
    rating: 4.6,
    reviewCount: 89,
    distanceMi: 1.2,
    phone: "+17185551234",
    website: "https://example.com/al-amir",
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
    slug: "karachi-kitchen",
    name: "Karachi Kitchen",
    address: "240 Brighton Beach Ave, Brooklyn, NY",
    lat: 40.5774,
    lng: -73.9596,
    cuisine: "Pakistani",
    cuttingMethod: "HAND_CUT",
    rating: 4.8,
    reviewCount: 612,
    distanceMi: 0.4,
    phone: "+17185559876",
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
    slug: "shawarma-brothers",
    name: "Shawarma Brothers",
    address: "85 Washington St, Brooklyn, NY",
    lat: 40.7027,
    lng: -73.9895,
    cuisine: "Middle Eastern",
    cuttingMethod: "HAND_CUT",
    rating: 4.6,
    reviewCount: 1204,
    distanceMi: 1.1,
    certificate: {
      certifier: "IFANCA",
      reviewedOn: seedHelpers.reviewedOnAgoDays(400),
      expiresOn: new Date(NOW() - 10 * 86_400_000).toISOString(), // expired
      certificateUrl: "https://example.com/cert/shawarma.pdf",
    },
  },
  {
    id: "l-4",
    slug: "daves-hot-chicken",
    name: "Dave's Hot Chicken",
    address: "902 Utica Ave, Brooklyn, NY",
    lat: 40.6519,
    lng: -73.9302,
    cuisine: "American",
    cuttingMethod: "MACHINE_CUT",
    rating: 4.4,
    reviewCount: 2377,
    distanceMi: 2.3,
  },
  {
    id: "l-5",
    slug: "al-sultan-grill",
    name: "Al-Sultan Grill",
    address: "571 Nostrand Ave, Brooklyn, NY",
    lat: 40.6777,
    lng: -73.9497,
    cuisine: "Lebanese",
    cuttingMethod: "HAND_CUT",
    rating: 4.7,
    reviewCount: 540,
    distanceMi: 1.8,
    certificate: {
      certifier: "IFANCA",
      reviewedOn: seedHelpers.reviewedOnAgoDays(150),
      expiresOn: seedHelpers.expiryInYears(1),
      certificateUrl: "https://example.com/cert/al-sultan.pdf",
    },
  },
  {
    id: "l-6",
    slug: "the-halal-guys",
    name: "The Halal Guys",
    address: "310 Court St, Brooklyn, NY",
    lat: 40.684,
    lng: -73.9914,
    cuisine: "American",
    cuttingMethod: "MACHINE_CUT",
    rating: 4.5,
    reviewCount: 8912,
    distanceMi: 2.6,
  },
  {
    id: "l-7",
    slug: "cafe-acai",
    name: "Cafe Acai",
    address: "447 Graham Ave, Brooklyn, NY",
    lat: 40.7143,
    lng: -73.9447,
    cuisine: "Brazilian",
    cuttingMethod: "UNSPECIFIED",
    rating: 4.9,
    reviewCount: 321,
    distanceMi: 3.2,
  },
];

export function searchListings(
  query: string,
  cuttingMethod?: CuttingMethod,
): Restaurant[] {
  const q = query.trim().toLowerCase();
  return SEED.filter((r) => {
    if (cuttingMethod && cuttingMethod !== "UNSPECIFIED") {
      if (r.cuttingMethod !== cuttingMethod) return false;
    }
    if (!q) return true;
    const haystack = `${r.name} ${r.cuisine} ${r.address}`.toLowerCase();
    return haystack.includes(q);
  }).sort((a, b) => (a.distanceMi ?? 0) - (b.distanceMi ?? 0));
}

export function getRestaurantBySlug(slug: string): Restaurant | undefined {
  return SEED.find((r) => r.slug === slug);
}

/**
 * Async read seam — mirrors the future GET search / GET detail endpoints so the
 * screens exercise loading + error states and the swap to HTTP-backed calls is a
 * one-file change here (see restaurants.ts "backend gap"). Tests mock these.
 */
export function fetchRestaurantBySlug(
  slug: string,
): Promise<Restaurant | undefined> {
  return Promise.resolve(getRestaurantBySlug(slug));
}

/** Exposed for cards: reusable verification check. */
export { verificationStatus };

export type { Restaurant, VerificationStatus } from "./restaurants";