"use client";

import { useCallback, useEffect, useState } from "react";
import { api, ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { fetchVerificationQueue, type ReviewWithListing } from "./data";

type Status = "idle" | "loading" | "ready" | "error";

type DecisionError = { reviewId: string; message: string };

export type UseVerificationQueue = {
  /** The pending workqueue (each enriched with its listing details). */
  reviews: ReviewWithListing[];
  status: Status;
  /**
   * VC-role gate. True only for a VERIFICATION_COMMITTEE session. The queue is
   * never fetched for non-VC/anonymous sessions (the endpoint would 403/401).
   */
  isCommittee: boolean;
  /** True while an approve/deny for this review is in flight (disable buttons). */
  isDeciding: (reviewId: string) => boolean;
  /** Approve a review — on success the review leaves the queue and the listing
   *  is VERIFIED. `reason` is optional. */
  approve: (reviewId: string, reason?: string) => void;
  /** Deny a review — `reason` must be non-blank. On success the review leaves
   *  the queue and the listing stays UNVERIFIED. */
  deny: (reviewId: string, reason: string) => void;
  /** Transient summary of the last completed decision (success state). */
  notice: string | null;
  clearNotice: () => void;
  /** The most recent failed decision, keyed to its review for an inline error. */
  decisionError: DecisionError | null;
  clearDecisionError: () => void;
  /** Re-run the queue fetch (error-state retry, or refresh after decisions). */
  retry: () => void;
};

const COMMITTEE_ROLE = "VERIFICATION_COMMITTEE";

/** Human summary of a completed decision, shown once as the success state. */
function successNotice(reviewId: string, approved: boolean): string {
  return approved
    ? "Approved — the listing is now VERIFIED."
    : "Denied — the listing stays unverified.";
}

function decisionFailureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === "review_not_pending")
      return "This review was already decided or is no longer pending.";
    if (error.code === "review_not_found")
      return "That review could not be found.";
    if (error.detail) return error.detail;
  }
  return "We couldn't complete that decision. Please try again.";
}

/**
 * Load-bearing state for the Verification Committee screen (sc-73). Fetches the
 * pending workqueue and drives approve/deny, mirroring the FavoritesProvider
 * pattern (derived loading, per-review in-flight lock, transient feedback).
 *
 * Gating: the queue is fetched only while `session.role` is
 * VERIFICATION_COMMITTEE. A non-VC authenticated user renders the page's
 * "no access" treatment and never triggers a 403 from this hook.
 */
export function useVerificationQueue(): UseVerificationQueue {
  const { session } = useAuth();
  const isCommittee = session?.role === COMMITTEE_ROLE;
  const accountId = session?.accountId ?? null;

  const [reviews, setReviews] = useState<ReviewWithListing[]>([]);
  const [status, setStatus] = useState<Status>("idle");
  // Which account's queue is currently in `reviews`/`status` — prevents leaking
  // one account's queue across a sign-out/sign-in boundary without a synchronous
  // setState in the effect body.
  const [loadedAccount, setLoadedAccount] = useState<string | null>(null);
  const [decidingIds, setDecidingIds] = useState<Set<string>>(new Set());
  const [notice, setNotice] = useState<string | null>(null);
  const [decisionError, setDecisionError] = useState<DecisionError | null>(null);
  const [reload, setReload] = useState(0);

  // Fetch the queue when a committee session appears (or on retry/refresh).
  // Non-committee/anonymous: fetch nothing (the endpoint would 403/401). Only
  // async callbacks set state (react-hooks/set-state-in-effect), like useListings.
  useEffect(() => {
    if (!isCommittee || !accountId) return;
    let cancelled = false;
    Promise.resolve()
      .then(() => {
        if (cancelled) return [] as ReviewWithListing[];
        setStatus("loading");
        return fetchVerificationQueue();
      })
      .then((list) => {
        if (cancelled) return;
        setReviews(list);
        setLoadedAccount(accountId);
        setStatus("ready");
      })
      .catch(() => {
        if (cancelled) return;
        setLoadedAccount(accountId);
        setStatus("error");
      });
    return () => {
      cancelled = true;
    };
  }, [isCommittee, accountId, reload]);

  // Derived: never expose another account's queue or a stale "ready".
  const readyForCurrent = isCommittee && loadedAccount === accountId;
  const effectiveReviews = readyForCurrent ? reviews : [];
  const effectiveStatus: Status = !isCommittee
    ? "idle" // never a committee queue for a non-committee viewer
    : readyForCurrent
      ? status
      : "loading";

  const removeReview = useCallback((reviewId: string) => {
    setReviews((prev) => prev.filter((r) => r.reviewId !== reviewId));
  }, []);

  const clearNotice = useCallback(() => setNotice(null), []);
  const clearDecisionError = useCallback(() => setDecisionError(null), []);
  const retry = useCallback(() => setReload((n) => n + 1), []);

  const isDeciding = useCallback(
    (reviewId: string) => decidingIds.has(reviewId),
    [decidingIds],
  );

  /**
   * Shared approve/deny runner: locks the review while in flight, removes it on
   * success (it is no longer pending), surfaces a transient success notice, and
   * reports an inline per-review error on failure.
   */
  const runDecision = useCallback(
    (reviewId: string, approved: boolean, request: () => Promise<unknown>) => {
      if (!isCommittee || decidingIds.has(reviewId)) return;
      setNotice(null);
      setDecisionError(null);
      setDecidingIds((prev) => {
        const next = new Set(prev);
        next.add(reviewId);
        return next;
      });
      request()
        .then(() => {
          removeReview(reviewId);
          setDecisionError(null);
          setNotice(successNotice(reviewId, approved));
        })
        .catch((error: unknown) => {
          setDecisionError({ reviewId, message: decisionFailureMessage(error) });
        })
        .finally(() => {
          setDecidingIds((prev) => {
            const next = new Set(prev);
            next.delete(reviewId);
            return next;
          });
        });
    },
    [isCommittee, decidingIds, removeReview],
  );

  const approve = useCallback(
    (reviewId: string, reason?: string) => {
      runDecision(reviewId, true, () =>
        api.approveVerificationReview(reviewId, reason),
      );
    },
    [runDecision],
  );

  const deny = useCallback(
    (reviewId: string, reason: string) => {
      runDecision(reviewId, false, () =>
        api.denyVerificationReview(reviewId, reason),
      );
    },
    [runDecision],
  );

  return {
    reviews: effectiveReviews,
    status: effectiveStatus,
    isCommittee,
    isDeciding,
    approve,
    deny,
    notice,
    clearNotice,
    decisionError,
    clearDecisionError,
    retry,
  };
}