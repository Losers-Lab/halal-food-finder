"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { MobileTabBar } from "@/components/layout/MobileTabBar";
import { ListingGrid } from "@/components/listing/ListingGrid";
import { SealMark } from "@/components/trust";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useFavorites } from "@/lib/favorites/FavoritesProvider";

/**
 * My saved spots — route /saved (sc-52), the "Saved" bottom-tab destination.
 * Reads the authenticated user's favorites (GET /v1/favorites) through the
 * FavoritesProvider and renders them as a standard browse grid. Unfavouriting
 * a card removes it optimistically (provider), so the list shrinks live.
 *
 * States: auth re-materializing (hold) / anonymous (log-in prompt) / loading
 * (grid skeleton) / error (retry) / empty (browse CTA) / success (grid).
 */
export default function SavedPage() {
  const { session, restoring } = useAuth();
  const { favorites, status, retryFavorites } = useFavorites();

  return (
    <div className="min-h-screen bg-cream-50">
      <SiteHeader />
      <main className="mx-auto max-w-[1200px] px-5 py-12 pb-32">
        <header>
          <h1 className="text-title text-ink-900">Saved spots</h1>
          <p className="mt-2 text-body text-ink-500">
            Restaurants you&apos;ve favourited, ready to revisit.
          </p>
        </header>

        <div className="mt-8">
          {restoring && !session ? (
            <div
              aria-busy="true"
              className="h-40 animate-pulse rounded-lg bg-ink-100"
            />
          ) : null}

          {!restoring && !session ? (
            <div className="rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 p-10 text-center shadow-card">
              <SealMark
                className="mx-auto h-12 w-12 text-ink-300"
                srLabel="Signed out"
              />
              <h2 className="mt-4 text-title text-ink-900">
                Log in to see your saved spots
              </h2>
              <p className="mx-auto mt-2 max-w-md text-body text-ink-500">
                Your favourites are tied to your account. Sign in to keep and
                revisit them.
              </p>
              <Link
                href="/login"
                className="mt-6 inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-body font-medium text-cream-50 shadow-chip hover:bg-brand-600"
              >
                Log in
              </Link>
            </div>
          ) : null}

          {session && status === "loading" ? (
            <ListingGrid restaurants={[]} status="loading" />
          ) : null}

          {session && status === "error" ? (
            <div
              role="alert"
              className="rounded-lg border-[1.5px] border-danger-100 bg-danger-50 p-8 text-center"
            >
              <h2 className="text-title text-ink-900">
                We couldn&apos;t load your saved spots.
              </h2>
              <button
                type="button"
                onClick={retryFavorites}
                className="mt-4 inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
              >
                Retry
              </button>
            </div>
          ) : null}

          {session && status === "ready" && favorites.length === 0 ? (
            <div className="rounded-lg border border-dashed border-kraft-300 bg-ink-0 p-10 text-center">
              <SealMark
                className="mx-auto h-12 w-12 text-ink-300"
                srLabel="Empty"
              />
              <h2 className="mt-4 text-title text-ink-900">
                No saved spots yet
              </h2>
              <p className="mx-auto mt-2 max-w-md text-body text-ink-500">
                Tap the heart on any restaurant to save it here for later.
              </p>
              <Link
                href="/search"
                className="mt-6 inline-flex h-11 items-center justify-center rounded-full bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
              >
                Browse restaurants
              </Link>
            </div>
          ) : null}

          {session && status === "ready" && favorites.length > 0 ? (
            <ListingGrid
              restaurants={favorites}
              status="success"
              onRetry={retryFavorites}
            />
          ) : null}
        </div>
      </main>
      <SiteFooter />
      <MobileTabBar />
    </div>
  );
}