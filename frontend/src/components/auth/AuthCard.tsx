"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import { SealMark } from "@/components/trust";

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
 * frame" reskinned per auth-screens-v2-delta.md. Centered card on cream-50;
 * ink-0 card, radius-lg, kraft-200 border, hard offset shadow-card; max-width
 * 400px; full-bleed on mobile (page = card), 16px input font.
 */
export function AuthCard({
  heading,
  subcopy,
  footer,
  trustLine = false,
  children,
}: AuthCardProps) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-cream-50 px-0 py-0 sm:px-6 sm:py-10">
      <div className="w-full max-w-[400px]">
        <div className="rounded-none border-0 bg-ink-0 px-5 py-8 shadow-none sm:rounded-lg sm:border sm:border-kraft-200 sm:px-8 sm:py-8 sm:shadow-card">
          {/* Brand mark */}
          <div className="mb-6">
            <Link
              href="/"
              className="inline-flex items-center gap-2 text-heading font-bold text-brand-500 hover:text-brand-600"
            >
              <SealMark className="h-8 w-8 text-brand-500" />
              HalalMarket
            </Link>
          </div>

          <header className="space-y-1.5">
            <h1 className="text-title text-ink-900">{heading}</h1>
            <p className="text-body text-ink-500">{subcopy}</p>
          </header>

          <div className="mt-6">{children}</div>
        </div>

        <footer className="px-5 pb-8 pt-4 text-center text-body text-ink-500 sm:px-0">
          {footer}
        </footer>

        {trustLine ? (
          <p className="px-5 pb-8 text-center text-small text-ink-500 sm:px-0">
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