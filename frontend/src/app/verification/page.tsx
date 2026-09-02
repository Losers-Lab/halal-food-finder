"use client";

import Link from "next/link";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { MobileTabBar } from "@/components/layout/MobileTabBar";
import { ReviewCard } from "@/components/reviews/ReviewCard";
import { SealMark } from "@/components/trust";
import { useAuth } from "@/lib/auth/AuthProvider";
import { useVerificationQueue } from "@/lib/reviews/useVerificationQueue";

/**
 * Verification Committee review queue — route /verification (sc-73).
 *
 * The single approve/deny surface. Gated so ONLY a VERIFICATION_COMMITTEE
 * session sees the queue:
 *   - anonymous           → log-in prompt (the API would 401)
 *   - authenticated, not VC → "no access" (the API would 403 `forbidden`)
 *   - VC                   → loading / error(retry) / empty / decision list
 *
 * Each card shows the listing + the hosted-AI suggestion (the certification
 * image itself is a documented follow-up — write-only storage, no read seam yet).
 * Approving promotes the listing to VERIFIED; denying (reason required) keeps it
 * unverified. A decided review leaves the queue immediately, confirmed by a
 * transient success notice.
 */
export default function VerificationPage() {
  const { session, restoring } = useAuth();
  const queue = useVerificationQueue();

  const isCommittee = session?.role === "VERIFICATION_COMMITTEE";

  return (
    <div className="min-h-screen bg-cream-50">
      <SiteHeader />
      <main className="mx-auto max-w-[1200px] px-5 py-12 pb-32 lg:pb-12">
        <header>
          <h1 className="text-title text-ink-900">Verification Committee</h1>
          <p className="mt-2 max-w-2xl text-body text-ink-500">
            Review and decide each pending certification. You are confirming
            that the restaurant may be shown as verified.
          </p>
        </header>

        {restoring && !session ? (
          <div aria-busy="true" className="mt-8 h-40 animate-pulse rounded-lg bg-ink-100" />
        ) : null}

        {!restoring && !session ? (
          <div className="mt-8 rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 p-10 text-center shadow-card">
            <SealMark className="mx-auto h-12 w-12 text-ink-300" srLabel="Signed out" />
            <h2 className="mt-4 text-title text-ink-900">
              Verification Committee sign in
            </h2>
            <p className="mx-auto mt-2 max-w-md text-body text-ink-500">
              Only a Verification Committee member can work the review queue.
            </p>
            <Link
              href="/login"
              className="mt-6 inline-flex h-11 items-center justify-center rounded-md bg-brand-500 px-4 text-body font-medium text-cream-50 shadow-chip hover:bg-brand-600"
            >
              Log in
            </Link>
          </div>
        ) : null}

        {session && !isCommittee ? (
          <div className="mt-8 rounded-lg border-[1.5px] border-danger-100 bg-danger-50 p-10 text-center">
            <h2 className="text-title text-ink-900">No access to this queue</h2>
            <p className="mx-auto mt-2 max-w-md text-body text-ink-500">
              This screen is for the Verification Committee only. If you think
              this is a mistake, contact an administrator.
            </p>
          </div>
        ) : null}

        {isCommittee ? (
          <div className="mt-8">
            {queue.notice ? (
              <div
                role="status"
                className="mb-5 flex items-center justify-between gap-3 rounded-md border border-stamp-200 bg-stamp-50 px-4 py-3 text-body text-stamp-700"
              >
                <span>{queue.notice}</span>
                <button
                  type="button"
                  onClick={queue.clearNotice}
                  aria-label="Dismiss"
                  className="rounded-md px-1.5 text-stamp-700 hover:bg-stamp-100"
                >
                  ×
                </button>
              </div>
            ) : null}

            {queue.status === "loading" ? (
              <div aria-busy="true" className="space-y-4">
                <div className="h-40 animate-pulse rounded-lg bg-ink-100" />
                <div className="h-40 animate-pulse rounded-lg bg-ink-100" />
              </div>
            ) : null}

            {queue.status === "error" ? (
              <div className="rounded-lg border-[1.5px] border-danger-100 bg-danger-50 p-8 text-center">
                <h2 className="text-title text-ink-900">
                  We couldn&apos;t load the review queue.
                </h2>
                <button
                  type="button"
                  onClick={queue.retry}
                  className="mt-4 inline-flex h-11 items-center justify-center rounded-md bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
                >
                  Retry
                </button>
              </div>
            ) : null}

            {queue.status === "ready" && queue.reviews.length === 0 ? (
              <div className="rounded-lg border border-dashed border-kraft-300 bg-ink-0 p-10 text-center">
                <SealMark className="mx-auto h-12 w-12 text-ink-300" srLabel="Empty" />
                <h2 className="mt-4 text-title text-ink-900">
                  No pending verifications
                </h2>
                <p className="mx-auto mt-2 max-w-md text-body text-ink-500">
                  The queue is clear. New certification reviews will appear here
                  once an owner submits proof and the AI suggests a verdict.
                </p>
              </div>
            ) : null}

            {queue.status === "ready" && queue.reviews.length > 0 ? (
              <div className="space-y-5">
                {queue.reviews.map((review) => (
                  <ReviewCard
                    key={review.reviewId}
                    review={review}
                    isDeciding={queue.isDeciding(review.reviewId)}
                    onApprove={(reason) => queue.approve(review.reviewId, reason)}
                    onDeny={(reason) => queue.deny(review.reviewId, reason)}
                    error={
                      queue.decisionError?.reviewId === review.reviewId
                        ? queue.decisionError.message
                        : undefined
                    }
                  />
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
      </main>
      <SiteFooter />
      <MobileTabBar />
    </div>
  );
}