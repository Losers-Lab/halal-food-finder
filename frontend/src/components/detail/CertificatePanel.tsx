import Link from "next/link";
import { SealMark } from "@/components/trust";
import {
  expiryState,
  formatDate,
  type Certificate,
  type ExpiryState,
} from "@/lib/listings/restaurants";

/**
 * Certificate trust panel — docs/design/detail-page.md §1.2 (REQUIRED centerpiece for
 * verified listings). Dashed stamp-200 border panel framing the certifier, review
 * date, expiry, and a View-certificate link — with binding expiry states:
 * valid / expiring-soon (warning icon+text) / expired (informs, downgrades badge —
 * never danger-red, a lapse is informational not a user error).
 */
export function CertificatePanel({
  certificate,
  name,
}: {
  certificate: Certificate;
  name: string;
}) {
  const state = expiryState(certificate);
  const expired = state === "expired";

  return (
    <section
      aria-label="Halal verification"
      className="rounded-lg border-[1.5px] border-dashed border-stamp-200 bg-ink-0 p-6 shadow-stamp"
    >
      <div className="flex items-center gap-2">
        <SealMark className="h-6 w-6 text-stamp-500" srLabel="Verified seal" />
        <h2 className="text-heading text-ink-900">Halal verification</h2>
      </div>

      <dl className="mt-4 grid gap-3 text-body">
        <div className="flex flex-col gap-0.5 sm:flex-row sm:gap-2">
          <dt className="w-36 shrink-0 font-medium text-ink-500">Certifier</dt>
          <dd className="font-medium text-ink-700">{certificate.certifier}</dd>
        </div>
        <div className="flex flex-col gap-0.5 sm:flex-row sm:gap-2">
          <dt className="w-36 shrink-0 font-medium text-ink-500">Last reviewed</dt>
          <dd className="text-ink-700">{formatDate(certificate.reviewedOn)}</dd>
        </div>
        <div className="flex flex-col gap-0.5 sm:flex-row sm:gap-2">
          <dt className="w-36 shrink-0 font-medium text-ink-500">Expires</dt>
          <dd className="text-ink-700">{expired ? "—" : formatDate(certificate.expiresOn)}</dd>
        </div>
      </dl>

      <div className="mt-4">
        <a
          href={certificate.certificateUrl ?? "#"}
          className="text-label text-brand-500 underline-offset-2 hover:underline"
          aria-label={`View halal certificate for ${name}`}
          target={certificate.certificateUrl ? "_blank" : undefined}
          rel="noreferrer"
        >
          View certificate →
        </a>
      </div>

      {state === "expiring" ? (
        <ExpiryNote variant="expiring" detail={`Expires ${formatDate(certificate.expiresOn)} — review in progress`} />
      ) : null}
      {expired ? (
        <ExpiryNote
          variant="expired"
          detail={`Certification lapsed on ${formatDate(certificate.expiresOn)}. We're following up with the restaurant.`}
        />
      ) : null}

      <p className="mt-4 text-small text-ink-500">
        Reviewed by our verification committee — not self-reported.{" "}
        <Link href="/how-verification-works" className="text-brand-500 underline-offset-2 hover:underline">
          Learn more
        </Link>
      </p>
    </section>
  );
}

function ExpiryNote({
  variant,
  detail,
}: {
  variant: Exclude<ExpiryState, "valid" | "none">;
  detail: string;
}) {
  const warning = variant === "expiring";
  return (
    <p
      className={`mt-4 flex items-start gap-2 rounded-md px-3 py-2.5 text-small ${
        warning ? "bg-warning-50 text-warning-700" : "bg-ink-100 text-ink-700"
      }`}
    >
      {warning ? (
        <WarningIcon className="mt-0.5 h-4 w-4 shrink-0" />
      ) : (
        <InfoIcon className="mt-0.5 h-4 w-4 shrink-0" />
      )}
      <span>{detail}</span>
    </p>
  );
}

function WarningIcon({ className = "" }: { className?: string }) {
  return (
    <svg aria-hidden="true" width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" className={className}>
      <path d="M8 2.5 14.5 13h-13L8 2.5Z" />
      <path d="M8 6.5v3M8 11.5h.01" />
    </svg>
  );
}

function InfoIcon({ className = "" }: { className?: string }) {
  return (
    <svg aria-hidden="true" width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" className={className}>
      <circle cx="8" cy="8" r="6.5" />
      <path d="M8 7v3.5M8 4.75h.01" />
    </svg>
  );
}