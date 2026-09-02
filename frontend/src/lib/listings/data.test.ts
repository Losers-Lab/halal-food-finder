import { describe, expect, it, vi, beforeEach } from "vitest";
import { ApiError } from "@/lib/api/client";
import type { BrowseListing, ListingDetail } from "@/lib/api/client";
import type { Restaurant } from "./restaurants";

vi.mock("@/lib/api/client", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/api/client")>();
  return {
    ...original,
    api: { getListings: vi.fn(), getListing: vi.fn(), getFavorites: vi.fn() },
  };
});
import { api } from "@/lib/api/client";
import { filterListings, getFavorites, getRestaurant, searchListings } from "./data";

const getListingsMock = vi.mocked(api.getListings);
const getListingMock = vi.mocked(api.getListing);
const getFavoritesMock = vi.mocked(api.getFavorites);

const UUID = "12ca4fe9-2884-4cac-9528-cc38fc0efa2f";

function card(over: Partial<BrowseListing> = {}): BrowseListing {
  return {
    id: UUID,
    name: "Afrah",
    address: "E Main St",
    lat: 32.94807,
    lng: -96.728031,
    cuisine: null,
    isHandCut: true,
    isDelivery: false,
    verificationStatus: "UNVERIFIED",
    imageThumbnailUrl: `http://localhost:8080/v1/listings/${UUID}/image?variant=thumbnail`,
    // sc-183: the backend's pre-rendered multi-width thumbnail set.
    imageSrcset: [
      { width: 400, url: `http://localhost:8080/v1/listings/${UUID}/image?variant=thumbnail` },
      { width: 768, url: `http://localhost:8080/v1/listings/${UUID}/image?variant=thumbnail_768` },
      { width: 1280, url: `http://localhost:8080/v1/listings/${UUID}/image?variant=thumbnail_1280` },
      { width: 1920, url: `http://localhost:8080/v1/listings/${UUID}/image?variant=thumbnail_1920` },
    ],
    ...over,
  };
}

function detail(over: Partial<ListingDetail> = {}): ListingDetail {
  return {
    ...card(over),
    imageUrl: `http://localhost:8080/v1/listings/${UUID}/image?variant=full`,
  };
}

function certDetail(
  over: Partial<ListingDetail> = {},
): ListingDetail {
  return {
    ...detail(over),
    verificationStatus: "VERIFIED",
    certificate: {
      certifier: "HFSAA",
      reviewedOn: "2026-09-01T12:00:00Z",
      expiresOn: "2027-01-12",
      certificateUrl: `http://localhost:8080/v1/listings/${UUID}/certificate`,
    },
  };
}

