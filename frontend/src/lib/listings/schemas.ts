import { z } from "zod";

/**
 * Zod schema for the Add Restaurant Listing form (sc-138).
 *
 * Mirrors the backend POST /v1/listings contract (CreateListingRequest):
 * name, address, lat, lng, cuisine, cuttingMethod — all required. Range
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

export const cuttingMethodSchema = z.enum([
  "HAND_CUT",
  "MACHINE_CUT",
  "UNSPECIFIED",
]);

export type CuttingMethod = z.infer<typeof cuttingMethodSchema>;

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
  cuttingMethod: cuttingMethodSchema,
});

export type CreateListingFormValues = z.infer<typeof createListingSchema>;

/** Human labels for the cutting-method choices, in enum order. */
export const CUTTING_METHOD_OPTIONS: {
  value: CuttingMethod;
  label: string;
  helper: string;
}[] = [
  {
    value: "HAND_CUT",
    label: "Hand-cut",
    helper: "Animal slaughtered by hand (Zabiha)",
  },
  {
    value: "MACHINE_CUT",
    label: "Machine-cut",
    helper: "Slaughtered by an automated mechanical process",
  },
  {
    value: "UNSPECIFIED",
    label: "Not sure / prefer not to say",
    helper: "Recorded as unspecified; owners can update it on claim",
  },
];