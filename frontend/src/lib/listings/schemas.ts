import { z } from "zod";

/**
 * Zod schema for the Add Restaurant Listing form (sc-138).
 *
 * Mirrors the backend POST /v1/listings contract (CreateListingRequest):
 * name, address, lat, lng, cuisine, isHandCut — name/address/lat/lng/cuisine
 * are required, isHandCut optional. Range
 * constraints clone the domain LatLng bounds (lat [-90,90], lng [-180,180])
 * and Cuisine.length <= 64 so invalid input fails fast client-side before any
 * network call.
 *
 * lat/lng are modelled as strings here: an empty input must be rejected as
 * "required", never coerced to a valid 0. The page converts them to Number on
 * submit (the API body expects doubles).
 */
const coordinateSchema = (min: number, max: number, label: string) =>
  z
    .string()
    .trim()
    .min(1, `${label} is required.`)
    .refine((s) => s !== "" && !Number.isNaN(Number(s)), `${label} must be a number.`)
    .refine((s) => {
      const n = Number(s);
      return n >= min && n <= max;
    }, `${label} must be between ${min} and ${max}.`);

export const createListingSchema = z.object({
  name: z.string().trim().min(1, "Restaurant name is required."),
  address: z.string().trim().min(1, "Address is required."),
  lat: coordinateSchema(-90, 90, "Latitude"),
  lng: coordinateSchema(-180, 180, "Longitude"),
  cuisine: z
    .string()
    .trim()
    .min(1, "Cuisine is required.")
    .max(64, "Cuisine must be 64 characters or fewer."),
  /**
   * sc-42: hand-cut is an optional on/off extra. `undefined` (unchecked) leaves
   * the listing's hand-cut state unknown to the backend; `true` records hand-cut
   * (Zabiha). The either/or cutting-method enum was dropped upstream.
   */
  isHandCut: z.boolean().optional(),
});

export type CreateListingFormValues = z.infer<typeof createListingSchema>;