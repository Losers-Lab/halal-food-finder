import { describe, expect, it } from "vitest";
import {
  createListingSchema,
  CUTTING_METHOD_OPTIONS,
} from "./schemas";

const valid = {
  name: "  Al-Amir Grill  ",
  address: "  123 Main St  ",
  lat: "40.7128",
  lng: "-74.0060",
  cuisine: "Middle Eastern",
  cuttingMethod: "HAND_CUT",
} as const;

describe("createListingSchema (sc-138)", () => {
  it("accepts a valid payload and trims name/address/cuisine", () => {
    const result = createListingSchema.parse(valid);
    expect(result.name).toBe("Al-Amir Grill");
    expect(result.address).toBe("123 Main St");
    // cuisines are trimmed client-side; the backend lowercases for storage.
    expect(result.cuisine).toBe("Middle Eastern");
  });

  it("rejects a blank name", () => {
    const result = createListingSchema.safeParse({ ...valid, name: "   " });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].path).toEqual(["name"]);
    }
  });

  it("rejects a blank address", () => {
    const result = createListingSchema.safeParse({ ...valid, address: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.issues[0].path).toEqual(["address"]);
    }
  });

  it("rejects a blank cuisine (required)", () => {
    const result = createListingSchema.safeParse({ ...valid, cuisine: "" });
    expect(result.success).toBe(false);
  });

  it("rejects a cuisine longer than 64 characters", () => {
    const result = createListingSchema.safeParse({
      ...valid,
      cuisine: "x".repeat(65),
    });
    expect(result.success).toBe(false);
  });

  it("rejects a blank lat as required, never coercing to a valid 0", () => {
    const result = createListingSchema.safeParse({ ...valid, lat: "" });
    expect(result.success).toBe(false);
  });

  it("rejects a non-numeric lat", () => {
    const result = createListingSchema.safeParse({ ...valid, lat: "abc" });
    expect(result.success).toBe(false);
  });

  it("rejects lat out of [-90, 90]", () => {
    expect(
      createListingSchema.safeParse({ ...valid, lat: "91" }).success,
    ).toBe(false);
    expect(
      createListingSchema.safeParse({ ...valid, lat: "-91" }).success,
    ).toBe(false);
  });

  it("rejects lng out of [-180, 180]", () => {
    expect(
      createListingSchema.safeParse({ ...valid, lng: "181" }).success,
    ).toBe(false);
    expect(
      createListingSchema.safeParse({ ...valid, lng: "-181" }).success,
    ).toBe(false);
  });

  it("rejects an unknown cuttingMethod", () => {
    const result = createListingSchema.safeParse({
      ...valid,
      cuttingMethod: "SOMETHING_ELSE",
    });
    expect(result.success).toBe(false);
  });

  it("exposes all three cutting-method options for the select (incl. UNSPECIFIED)", () => {
    expect(CUTTING_METHOD_OPTIONS.map((o) => o.value)).toEqual([
      "HAND_CUT",
      "MACHINE_CUT",
      "UNSPECIFIED",
    ]);
  });
});