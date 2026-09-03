"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ListingForm } from "@/components/listing/ListingForm";
import { UnverifiedTag, VerifiedBadge } from "@/components/trust";
import { api, ApiError, type ListingResponse } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { getRestaurant } from "@/lib/listings/data";
import type { CreateListingFormValues } from "@/lib/listings/schemas";
import type { Restaurant } from "@/lib/listings/restaurants";
import { verificationStatus } from "@/lib/listings/restaurants";

/**
 * Owner listing edit — route /restaurants/[id]/edit (sc-23/47/48), the
 * manage-listing screen.
 *
 * The backend's update endpoint is PATCH /v1/listings/{id}: a FULL replace of
 * the editable content fields, reusing the Add Listing request shape, so this
 * screen renders the same shared `ListingForm` as the add screen (pre-filled
 * from a live GET /v1/listings/{id} read). Owner, verification status, price
 * and rating are preserved server-side on edit.
 *
 * Ownership is enforced server-side (a non-owner gets 403 `not_listing_owner`).
 * The public detail read surface does not expose `ownerId` and there is no
 * "my listings" endpoint, so the frontend cannot statically confirm ownership
 * before rendering an edit affordance; the owner reaches this screen via the
 * add-listing success card (where the creator is provably the owner) or by
 * direct URL. A non-owner who lands here sees the honest 403 error on save.
 *
 * States: auth restore hold / not-signed-in prompt / unknown listing (404) /
 * load error (retry) / form (success) / saved (reflects updated data).
 */
export default function EditListingPage() {
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params?.id) ? params.id[0] : params?.id ?? "";
  const { session, restoring } = useAuth();

  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [status, setStatus] = useState<
    "loading" | "success" | "error" | "not-found"
  >("loading");
  const [reload, setReload] = useState(0);
  const [saved, setSaved] = useState<ListingResponse | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  // Load the listing so the form can be pre-filled from its live data.
  useEffect(() => {
    let cancelled = false;
    Promise.resolve()
      .then(() => {
        if (cancelled) return undefined;
        setStatus("loading");
        return getRestaurant(id);
      })
      .then((r) => {
        if (cancelled) return;
        setRestaurant(r ?? null);
        setStatus(r ? "success" : "not-found");
      })
      .catch(() => {
        if (cancelled) return;
        setStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [id, reload]);

  async function onSubmit(values: CreateListingFormValues) {
    setServerError(null);
    try {
      const updated = await api.updateListing(id, {
        name: values.name,
        address: values.address,
        lat: Number(values.lat),
        lng: Number(values.lng),
        cuisine: values.cuisine,
        // sc-42/sc-184: omit when unchecked → unknown (null) on the full-replace
        // PATCH; checked → claimed. Round-tripped so an edit never silently
        // clears a previously claimed flag.
        ...(values.isHandCut ? { isHandCut: true } : {}),
        ...(values.isDelivery ? { isDelivery: true } : {}),
      });
      setSaved(updated);
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.code === "invalid_input" && err.detail) {
          // sc-134 fail-fast: backend concatenated per-field messages.
          setServerError(err.detail);
          return;
        }
        if (err.code === "not_listing_owner" || err.status === 403) {
          setServerError(
            "You can only edit a listing you own. This listing belongs to a different account.",
          );
          return;
        }
        if (err.status === 401) {
          setServerError(
            "Your session has expired. Please log in again to save changes.",
          );
          return;
        }
        if (err.status === 404) {
          setServerError(
            "This listing was not found. It may have been removed.",
          );
          return;
        }
      }
      setServerError("Something went wrong. Please try again.");
    }
  }

  // While a persisted session is re-materializing, hold a neutral placeholder.
  if (restoring && !session) {
    return <RestorePlaceholder />;
  }

  // The PATCH endpoint requires an authenticated account (401 otherwise).
  if (!session) {
    return <SignedOutPrompt />;
  }

  return (
    <main className="mx-auto max-w-lg px-5 py-12">
      <p className="mb-4 text-small text-ink-500">
        <Link
          href={`/restaurants/${encodeURIComponent(id)}`}
          className="underline-offset-2 hover:text-brand-600 hover:underline"
        >
          ← Back to listing
        </Link>
      </p>

      {status === "loading" ? (
        <div aria-busy="true" className="h-40 animate-pulse rounded-lg bg-ink-100" />
      ) : null}

      {status === "error" ? (
        <LoadErrorPanel onRetry={() => setReload((n) => n + 1)} />
      ) : null}

      {status === "not-found" ? <NotFoundPanel /> : null}

      {status === "success" && restaurant ? (
        <EditCard
          restaurant={restaurant}
          saved={saved}
          serverError={serverError}
          onSubmit={onSubmit}
        />
      ) : null}
    </main>
  );
}