describe("data seam — live listing reads (sc-171)", () => {
  beforeEach(() => {
    getListingsMock.mockReset();
    getListingMock.mockReset();
    getFavoritesMock.mockReset();
  });

  it("maps browse cards and normalizes absolute backend image URLs to the same-origin /v1 path", async () => {
    getListingsMock.mockResolvedValue([card()]);

    const r = await searchListings("");

    expect(r).toHaveLength(1);
    expect(r[0].id).toBe(UUID);
    expect(r[0].imageThumbnailUrl).toBe(`/v1/listings/${UUID}/image?variant=thumbnail`);
    // sc-183: every srcset entry URL is normalized to the same-origin proxy
    // path, widths preserved (the card sources the widest for a sharp srcset).
    expect(r[0].imageSrcset).toEqual([
      { width: 400, url: `/v1/listings/${UUID}/image?variant=thumbnail` },
      { width: 768, url: `/v1/listings/${UUID}/image?variant=thumbnail_768` },
      { width: 1280, url: `/v1/listings/${UUID}/image?variant=thumbnail_1280` },
      { width: 1920, url: `/v1/listings/${UUID}/image?variant=thumbnail_1920` },
    ]);
    expect(r[0].imageUrl).toBeUndefined(); // browse cards never carry the full-res
    expect(r[0].cuisine).toBe(""); // null cuisine → empty, not "N/A"
    expect(r[0].isDelivery).toBe(false); // sc-184 read model carries the delivery flag
  });

  it("leaves imageSrcset undefined when the backend sends no srcset (pre-ingest/legacy)", async () => {
    getListingsMock.mockResolvedValue([card({ imageSrcset: null })]);

    const r = await searchListings("");
    expect(r[0].imageSrcset).toBeUndefined();
    // The small-thumbnail fallback still resolves for the card in that case.
    expect(r[0].imageThumbnailUrl).toBe(`/v1/listings/${UUID}/image?variant=thumbnail`);
  });

  it("filters browse results by query and hand-cut through the pure helper", async () => {
    getListingsMock.mockResolvedValue([
      card({ name: "Afrah", isHandCut: true }),
      card({ id: "other", name: "Burger Joint", isHandCut: false }),
    ]);

    const byCut = await searchListings("", true);
    expect(byCut.map((x) => x.name)).toEqual(["Afrah"]);

    const byQuery = await searchListings("afrah");
    expect(byQuery.map((x) => x.id)).toEqual([UUID]);
  });

  it("pipes the deliveryOnly flag through to the browse filter (sc-184)", async () => {
    getListingsMock.mockResolvedValue([
      card({ id: "d1", name: "Deliveroo", isDelivery: true }),
      card({ id: "p1", name: "Pickup Only", isDelivery: false }),
      card({ id: "u1", name: "Unknown Mode", isDelivery: null }),
    ]);

    // deliveryOnly=true narrows to listings that claim delivery only.
    const byDelivery = await searchListings("", undefined, true);
    expect(byDelivery.map((x) => x.id)).toEqual(["d1"]);

    // absent deliveryOnly = no delivery predicate (every row matches), exactly
    // like the sc-42 hand-cut on/off filter.
    expect(await searchListings("")).toHaveLength(3);
  });

  it("getRestaurant maps the detail payload, normalizing the full-res hero URL", async () => {
    getListingMock.mockResolvedValue(detail());

    const r = await getRestaurant(UUID);

    expect(r?.imageThumbnailUrl).toBe(`/v1/listings/${UUID}/image?variant=thumbnail`);
    expect(r?.imageUrl).toBe(`/v1/listings/${UUID}/image?variant=full`);
    expect(r?.imageSrcset?.[3].width).toBe(1920);
    expect(r?.imageSrcset?.[3].url).toBe(`/v1/listings/${UUID}/image?variant=thumbnail_1920`);
  });

  it("carries the certificate display facts through and normalizes the certificateUrl (sc-73 read surface)", async () => {
    getListingMock.mockResolvedValue(certDetail());

    const r = await getRestaurant(UUID);

    expect(r?.verificationStatus).toBe("VERIFIED");
    expect(r?.certificate).toEqual({
      certifier: "HFSAA",
      reviewedOn: "2026-09-01T12:00:00Z",
      expiresOn: "2027-01-12",
      // absolute backend origin rewritten to our same-origin /v1 path, like images.
      certificateUrl: `/v1/listings/${UUID}/certificate`,
    });
  });

  it("leaves certificate undefined when the backend emits none (unverified / no cert recorded)", async () => {
    getListingMock.mockResolvedValue(detail());

    const r = await getRestaurant(UUID);

    expect(r?.certificate).toBeUndefined();
  });

  it("getRestaurant returns undefined on a 404 (drives the not-found state)", async () => {
    getListingMock.mockRejectedValue(new ApiError(404, "invalid_input"));

    await expect(getRestaurant("missing")).resolves.toBeUndefined();
  });

  it("getRestaurant rethrows non-404 errors for the fetch-error state", async () => {
    getListingMock.mockRejectedValue(new ApiError(503, "internal_error"));

    await expect(getRestaurant(UUID)).rejects.toMatchObject({ status: 503 });
  });

  it("getFavorites maps browse-card objects to the read-model and normalizes image URLs (sc-50)", async () => {
    getFavoritesMock.mockResolvedValue([card()]);

    const r = await getFavorites();

    expect(r).toHaveLength(1);
    expect(r[0].id).toBe(UUID);
    expect(r[0].imageThumbnailUrl).toBe(`/v1/listings/${UUID}/image?variant=thumbnail`);
    expect(r[0].imageUrl).toBeUndefined(); // favorites cards carry only the thumbnail
  });
});

describe("filterListings (pure browse filter)", () => {
  const list: Restaurant[] = [
    { id: "a", name: "Al-Amir Grill", address: "1 St", lat: 1, lng: 1, cuisine: "Middle Eastern", isHandCut: true, isDelivery: true, distanceMi: 2.0 },
    { id: "b", name: "Karachi Kitchen", address: "2 St", lat: 1, lng: 1, cuisine: "Pakistani", isHandCut: true, isDelivery: false, distanceMi: 0.4 },
    { id: "c", name: "Burger Joint", address: "3 St", lat: 1, lng: 1, cuisine: "American", isHandCut: false, isDelivery: null, distanceMi: 1.2 },
  ];

  it("matches query against name/cuisine/address and sorts by distance", () => {
    const r = filterListings(list, "karachi");
    expect(r.map((x) => x.id)).toEqual(["b"]);
    expect(filterListings(list, "").map((x) => x.id)).toEqual(["b", "c", "a"]);
  });

  it("restricts to hand-cut only when handCut is set (absent/false = no filter)", () => {
    expect(filterListings(list, "", true)).toHaveLength(2);
    expect(filterListings(list, "", undefined).length).toBe(3);
  });

  it("restricts to delivery-only when deliveryOnly is set (false/null = no delivery) (sc-184)", () => {
    expect(filterListings(list, "", undefined, true)).toHaveLength(1);
    expect(filterListings(list, "", undefined, false).length).toBe(3);
  });
});