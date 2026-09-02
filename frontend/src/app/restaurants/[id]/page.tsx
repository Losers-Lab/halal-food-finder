"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { MobileTabBar } from "@/components/layout/MobileTabBar";
import {
  DeliveryIndicator,
  HandCutIndicator,
  SealMark,
  UnverifiedTag,
  VerifiedBadge,
} from "@/components/trust";
import { CertificatePanel } from "@/components/detail/CertificatePanel";
import { MapPreview } from "@/components/detail/MapPreview";
import { RestaurantPhoto } from "@/components/listing/RestaurantPhoto";
import { FavoriteButton } from "@/components/listing/FavoriteButton";
import type { Restaurant } from "@/lib/listings/restaurants";
import {
  expiryState,
  verificationStatus,
} from "@/lib/listings/restaurants";
import { getRestaurant } from "@/lib/listings/data";

/**
 * Restaurant detail — docs/design/detail-page.md. Route /restaurants/[id].
 * Backend detail read (sc-171) is keyed by the listing's UUID, so the route
 * param is the `id` (the backend has no slug; cards link to /restaurants/{id}).
 * Trust centerpiece = CertificatePanel (verified) or the quiet unverified panel.
 * Actions (max 3, priority order): Directions, Call (if phone), Website (if any).
 * States: loading skeleton / not-found / fetch-error / success.
 */
export default function RestaurantDetailPage() {
  const params = useParams<{ id: string }>();
  const id = Array.isArray(params?.id) ? params.id[0] : params?.id ?? "";

  const [restaurant, setRestaurant] = useState<Restaurant | null>(null);
  const [status, setStatus] = useState<"loading" | "success" | "error">(
    "loading",
  );
  const [reload, setReload] = useState(0);

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
        setStatus("success");
      })
      .catch(() => {
        if (cancelled) return;
        setStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [id, reload]);

  return (
    <div className="min-h-screen bg-cream-50">
      <SiteHeader />
      <main className="mx-auto max-w-[1200px] px-5 py-8 pb-32 lg:pb-8">
        <BackLink />

        {status === "loading" ? <DetailSkeleton /> : null}

        {status === "error" ? (
          <StatePanel
            title="We couldn't load this restaurant."
            detail="Something went wrong on our end. Try again."
            actionLabel="Retry"
            actionKind="retry"
            onAction={() => setReload((n) => n + 1)}
          />
        ) : null}

        {status === "success" && !restaurant ? (
          <StatePanel
            title="We couldn't find that restaurant."
            detail="It may have been removed or the link is out of date."
            actionLabel="Browse all spots"
            actionKind="link"
            actionHref="/search"
          />
        ) : null}

        {status === "success" && restaurant ? (
          <RestaurantDetail restaurant={restaurant} />
        ) : null}
      </main>
      <SiteFooter />
      <MobileTabBar />
    </div>
  );
}

function BackLink() {
  const router = useRouter();
  return (
    <div className="mb-6">
      <button
        type="button"
        onClick={() => {
          if (window.history.length > 1) router.back();
          else router.push("/search?q=");
        }}
        className="text-small text-ink-700 underline-offset-2 hover:text-brand-600 hover:underline"
      >
        ← Back to results
      </button>
    </div>
  );
}

function RestaurantDetail({ restaurant }: { restaurant: Restaurant }) {
  const verified = verificationStatus(restaurant) === "VERIFIED";
  const certState = verified ? expiryState(restaurant.certificate) : "none";
  // An expired cert downgrades the badge everywhere (detail-page.md §1.2).
  const showVerifiedBadge = verified && certState !== "expired";

  return (
    <div>
      {/* Hero — full-res variant only, eager (sc-157) */}
      <div className="relative aspect-[16/7] overflow-hidden rounded-lg border border-kraft-200 shadow-card">
        <RestaurantPhoto
          src={restaurant.imageUrl}
          alt={restaurant.name}
          sizes="(min-width: 1200px) 1200px, 100vw"
          eager
        />
        {showVerifiedBadge ? (
          <div className="absolute right-4 top-4">
            <VerifiedBadge variant="on-photo" />
          </div>
        ) : null}
      </div>

      {/* Title + actions */}
      <div className="mt-6 flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <h1 className="text-[2rem] font-extrabold leading-[2.375rem] text-ink-900" style={{ fontFamily: "var(--font-display)" }}>
              {restaurant.name}
            </h1>
            {verified ? (
              showVerifiedBadge ? (
                <VerifiedBadge />
              ) : (
                <UnverifiedTag />
              )
            ) : (
              <UnverifiedTag />
            )}
          </div>
          <p className="mt-1 text-body text-ink-500">
            {[restaurant.cuisine, restaurant.distanceMi != null ? `${restaurant.distanceMi.toFixed(1)} mi` : null, restaurant.rating != null ? `★ ${restaurant.rating.toFixed(1)} (${restaurant.reviewCount ?? 0})` : null]
              .filter(Boolean)
              .join(" · ")}
          </p>
          {restaurant.isHandCut === true ? (
            <div className="mt-3">
              <HandCutIndicator />
            </div>
          ) : null}
          {restaurant.isDelivery === true ? (
            <div className="mt-3">
              <DeliveryIndicator />
            </div>
          ) : null}
        </div>

        <div className="flex flex-col gap-2 sm:flex-row lg:flex-col xl:flex-row lg:items-stretch">
          <FavoriteButton
            listingId={restaurant.id}
            restaurant={restaurant}
            variant="detail"
          />
          <ActionChip
            label="Directions"
            href={`https://www.google.com/maps/dir/?api=1&destination=${restaurant.lat},${restaurant.lng}`}
            external
          />
          {restaurant.phone ? (
            <ActionChip label="Call" href={`tel:${restaurant.phone}`} />
          ) : null}
          {restaurant.website ? (
            <ActionChip label="Website" href={restaurant.website} external ghost />
          ) : null}
        </div>
      </div>

      {/* Certificate panel | Sidebar */}
      <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-start">
        <div>
          {restaurant.certificate && verified ? (
            <CertificatePanel certificate={restaurant.certificate} name={restaurant.name} />
          ) : (
            <UnverifiedPanel />
          )}
        </div>
        <Sidebar restaurant={restaurant} />
      </div>
    </div>
  );
}

