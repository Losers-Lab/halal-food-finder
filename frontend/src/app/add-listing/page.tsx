"use client";

import { useState } from "react";
import Link from "next/link";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { Alert } from "@/components/auth/Alert";
import { Button } from "@/components/auth/Button";
import { Field } from "@/components/auth/Field";
import { SelectField } from "@/components/listings/SelectField";
import { UnverifiedTag } from "@/components/trust";
import { api, ApiError, type ListingResponse } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import {
  createListingSchema,
  CUTTING_METHOD_OPTIONS,
  type CreateListingFormValues,
} from "@/lib/listings/schemas";

/**
 * Add Restaurant Listing (sc-138) — POST /v1/listings.
 *
 * Listing-first model: anyone can add a restaurant, and it is created always
 * UNVERIFIED (honestly labelled — never premium-styled). The authenticated
 * account owns the listing. The backend does NOT geocode (sc-138 decision:
 * geocoding stays out of the write-path), so the client supplies lat/lng
 * directly; address-autocomplete is a future server-side seam
 * (docs/reviews/sc-138-external-services.md §3), not part of this form.
 */
export default function AddListingPage() {
  const { session, restoring } = useAuth();

  const [created, setCreated] = useState<ListingResponse | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<CreateListingFormValues>({
    resolver: zodResolver(createListingSchema),
    defaultValues: {
      name: "",
      address: "",
      lat: "",
      lng: "",
      cuisine: "",
      cuttingMethod: "UNSPECIFIED",
    },
  });

  async function onSubmit(values: CreateListingFormValues) {
    setServerError(null);
    try {
      const listing = await api.createListing({
        name: values.name,
        address: values.address,
        lat: Number(values.lat),
        lng: Number(values.lng),
        cuisine: values.cuisine,
        cuttingMethod: values.cuttingMethod,
      });
      setCreated(listing);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "invalid_input" && err.detail) {
          // sc-134 fail-fast: the backend concatenates per-field messages; a
          // single banner is the honest, unambiguous surface for this shape.
          setServerError(err.detail);
          return;
        }
        if (err.status === 401) {
          setServerError(
            "Your session has expired. Please log in again to add a listing.",
          );
          return;
        }
      }
      setServerError("Something went wrong. Please try again.");
    }
  }

  // While a persisted session is re-materializing, hold a neutral placeholder
  // instead of flashing between the logged-out prompt and the form.
  if (restoring && !session) {
    return (
      <main className="mx-auto max-w-lg px-5 py-12">
        <div
          aria-busy="true"
          className="h-40 animate-pulse rounded-lg bg-ink-100"
        />
      </main>
    );
  }

  // The endpoint requires an authenticated account (401 otherwise) — gate the
  // form behind a session and offer the log-in destination rather than letting
  // the submit fail.
  if (!session) {
    return (
      <main className="mx-auto max-w-lg px-5 py-12 text-center">
        <h1 className="text-title text-ink-900">Add a restaurant</h1>
        <p className="mt-2 text-body text-ink-500">
          You need to be signed in to add a listing.
        </p>
        <div className="mt-6">
          <Link
            href="/login"
            className="inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-body font-medium text-cream-50 shadow-chip hover:bg-brand-600"
          >
            Log in to continue
          </Link>
        </div>
      </main>
    );
  }

  if (created) {
    return (
      <main className="mx-auto max-w-lg px-5 py-12">
        <div className="rounded-lg border border-kraft-200 bg-ink-0 p-6 shadow-card">
          <div className="flex items-center justify-between gap-3">
            <h1 className="text-title text-ink-900">Listing saved</h1>
            <UnverifiedTag />
          </div>
          <p className="mt-3 text-body text-ink-500">
            <span className="font-medium text-ink-900">
              {created.name}
            </span>{" "}
            is now on Tahir&#39;s List. This listing is currently{" "}
            <span className="font-medium text-ink-700">unverified</span> —
            the owner can claim it and submit their halal certification for
            review.
          </p>
          <div className="mt-6 flex flex-wrap items-center gap-3">
            <Button
              type="button"
              variant="ghost"
              onClick={() => {
                setCreated(null);
                setServerError(null);
                reset();
              }}
            >
              Add another listing
            </Button>
            <Link
              href="/"
              className="rounded-md px-2 py-1 text-small text-brand-500 hover:text-brand-600 hover:underline"
            >
              Back to home
            </Link>
          </div>
        </div>
      </main>
    );
  }

  return (
    <main className="mx-auto max-w-lg px-5 py-12">
      <div className="rounded-lg border border-kraft-200 bg-ink-0 p-6 shadow-card sm:p-8">
        <h1 className="text-title text-ink-900">Add a restaurant</h1>
        <p className="mt-2 text-body text-ink-500">
          Share a halal place you know. Listings start{" "}
          <span className="font-medium text-ink-700">unverified</span> and
          can be claimed by the owner later.
        </p>

        {serverError ? (
          <div className="mt-4">
            <Alert>{serverError}</Alert>
          </div>
        ) : null}

        <form onSubmit={handleSubmit(onSubmit)} noValidate className="mt-6 space-y-4">
          <Field
            label="Restaurant name"
            error={errors.name?.message}
            inputProps={{
              ...register("name"),
              type: "text",
              autoComplete: "organization",
              placeholder: "e.g. Al-Amir Grill",
              disabled: isSubmitting,
            }}
          />

          <Field
            label="Address"
            error={errors.address?.message}
            inputProps={{
              ...register("address"),
              type: "text",
              autoComplete: "street-address",
              placeholder: "Full street address",
              disabled: isSubmitting,
            }}
          />

          <div className="grid grid-cols-2 gap-4">
            <Field
              label="Latitude"
              helper="e.g. 40.7128"
              error={errors.lat?.message}
              inputProps={{
                ...register("lat"),
                type: "text",
                inputMode: "decimal",
                autoComplete: "off",
                disabled: isSubmitting,
              }}
            />
            <Field
              label="Longitude"
              helper="e.g. -74.0060"
              error={errors.lng?.message}
              inputProps={{
                ...register("lng"),
                type: "text",
                inputMode: "decimal",
                autoComplete: "off",
                disabled: isSubmitting,
              }}
            />
          </div>
          <p className="text-small text-ink-500">
            Coordinates place this listing on the map. Address-to-coordinate
            lookup is coming in a later release.
          </p>

          <Field
            label="Cuisine"
            error={errors.cuisine?.message}
            inputProps={{
              ...register("cuisine"),
              type: "text",
              placeholder: "e.g. Middle Eastern",
              disabled: isSubmitting,
            }}
          />

          <SelectField
            label="Cutting method"
            helper="How is the meat slaughtered? If unknown, choose the last option."
            error={errors.cuttingMethod?.message}
            inputProps={{
              ...register("cuttingMethod"),
              disabled: isSubmitting,
            }}
          >
            {CUTTING_METHOD_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </SelectField>

          <div className="pt-2">
            <Button
              type="submit"
              loading={isSubmitting}
              loadingLabel="Saving listing…"
              className="w-full"
            >
              Add listing
            </Button>
          </div>
        </form>
      </div>
    </main>
  );
}