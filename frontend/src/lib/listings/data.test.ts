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
    cuttingMethod: "HAND_CUT",
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
  });

  it("leaves imageSrcset undefined when the backend sends no srcset (pre-ingest/legacy)", async () => {
    getListingsMock.mockResolvedValue([card({ imageSrcset: null })]);

    const r = await searchListings("");
    expect(r[0].imageSrcset).toBeUndefined();
    // The small-thumbnail fallback still resolves for the card in that case.
    expect(r[0].imageThumbnailUrl).toBe(`/v1/listings/${UUID}/image?variant=thumbnail`);
  });

  it("filters browse results by query and cutting method through the pure helper", async () => {
    getListingsMock.mockResolvedValue([
      card({ name: "Afrah", cuttingMethod: "HAND_CUT" }),
      card({ id: "other", name: "Burger Joint", cuttingMethod: "MACHINE_CUT" }),
    ]);

    const byCut = await searchListings("", "MACHINE_CUT");
    expect(byCut.map((x) => x.name)).toEqual(["Burger Joint"]);

    const byQuery = await searchListings("afrah");
    expect(byQuery.map((x) => x.id)).toEqual([UUID]);
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
    { id: "a", name: "Al-Amir Grill", address: "1 St", lat: 1, lng: 1, cuisine: "Middle Eastern", cuttingMethod: "HAND_CUT", distanceMi: 2.0 },
    { id: "b", name: "Karachi Kitchen", address: "2 St", lat: 1, lng: 1, cuisine: "Pakistani", cuttingMethod: "HAND_CUT", distanceMi: 0.4 },
    { id: "c", name: "Burger Joint", address: "3 St", lat: 1, lng: 1, cuisine: "American", cuttingMethod: "MACHINE_CUT", distanceMi: 1.2 },
  ];

  it("matches query against name/cuisine/address and sorts by distance", () => {
    const r = filterListings(list, "karachi");
    expect(r.map((x) => x.id)).toEqual(["b"]);
    expect(filterListings(list, "").map((x) => x.id)).toEqual(["b", "c", "a"]);
  });

  it("restricts to a cutting method when specified (UNSPECIFIED means no filter)", () => {
    expect(filterListings(list, "", "MACHINE_CUT")).toHaveLength(1);
    expect(filterListings(list, "", undefined).length).toBe(3);
  });
});