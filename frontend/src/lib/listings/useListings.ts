"use client";

import { useEffect, useState } from "react";
import type { Restaurant } from "@/lib/listings/restaurants";
import { verificationStatus } from "@/lib/listings/restaurants";
import {
  searchListings,
  type BrowseFilter,
} from "./data";

type Status = "loading" | "success" | "error";

/**
 * Client-side listing read hook for the browse/search screens. Wraps the async
 * read seam (`data.searchListings`, now backed by live GET /v1/listings) so the
 * UI exercises loading + error states, and drives the search + chip filters.
 *
 * Derives `verifiedCount` during the (async, non-render) read so render stays
 * pure — no Date.now() in the component body (react-hooks/purity).
 */
export function useListings(query: string, filter: BrowseFilter) {
  const [restaurants, setRestaurants] = useState<Restaurant[]>([]);
  const [verifiedCount, setVerifiedCount] = useState(0);
  const [status, setStatus] = useState<Status>("loading");
  const [reload, setReload] = useState(0);

  useEffect(() => {
    let cancelled = false;
    // Keep the seam async-shaped so swapping to a real request is trivial. The
    // first `loading` set lives in the microtask (not the effect body) to avoid
    // a cascading render on mount.
    Promise.resolve()
      .then(() => {
        if (cancelled) return [];
        setStatus("loading");
        return searchListings(query, filter === "HAND_CUT", filter === "DELIVERY");
      })
      .then((r) => {
        if (cancelled) return;
        setRestaurants(r);
        setVerifiedCount(r.filter((x) => verificationStatus(x) === "VERIFIED").length);
        setStatus("success");
      })
      .catch(() => {
        if (cancelled) return;
        setStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [query, filter, reload]);

  return {
    restaurants,
    verifiedCount,
    status,
    retry: () => setReload((n) => n + 1),
  };
}