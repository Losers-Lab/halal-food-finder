"use client";

import type { ReactNode } from "react";
import Link from "next/link";

type AuthCardProps = {
  heading: string;
  subcopy: string;
  /** Footer cross-link content (e.g. Login / Sign up links). */
  footer: ReactNode;
  /** Sign Up only — trust line below the card. */
  trustLine?: boolean;
  children: ReactNode;
};

/**
 * Shared auth screen frame — spec: docs/design/auth-screens.md §"Shared screen
 * frame". Centered card on neutral-50; white card radius-lg shadow-card;
 * max-width 400px; full-bleed on mobile (page = card), 16px input font.
 */
export function AuthCard({
  heading,
  subcopy,
  footer,
  trustLine = false,
  children,
}: AuthCardProps) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-neutral-50 px-0 py-0 sm:px-6 sm:py-10">
      <div className="w-full max-w-[400px]">
        <div className="rounded-none bg-neutral-0 px-5 py-8 shadow-none sm:rounded-lg sm:px-8 sm:py-8 sm:shadow-card">
          {/* Brand mark */}
          <div className="mb-6">
            <Link
              href="/"
              className="inline-flex items-center gap-2 text-heading font-semibold text-brand-500 hover:text-brand-600"
            >
              <span className="inline-flex h-8 w-8 items-center justify-center rounded-md bg-brand-500 text-sm font-bold text-white">
                H
              </span>
              Halal Food Finder
            </Link>
          </div>

          <header className="space-y-1.5">
            <h1 className="text-title text-neutral-900">{heading}</h1>
            <p className="text-body text-neutral-500">{subcopy}</p>
          </header>

          <div className="mt-6">{children}</div>
        </div>

        <footer className="px-5 pb-8 pt-4 text-center text-body text-neutral-500 sm:px-0">
          {footer}
        </footer>

        {trustLine ? (
          <p className="px-5 pb-8 text-center text-small text-neutral-500 sm:px-0">
            By signing up you agree to our{" "}
            <Link
              href="#"
              className="text-brand-500 hover:text-brand-600 hover:underline"
            >
              Terms
            </Link>{" "}
            and{" "}
            <Link
              href="#"
              className="text-brand-500 hover:text-brand-600 hover:underline"
            >
              Privacy Policy
            </Link>
            .
          </p>
        ) : null}
      </div>
    </div>
  );
}