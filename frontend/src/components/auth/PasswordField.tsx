"use client";

import type { InputHTMLAttributes } from "react";
import { forwardRef, useId, useState } from "react";

type PasswordFieldProps = {
  label: string;
  helper?: string;
  error?: string;
  /** Extra text in the label row's end slot (e.g. "Forgot password?" link). */
  labelEnd?: React.ReactNode;
  inputProps: Omit<
    InputHTMLAttributes<HTMLInputElement>,
    "id" | "aria-describedby" | "aria-invalid"
  >;
};

/**
 * Password field with a show/hide toggle (44px hit area, right-aligned).
 * Spec: docs/design/auth-screens.md §"Form field spec". The toggle toggles
 * type=password, announces state via aria-pressed + aria-label.
 */
export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(
  function PasswordField({ label, helper, error, labelEnd, inputProps }, ref) {
    const id = useId();
    const [visible, setVisible] = useState(false);
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

        <div className="relative">
          <input
            ref={ref}
            id={id}
            type={visible ? "text" : "password"}
            aria-invalid={hasError || undefined}
            aria-describedby={describedBy}
            className={`h-11 w-full rounded-md border bg-white px-3 pr-14 text-body text-neutral-900 placeholder:text-neutral-400 focus:outline-2 focus:outline-offset-0 focus:outline-brand-500 ${
              hasError ? "border-danger-500" : "border-neutral-300"
            }`}
            {...inputProps}
          />
          <button
            type="button"
            aria-label={visible ? "Hide password" : "Show password"}
            aria-pressed={visible}
            onClick={() => setVisible((v) => !v)}
            className="absolute inset-y-0 right-0 flex w-11 items-center justify-center text-neutral-500 hover:text-neutral-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
          >
            {visible ? (
              <EyeOffIcon />
            ) : (
              <EyeIcon />
            )}
          </button>
        </div>

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

function EyeIcon() {
  return (
    <svg
      aria-hidden="true"
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6-9.5-6-9.5-6Z" />
      <circle cx="12" cy="12" r="2.5" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg
      aria-hidden="true"
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      <path d="M3 3l18 18" />
      <path d="M10.6 5.6A9.8 9.8 0 0 1 12 5.5c6 0 9.5 6.5 9.5 6.5a17.9 17.9 0 0 1-2.4 3.2M6.1 6.1A17.6 17.6 0 0 0 2.5 12s3.5 6.5 9.5 6.5a9.6 9.6 0 0 0 3.4-.6M9.9 9.9a2.5 2.5 0 0 0 3.4 3.4" />
    </svg>
  );
}

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