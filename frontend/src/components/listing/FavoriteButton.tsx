"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useFavorites } from "@/lib/favorites/FavoritesProvider";
import type { Restaurant } from "@/lib/listings/restaurants";

/**
 * FavoriteButton — the authenticated save/un-save control (sc-50/51/52).
 *
 * Two visual variants:
 *   - "card"   : small circular heart, absolute-positioned by the parent (e.g.
 *                top-left of the card photo). No label.
 *   - "detail" : a labelled chip (heart + "Save"/"Saved") that slots into the
 *                detail page's action row alongside Directions / Call / Website.
 *
 * Behavior:
 *   - Anonymous → rendered as a heart link to /login (favorites require auth;
 *     the backend would 401 otherwise).
 *   - Logged in → an optimistic toggle via the FavoritesProvider; the heart
 *     flips immediately and reverts on failure. Disabled while the favorites
 *     list loads or the specific toggle is in flight (never flash the wrong
 *     state, never double-fire).
 */
function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="h-5 w-5"
      fill={filled ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z" />
    </svg>
  );
}

export function FavoriteButton({
  listingId,
  restaurant,
  variant = "detail",
  className = "",
}: {
  listingId: string;
  /** Pass the read-model card so a fresh favourite joins the /saved list. */
  restaurant?: Restaurant;
  variant?: "card" | "detail";
  className?: string;
}) {
  const { session, restoring } = useAuth();
  const {
    isFavorited,
    isPending,
    toggleFavorites,
    status,
    favoritesError,
    clearFavoritesError,
  } = useFavorites();

  // Hold a neutral placeholder (disabled) while auth re-materializes or the
  // favorites list loads — never guess the heart state.
  const loading = restoring || status === "loading";
  const favorited = isFavorited(listingId);
  const pending = isPending(listingId);
  const disabled = loading || pending;

  const base =
    variant === "card"
      ? "inline-flex h-9 w-9 items-center justify-center rounded-full border border-kraft-300 bg-ink-0/90 shadow-chip backdrop-blur transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand-500"
      : "inline-flex h-11 items-center justify-center gap-2 rounded-md border-[1.5px] px-4 text-label shadow-chip transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500";
  const tone = favorited
    ? variant === "card"
      ? " text-brand-500"
      : " border-brand-200 bg-brand-50 text-brand-600"
    : variant === "card"
      ? " text-ink-700 hover:text-brand-500"
      : " border-kraft-200 bg-ink-0 text-ink-700 hover:border-brand-200 hover:text-brand-600";
  const disabledCls = disabled ? " opacity-60" : "";
  const cls = `${base}${tone}${disabledCls}`;

  // Anonymous: favorites are authenticated — route to login, never a 401.
  if (!session) {
    return (
      <Link
        href="/login"
        aria-label="Save to favorites — log in"
        className={`${cls} ${className}`}
      >
        <HeartIcon filled={false} />
        {variant === "detail" ? <span>Save</span> : null}
      </Link>
    );
  }

  const label = favorited ? "Remove from favorites" : "Save to favorites";
  return (
    <>
      <button
        type="button"
        aria-pressed={favorited}
        aria-label={label}
        title={label}
        disabled={disabled}
        onClick={() => {
          if (!disabled) {
            clearFavoritesError();
            toggleFavorites(listingId, restaurant);
          }
        }}
        className={`${cls} ${className}`}
      >
        <HeartIcon filled={favorited} />
        {variant === "detail" ? (
          <span>{favorited ? "Saved" : "Save"}</span>
        ) : null}
      </button>
      {variant === "detail" && favoritesError ? (
        <p role="alert" className="text-small text-danger-600">
          {favoritesError}
        </p>
      ) : null}
    </>
  );
}