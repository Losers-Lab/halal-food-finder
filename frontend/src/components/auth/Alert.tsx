import type { ReactNode } from "react";

/**
 * Error alert — shared anatomy for auth error surfaces.
 *
 * Two variants (spec: auth-screens.md):
 * - `banner`: full-width top-of-card server/network error (danger-50 surface,
 *   danger-100 left border, danger-600 text). role=alert, focusable.
 * - `inline`: compact alert above the submit button (login bad-credentials,
 *   rate-locked). Same visual family, no left border.
 */
export function Alert({
  variant = "banner",
  children,
}: {
  variant?: "banner" | "inline";
  children: ReactNode;
}) {
  const styles =
    variant === "banner"
      ? "border-l-4 border-danger-100 bg-danger-50 text-danger-600"
      : "border border-danger-100 bg-danger-50 text-danger-600";

  return (
    <div
      role="alert"
      tabIndex={-1}
      className={`flex items-start gap-2 rounded-md px-3 py-2.5 text-small ${styles}`}
    >
      <AlertIcon className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" />
      <span>{children}</span>
    </div>
  );
}

function AlertIcon({ className = "" }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      <circle cx="8" cy="8" r="6.5" />
      <path d="M8 4.75v3.5M8 11.25h.01" />
    </svg>
  );
}