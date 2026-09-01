"use client";

import Link from "next/link";
import { useState } from "react";
import type { ReviewWithListing } from "@/lib/reviews/data";
import { Button } from "@/components/auth/Button";

type SuggestionTone = "approve" | "deny" | "review";

function suggestionTone(verdict: string | null): SuggestionTone {
  if (verdict === "APPROVE") return "approve";
  if (verdict === "DENY") return "deny";
  return "review";
}

function toneClasses(tone: SuggestionTone): string {
  switch (tone) {
    case "approve":
      return "border-stamp-200 bg-stamp-50 text-stamp-700";
    case "deny":
      return "border-warning-100 bg-warning-50 text-warning-700";
    case "review":
      return "border-kraft-200 bg-ink-100 text-ink-700";
  }
}

function verdictLabel(verdict: string | null): string {
  const label = verdict === "APPROVE"
    ? "Approve"
    : verdict === "DENY"
      ? "Deny"
      : "Needs review";
  return `AI suggests: ${label}`;
}

/**
 * One pending Verification Committee decision (sc-73). Shows the listing being
 * decided (the cert image is a documented follow-up — CertificationImageStorage
 * has no read/URL seam yet), the hosted-AI's conservative suggestion, and
 * Approve / Deny controls. Deny expands an inline reason field and validates it
 * (a block-reason denial is rejected by the backend with 400).
 *
 * `isDeciding` disables both controls while an approve/deny is in flight; a
 * per-review `error` renders inline so a failed decision never vanishes.
 */
export function ReviewCard({
  review,
  isDeciding,
  onApprove,
  onDeny,
  error,
}: {
  review: ReviewWithListing;
  isDeciding: boolean;
  onApprove: (reason?: string) => void;
  onDeny: (reason: string) => void;
  error?: string;
}) {
  const [denying, setDenying] = useState(false);
  const [reason, setReason] = useState("");
  const [validation, setValidation] = useState<string | null>(null);

  const listing = review.listing;
  const tone = suggestionTone(review.suggestedVerdict);
  const confidence =
    review.suggestionConfidence != null
      ? `${Math.round(review.suggestionConfidence * 100)}%`
      : null;

  function openDeny() {
    setDenying(true);
    setValidation(null);
    setReason("");
  }

  function cancelDeny() {
    setDenying(false);
    setValidation(null);
    setReason("");
  }

  function submitDeny() {
    const trimmed = reason.trim();
    if (!trimmed) {
      setValidation("Enter a reason to deny this verification.");
      return;
    }
    onDeny(trimmed);
  }

  return (
    <article className="rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 p-5 shadow-card">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          {listing ? (
            <>
              <h3 className="text-heading text-ink-900">
                <Link
                  href={`/restaurants/${listing.id}`}
                  className="hover:text-brand-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
                >
                  {listing.name}
                </Link>
              </h3>
              <p className="mt-1 text-small text-ink-500">
                {[listing.cuisine, listing.cuttingMethod === "UNSPECIFIED" ? null : listing.cuttingMethod]
                  .filter(Boolean)
                  .join(" · ")}
              </p>
              <p className="mt-0.5 text-small text-ink-400">{listing.address}</p>
            </>
          ) : (
            <>
              <h3 className="text-heading text-ink-900">Listing unavailable</h3>
              <p className="mt-1 text-small text-ink-500">
                This review&apos;s listing no longer exists. Deny it to remove it
                from the queue.
              </p>
            </>
          )}
        </div>

        <span
          className={`shrink-0 rounded-md border px-2.5 py-1 text-small font-medium ${toneClasses(tone)}`}
        >
          {verdictLabel(review.suggestedVerdict)}
          {confidence ? ` · ${confidence}` : ""}
        </span>
      </div>

      {review.suggestionReasoning ? (
        <p className="mt-3 border-l-2 border-kraft-200 pl-3 text-small italic text-ink-500">
          {review.suggestionReasoning}
        </p>
      ) : null}

      {error ? (
        <p role="alert" className="mt-4 text-small text-danger-600">
          {error}
        </p>
      ) : null}

      <div className="mt-5 flex flex-wrap items-center gap-3">
        {!denying ? (
          <>
            <Button
              type="button"
              disabled={isDeciding}
              loading={isDeciding}
              loadingLabel="Approving…"
              onClick={() => onApprove()}
            >
              Approve verification
            </Button>
            <Button
              type="button"
              variant="ghost"
              disabled={isDeciding}
              onClick={openDeny}
            >
              Deny
            </Button>
          </>
        ) : (
          <>
            <label htmlFor={`deny-reason-${review.reviewId}`} className="sr-only">
              Reason for denying this verification
            </label>
            <input
              id={`deny-reason-${review.reviewId}`}
              type="text"
              value={reason}
              onChange={(event) => {
                setReason(event.target.value);
                if (validation) setValidation(null);
              }}
              aria-invalid={validation ? true : undefined}
              aria-describedby={
                validation ? `deny-reason-error-${review.reviewId}` : undefined
              }
              placeholder="Why are you denying this?"
              disabled={isDeciding}
              className={`h-11 min-w-56 flex-1 rounded-md border bg-ink-0 px-3 text-body text-ink-900 placeholder:text-ink-400 focus:outline-2 focus:outline-offset-0 focus:outline-brand-500 ${
                validation ? "border-danger-500" : "border-kraft-300"
              }`}
            />
            {validation ? (
              <span
                id={`deny-reason-error-${review.reviewId}`}
                role="alert"
                className="text-small text-danger-600"
              >
                {validation}
              </span>
            ) : null}
            <Button
              type="button"
              disabled={isDeciding}
              loading={isDeciding}
              loadingLabel="Denying…"
              onClick={submitDeny}
            >
              Confirm deny
            </Button>
            <Button
              type="button"
              variant="ghost"
              disabled={isDeciding}
              onClick={cancelDeny}
            >
              Cancel
            </Button>
          </>
        )}
      </div>
    </article>
  );
}