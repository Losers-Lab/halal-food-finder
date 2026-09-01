"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { api } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { getFavorites } from "@/lib/listings/data";
import type { Restaurant } from "@/lib/listings/restaurants";

/**
 * Favorites store (sc-50/51/52) — the shared, authenticated heart-state for the
 * first stateful feature. Wraps the app (in the root layout, like AuthProvider)
 * so every surface (browse cards, detail page, /saved page) reads and writes
 * the SAME favorited set — no per-surface fetches, no drift between the heart
 * on a card and the one on the detail page or the /saved list.
 *
 * Data model: the backend exposes:
 *   GET    /v1/favorites          → browse-card objects (the /saved list)
 *   POST   /v1/favorites/{id}     → 204, idempotent (favourite)
 *   DELETE /v1/favorites/{id}     → 204, idempotent (unfavourite)
 * There is no per-listing "is it favourited" read, so the client derives heart
 * state from the favorites list it holds. `toggleFavorites` writes optimistically
 * and reverts on failure so toggling feels instant but never lies.
 *
 * Anonymous: the provider holds nothing and fetches nothing (GET /v1/favorites
 * would 401). Buttons gate on `session` and route anonymous users to /login.
 */
type FavoritesStatus = "idle" | "loading" | "ready" | "error";

type FavoritesContextValue = {
  /** The favourited listings (browse-card model) — the /saved list source. */
  favorites: Restaurant[];
  /** True while the favorites fetch is in flight. */
  status: FavoritesStatus;
  /** True while auth is re-materializing; hearts hold a neutral placeholder. */
  pendingAuth: boolean;
  isFavorited: (id: string) => boolean;
  /** True while a toggle for this listing is in flight (disable the button). */
  isPending: (id: string) => boolean;
  /** Optimistically toggle a listing; reverts on failure. Passing `restaurant`
   *  lets a fresh favourite (from a card/detail) be added to the /saved list. */
  toggleFavorites: (id: string, restaurant?: Restaurant) => void;
  /** Last (best-effort, short-lived) failure message from a toggle. */
  favoritesError: string | null;
  clearFavoritesError: () => void;
  /** Re-run the GET /v1/favorites fetch (error state retry). */
  retryFavorites: () => void;
};

const FavoritesContext = createContext<FavoritesContextValue | null>(null);

export function FavoritesProvider({ children }: { children: React.ReactNode }) {
  const { session, restoring } = useAuth();
  const accountId = session?.accountId ?? null;

  const [favorites, setFavorites] = useState<Restaurant[]>([]);
  const [status, setStatus] = useState<FavoritesStatus>("idle");
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());
  const [favoritesError, setFavoritesError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  // Which account's list is currently in `favorites`/`status`. Lets render
  // derive "loading for the current account" without synchronous setState in
  // the effect, and prevents leaking one account's list to a different account.
  const [loadedAccount, setLoadedAccount] = useState<string | null>(null);

  // Always-current snapshot for revert + to avoid stale closures in toggles.
  const favoritesRef = useRef<Restaurant[]>([]);
  useEffect(() => {
    favoritesRef.current = favorites;
  }, [favorites]);

  // Fetch the favorite list when a session appears (or on retry). Anonymous:
  // fetch nothing (the endpoint would 401). No synchronous setState here — the
  // "loading" state is derived from `loadedAccount`, and state only changes in
  // the async callbacks (react-hooks/set-state-in-effect).
  useEffect(() => {
    if (!accountId) return;
    let cancelled = false;
    getFavorites()
      .then((list) => {
        if (cancelled) return;
        setFavorites(list);
        setLoadedAccount(accountId);
        setStatus("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setLoadedAccount(accountId);
        setStatus("error");
        setFavoritesError("We couldn't load your saved spots.");
      });
    return () => {
      cancelled = true;
    };
  }, [accountId, reload]);

  // Anonymous (or a newly-signed-in account still fetching) never exposes
  // another account's list or a stale "ready". Derived, not reset-in-effect.
  const readyForCurrent = accountId !== null && loadedAccount === accountId;
  const effectiveFavorites = readyForCurrent ? favorites : [];
  const effectiveStatus: FavoritesStatus = !accountId
    ? "idle"
    : readyForCurrent
      ? status
      : "loading";

  const favoritedIds = useMemo(
    () => new Set(effectiveFavorites.map((f) => f.id)),
    [effectiveFavorites],
  );

  const isFavorited = useCallback(
    (id: string) => favoritedIds.has(id),
    [favoritedIds],
  );

  const isPending = useCallback(
    (id: string) => pendingIds.has(id),
    [pendingIds],
  );

  const toggleFavorites = useCallback(
    (id: string, restaurant?: Restaurant) => {
      if (!accountId || pendingIds.has(id)) return;
      const before = favoritesRef.current;
      const wasFavorited = before.some((f) => f.id === id);

      // Optimistic write — flip the heart and /saved list immediately.
      setFavorites((prev) =>
        wasFavorited
          ? prev.filter((f) => f.id !== id)
          : restaurant
            ? [restaurant, ...prev.filter((f) => f.id !== id)]
            : prev,
      );
      setPendingIds((prev) => new Set(prev).add(id));

      const request = wasFavorited
        ? api.unfavoriteListing(id)
        : api.favoriteListing(id);
      request
        .then(() => {
          setFavoritesError(null);
        })
        .catch(() => {
          // Revert the optimistic write — never let a failed toggle lie about
          // the server state.
          setFavorites(before);
          setFavoritesError(
            wasFavorited ? "We couldn't unsave that spot." : "We couldn't save that spot.",
          );
        })
        .finally(() => {
          setPendingIds((prev) => {
            const next = new Set(prev);
            next.delete(id);
            return next;
          });
        });
    },
    [accountId, pendingIds],
  );

  const clearFavoritesError = useCallback(() => setFavoritesError(null), []);
  const retryFavorites = useCallback(() => setReload((n) => n + 1), []);

  const value = useMemo(
    () => ({
      favorites: effectiveFavorites,
      status: effectiveStatus,
      pendingAuth: restoring,
      isFavorited,
      isPending,
      toggleFavorites,
      favoritesError,
      clearFavoritesError,
      retryFavorites,
    }),
    [
      effectiveFavorites,
      effectiveStatus,
      restoring,
      isFavorited,
      isPending,
      toggleFavorites,
      favoritesError,
      clearFavoritesError,
      retryFavorites,
    ],
  );

  return (
    <FavoritesContext.Provider value={value}>
      {children}
    </FavoritesContext.Provider>
  );
}

export function useFavorites(): FavoritesContextValue {
  const ctx = useContext(FavoritesContext);
  if (!ctx) throw new Error("useFavorites must be used within <FavoritesProvider>");
  return ctx;
}