function ActionChip({
  label,
  href,
  external,
  ghost,
}: {
  label: string;
  href: string;
  external?: boolean;
  ghost?: boolean;
}) {
  const cls = ghost
    ? "inline-flex h-11 items-center justify-center rounded-md border-[1.5px] border-kraft-200 bg-ink-0 px-5 text-label text-ink-700 shadow-chip hover:bg-ink-100"
    : "inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-5 text-label text-cream-50 shadow-chip hover:bg-ink-700";
  return (
    <a
      className={cls}
      href={href}
      target={external ? "_blank" : undefined}
      rel={external ? "noreferrer" : undefined}
    >
      {label}
    </a>
  );
}

function Sidebar({ restaurant }: { restaurant: Restaurant }) {
  return (
    <div className="grid gap-6">
      {restaurant.hours ? <Hours hours={restaurant.hours} /> : null}
      <section aria-label="Location">
        <h2 className="text-heading text-ink-900">Location</h2>
        <p className="mt-2 text-body text-ink-700">{restaurant.address}</p>
        {/* sc-187: embedded map preview with a location pin from the listing's lat/lng. */}
        <div className="mt-3">
          <MapPreview
            lat={restaurant.lat}
            lng={restaurant.lng}
            restaurantName={restaurant.name}
          />
        </div>
        <a
          className="mt-2 inline-block text-label text-brand-500 underline-offset-2 hover:underline"
          href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(restaurant.address)}`}
          target="_blank"
          rel="noreferrer"
        >
          Get directions
        </a>
      </section>
    </div>
  );
}

function Hours({ hours }: { hours: NonNullable<Restaurant["hours"]> }) {
  const todayIdx = (new Date().getDay() + 6) % 7; // Mon=0..Sun=6
  return (
    <section aria-label="Hours">
      <h2 className="text-heading text-ink-900">Hours</h2>
      <ul className="mt-2 divide-y divide-kraft-100 text-body">
        {hours.map((h, i) => {
          const isToday = i === todayIdx;
          return (
            <li
              key={h.day}
              className={`flex items-center justify-between py-1.5 ${
                isToday ? "font-semibold text-ink-900" : "text-ink-700"
              }`}
            >
              <span>{h.day}</span>
              <span className="flex items-center gap-2">
                {h.value}
                {isToday ? (
                  <span className="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-small font-medium text-stamp-700" style={{ backgroundColor: "var(--color-stamp-50)" }}>
                    <span aria-hidden="true">●</span> Open
                  </span>
                ) : null}
              </span>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function UnverifiedPanel() {
  return (
    <section
      aria-label="Verification status"
      className="rounded-lg border-[1.5px] border-ink-300 bg-ink-100 p-6"
    >
      <p className="text-body text-ink-700">
        This listing hasn&apos;t been verified yet. Owners can claim it and submit
        their certification for review.
      </p>
      <Link
        href="/how-verification-works"
        className="mt-3 inline-block text-label text-brand-500 underline-offset-2 hover:underline"
      >
        How verification works
      </Link>
    </section>
  );
}

function StatePanel({
  title,
  detail,
  actionLabel,
  actionKind,
  onAction,
  actionHref,
}: {
  title: string;
  detail: string;
  actionLabel: string;
  actionKind: "retry" | "link";
  onAction?: () => void;
  actionHref?: string;
}) {
  return (
    <div className="rounded-lg border border-kraft-300 bg-ink-0 p-10 text-center shadow-card">
      <SealMark className="mx-auto h-12 w-12 text-ink-300" srLabel="Empty" />
      <h2 className="mt-4 text-title text-ink-900">{title}</h2>
      <p className="mx-auto mt-2 max-w-md text-body text-ink-500">{detail}</p>
      <div className="mt-6">
        {actionKind === "retry" ? (
          <button
            type="button"
            onClick={onAction}
            className="inline-flex h-11 items-center justify-center rounded-full bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
          >
            {actionLabel}
          </button>
        ) : (
          <Link
            href={actionHref ?? "/search"}
            className="inline-flex h-11 items-center justify-center rounded-full bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
          >
            {actionLabel}
          </Link>
        )}
      </div>
    </div>
  );
}

function DetailSkeleton() {
  return (
    <div aria-busy="true" className="space-y-6">
      <div className="aspect-[16/7] rounded-lg bg-ink-100" />
      <div className="h-8 w-64 rounded bg-ink-100" />
      <div className="h-4 w-72 rounded bg-ink-100" />
      <div className="grid gap-6 lg:grid-cols-2">
        <div className="h-64 rounded-lg border-[1.5px] border-dashed border-stamp-200 bg-ink-0" />
        <div className="h-64 rounded-lg bg-ink-100" />
      </div>
    </div>
  );
}