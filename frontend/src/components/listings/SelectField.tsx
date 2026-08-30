"use client";

import type { ReactNode, SelectHTMLAttributes } from "react";
import { forwardRef, useId } from "react";

type SelectFieldProps = {
  label: string;
  helper?: string;
  error?: ReactNode;
  inputProps: Omit<
    SelectHTMLAttributes<HTMLSelectElement>,
    "id" | "aria-describedby" | "aria-invalid"
  >;
  children: ReactNode;
};

/**
 * Form select — same anatomy as the auth Field (label above, helper/error
 * below, wired for a11y via aria-describedby + role=alert). Consumes the same
 * design tokens; no local hex. Used for the cutting-method picker in the
 * add-listing form (sc-138).
 */
export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(
  function SelectField(
    { label, helper, error, inputProps, children },
    ref,
  ) {
    const id = useId();
    const describedBy = error
      ? `${id}-error`
      : helper
        ? `${id}-helper`
        : undefined;
    const hasError = Boolean(error);

    return (
      <div className="space-y-2">
        <label
          htmlFor={id}
          className="block text-body font-medium text-neutral-700"
        >
          {label}
        </label>

        <select
          ref={ref}
          id={id}
          aria-invalid={hasError || undefined}
          aria-describedby={describedBy}
          className={`h-11 w-full appearance-none rounded-md border bg-white px-3 text-body text-neutral-900 focus:outline-2 focus:outline-offset-0 focus:outline-brand-500 ${
            hasError ? "border-danger-500" : "border-neutral-300"
          }`}
          {...inputProps}
        >
          {children}
        </select>

        {hasError ? (
          <p
            id={`${id}-error`}
            role="alert"
            className="text-small text-danger-600"
          >
            {error}
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