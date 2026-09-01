import { describe, expect, it } from "vitest";
import {
  cardThumbSource,
  expiryState,
  formatDate,
  verificationStatus,
  type Certificate,
  type Restaurant,
} from "./restaurants";
import { searchListings } from "./seed";

const cert = (expiresOn: string, reviewedOn = "2026-01-01T00:00:00Z"): Certificate => ({
  certifier: "HFSAA",
  reviewedOn,
  expiresOn,
});

function restaurant(over: Partial<Restaurant>): Restaurant {
  return {
    id: "l-x",
    name: "Test Place",
    address: "1 Main St",
    lat: 1,
    lng: 1,
    cuisine: "Test",
    cuttingMethod: "UNSPECIFIED",
    ...over,
  };
}

describe("verificationStatus", () => {
  it("is VERIFIED only when a certificate exists and has not expired", () => {
    const future = new Date(Date.now() + 30 * 86_400_000).toISOString();
    expect(verificationStatus(restaurant({ certificate: cert(future) }))).toBe(
      "VERIFIED",
    );
  });

  it("is UNVERIFIED when there is no certificate", () => {
    expect(verificationStatus(restaurant({}))).toBe("UNVERIFIED");
  });

  it("downgrades to UNVERIFIED when the certificate has expired", () => {
    const past = new Date(Date.now() - 5 * 86_400_000).toISOString();
    expect(verificationStatus(restaurant({ certificate: cert(past) }))).toBe(
      "UNVERIFIED",
    );
  });
});

describe("expiryState", () => {
  it("returns 'none' when there is no certificate", () => {
    expect(expiryState(undefined)).toBe("none");
  });

  it("returns 'valid' well before expiry (> 60 days)", () => {
    const far = new Date(Date.now() + 200 * 86_400_000).toISOString();
    expect(expiryState(cert(far))).toBe("valid");
  });

  it("returns 'expiring' within 60 days of expiry (icon+text, never hue alone)", () => {
    const soon = new Date(Date.now() + 30 * 86_400_000).toISOString();
    expect(expiryState(cert(soon))).toBe("expiring");
  });

  it("returns 'expired' after the expiry date", () => {
    const past = new Date(Date.now() - 1 * 86_400_000).toISOString();
    expect(expiryState(cert(past))).toBe("expired");
  });
});

describe("formatDate", () => {
  it("renders a human date, never raw ISO", () => {
    expect(formatDate("2026-08-12T00:00:00Z")).toMatch(/Aug 12, 2026/);
  });
});

describe("cardThumbSource (sc-183)", () => {
  it("returns the WIDEST imageSrcset variant so the card srcset downscales sharply", () => {
    const r = restaurant({
      imageThumbnailUrl: "/v1/listings/l-x/image?variant=thumbnail",
      imageSrcset: [
        { width: 400, url: "/v1/listings/l-x/image?variant=thumbnail" },
        { width: 768, url: "/v1/listings/l-x/image?variant=thumbnail_768" },
        { width: 1280, url: "/v1/listings/l-x/image?variant=thumbnail_1280" },
        { width: 1920, url: "/v1/listings/l-x/image?variant=thumbnail_1920" },
      ],
    });
    expect(cardThumbSource(r)).toBe("/v1/listings/l-x/image?variant=thumbnail_1920");
  });

  it("falls back to the small thumbnail URL when no srcset is published (pre-ingest/legacy)", () => {
    const r = restaurant({
      imageThumbnailUrl: "/v1/listings/l-x/image?variant=thumbnail",
    });
    expect(cardThumbSource(r)).toBe("/v1/listings/l-x/image?variant=thumbnail");
  });

  it("returns undefined when there is no image at all (render the placeholder)", () => {
    expect(cardThumbSource(restaurant({}))).toBeUndefined();
  });
});

describe("searchListings", () => {
  it("filters by cutting method when specified", () => {
    expect(searchListings("", "HAND_CUT").every((r) => r.cuttingMethod === "HAND_CUT")).toBe(
      true,
    );
    expect(searchListings("", "HAND_CUT").length).toBeGreaterThan(0);
  });

  it("returns everything for an empty query + ALL filter", () => {
    const all = searchListings("");
    expect(all.length).toBeGreaterThan(0);
  });

  it("searches name, cuisine, and address", () => {
    expect(searchListings("karachi").some((r) => r.name === "Karachi Kitchen")).toBe(
      true,
    );
    expect(searchListings("kebab").length).toBe(0);
  });

  it("sorts by distance ascending", () => {
    const distances = searchListings("").map((r) => r.distanceMi ?? 0);
    const sorted = [...distances].sort((a, b) => a - b);
    expect(distances).toEqual(sorted);
  });
});