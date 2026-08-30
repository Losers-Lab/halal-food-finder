import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "ghost";

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  /** When true, renders an inline spinner before children and disables. */
  loading?: boolean;
  loadingLabel?: string;
  children: ReactNode;
};

/** Spinner used inside loading buttons. 16px; respects reduced-motion (static). */
function Spinner() {
  return (
    <svg
      aria-hidden="true"
      className="h-4 w-4 animate-spin motion-reduce:animate-none"
      viewBox="0 0 16 16"
      fill="none"
    >
      <circle
        className="opacity-25"
        cx="8"
        cy="8"
        r="6"
        stroke="currentColor"
        strokeWidth="2"
      />
      <path
        className="opacity-75"
        fill="currentColor"
        d="M8 2a6 6 0 0 1 6 6h-2a4 4 0 0 0-4-4V2Z"
      />
    </svg>
  );
}

/**
 * Button — brand-500 primary action (44px hit area) or ghost/text link button.
 * Design tokens: docs/design/tokens.md. See auth-screens.md for button specs.
 */
export function Button({
  variant = "primary",
  loading = false,
  loadingLabel,
  disabled,
  children,
  className = "",
  type = "button",
  ...props
}: ButtonProps) {
  const base =
    "inline-flex h-11 items-center justify-center gap-2 rounded-md px-4 text-body font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 disabled:cursor-not-allowed";
  const styles =
    variant === "primary"
      ? "bg-brand-500 text-cream-50 shadow-chip hover:bg-brand-600 active:translate-x-0.5 active:translate-y-0.5 active:shadow-none motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0 disabled:bg-ink-200 disabled:text-ink-400 disabled:shadow-none"
      : "text-brand-500 hover:text-brand-600 hover:underline disabled:text-ink-400";

  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={`${base} ${styles} ${className}`}
      {...props}
    >
      {loading ? <Spinner /> : null}
      {loading && loadingLabel ? loadingLabel : children}
    </button>
  );
}