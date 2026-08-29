"use client";

import type { InputHTMLAttributes, ReactNode } from "react";
import { forwardRef, useId } from "react";

type FieldProps = {
  label: string;
  /** Helpful idle text (replaced by `error` when present). */
  helper?: string;
  /**
   * Field error below the input. Accepts a ReactNode so a message can carry an
   * inline element (e.g. the "logging in" <Link> on signup email-not-unique).
   */
  error?: ReactNode;
  /** Renders the label row's trailing adornment (e.g. "Forgot password?" link). */
  labelEnd?: ReactNode;
  inputProps: Omit<
    InputHTMLAttributes<HTMLInputElement>,
    "id" | "aria-describedby" | "aria-invalid"
  >;
};

/**
 * Form field — label above input, helper/error below, wired for a11y
 * (aria-describedby + role=alert). Spec: docs/design/auth-screens.md §"Form
 * field spec". Consumes tokens; no local hex.
 */
export const Field = forwardRef<HTMLInputElement, FieldProps>(
  function Field({ label, helper, error, labelEnd, inputProps }, ref) {
    const id = useId();
    const describedBy = error
      ? `${id}-error`
      : helper
        ? `${id}-helper`
        : undefined;
    const hasError = Boolean(error);

    return (
      <div className="space-y-2">
        <div className="flex items-baseline justify-between">
          <label
            htmlFor={id}
            className="text-body font-medium text-neutral-700"
          >
            {label}
          </label>
          {labelEnd}
        </div>

        <input
          ref={ref}
          id={id}
          aria-invalid={hasError || undefined}
          aria-describedby={describedBy}
          className={`h-11 w-full rounded-md border bg-white px-3 text-body text-neutral-900 placeholder:text-neutral-400 focus:outline-2 focus:outline-offset-0 focus:outline-brand-500 ${
            hasError ? "border-danger-500" : "border-neutral-300"
          }`}
          {...inputProps}
        />

        {hasError ? (
          <p
            id={`${id}-error`}
            role="alert"
            className="flex items-start gap-1.5 text-small text-danger-600"
          >
            <AlertIcon className="mt-0.5 h-3.5 w-3.5 shrink-0" />
            <span>{error}</span>
          </p>
        ) : helper ? (
          <p id={`${id}-helper`} className="text-small text-neutral-500">
            {helper}
          </p>
        ) : null}
      </div>
    );
  },
);

function AlertIcon({ className = "" }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      width="14"
      height="14"
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