/** Pre-fill the shared listing form from the live listing read. */
function toFormValues(r: Restaurant): CreateListingFormValues {
  return {
    name: r.name,
    address: r.address,
    lat: String(r.lat),
    lng: String(r.lng),
    cuisine: r.cuisine,
    // Tri-state → boolean: only a claimed flag is shown checked; unchecked on
    // submit is omitted (unknown), so an unclaimed flag stays unclaimed.
    isHandCut: r.isHandCut === true,
    isDelivery: r.isDelivery === true,
  };
}

function EditCard({
  restaurant,
  saved,
  serverError,
  onSubmit,
}: {
  restaurant: Restaurant;
  saved: ListingResponse | null;
  serverError: string | null;
  onSubmit: (values: CreateListingFormValues) => Promise<void>;
}) {
  const verified = verificationStatus(restaurant) === "VERIFIED";

  if (saved) {
    return (
      <div className="rounded-lg border border-kraft-200 bg-ink-0 p-6 shadow-card">
        <div className="flex items-center justify-between gap-3">
          <h1 className="text-title text-ink-900">Changed saved</h1>
          {verified ? <VerifiedBadge /> : <UnverifiedTag />}
        </div>
        <p className="mt-3 text-body text-ink-500">
          <span className="font-medium text-ink-900">{saved.name}</span>
          &apos;s details are up to date.{" "}
          {verified
            ? "Verification status is preserved on edit."
            : "The listing is still unverified."}
        </p>
        <div className="mt-6 flex flex-wrap items-center gap-3">
          <Link
            href={`/restaurants/${encodeURIComponent(saved.id)}`}
            className="inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-body font-medium text-cream-50 shadow-chip hover:bg-brand-600"
          >
            View listing
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div>
      <header className="mb-6">
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-title text-ink-900">Edit listing</h1>
          {verified ? <VerifiedBadge /> : <UnverifiedTag />}
        </div>
        <p className="mt-2 text-body text-ink-500">
          Update the details for{" "}
          <span className="font-medium text-ink-700">{restaurant.name}</span>.
          Changes apply immediately.
        </p>
      </header>
      <ListingForm
        onSubmit={onSubmit}
        submitLabel="Save changes"
        submittingLabel="Saving changes…"
        serverError={serverError}
        defaultValues={toFormValues(restaurant)}
      />
    </div>
  );
}

function RestorePlaceholder() {
  return (
    <main className="mx-auto max-w-lg px-5 py-12">
      <div aria-busy="true" className="h-40 animate-pulse rounded-lg bg-ink-100" />
    </main>
  );
}

function SignedOutPrompt() {
  return (
    <main className="mx-auto max-w-lg px-5 py-12 text-center">
      <h1 className="text-title text-ink-900">Edit a listing</h1>
      <p className="mt-2 text-body text-ink-500">
        You need to be signed in to edit a listing.
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

function LoadErrorPanel({ onRetry }: { onRetry: () => void }) {
  return (
    <div
      role="alert"
      className="rounded-lg border-[1.5px] border-danger-100 bg-danger-50 p-8 text-center"
    >
      <h2 className="text-title text-ink-900">We couldn&apos;t load this listing.</h2>
      <p className="mt-2 text-body text-ink-500">
        Something went wrong on our end. Try again.
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-4 inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
      >
        Retry
      </button>
    </div>
  );
}

function NotFoundPanel() {
  return (
    <div className="rounded-lg border border-kraft-300 bg-ink-0 p-10 text-center shadow-card">
      <h2 className="text-title text-ink-900">Listing not found</h2>
      <p className="mx-auto mt-2 max-w-md text-body text-ink-500">
        This listing may have been removed, or the link is out of date.
      </p>
      <div className="mt-6">
        <Link
          href="/search"
          className="inline-flex h-11 items-center justify-center rounded-full bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
        >
          Browse listings
        </Link>
      </div>
    </div>
  );
}