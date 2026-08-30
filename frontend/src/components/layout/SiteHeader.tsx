"use client";

import Link from "next/link";
import { useState } from "react";
import { AuthHeader } from "@/components/auth/AuthHeader";
import { SealMark } from "@/components/trust";

const secondaryLinks = [
  { href: "/search", label: "Search" },
  { href: "/add-listing", label: "Add a restaurant" },
];

/**
 * Site header (all screens) — docs/design/search-browse.md §"Header (all screens)".
 * 64px tall, cream-50, kraft-200 bottom border. Wordmark left (placeholder
 * "HalalMarket" — founder undecided, do not replace) over a brand seal. Right:
 * Log in = ghost, Sign up = primary (AuthHeader). Mobile collapses to wordmark +
 * hamburger → slide-down panel.
 */
export function SiteHeader() {
  return (
    <header className="sticky top-0 z-30 border-b-[1.5px] border-kraft-200 bg-cream-50">
      <div className="mx-auto flex h-16 max-w-[1200px] items-center justify-between gap-4 px-5">
        <Link href="/" className="inline-flex items-center gap-2 text-title font-extrabold text-brand-500 hover:text-brand-600">
          <SealMark className="h-7 w-7 text-brand-500" />
          HalalMarket
        </Link>
        <div className="hidden items-center gap-3 md:flex">
          {secondaryLinks.map((l) => (
            <Link
              key={l.href}
              href={l.href}
              className="text-small text-ink-700 hover:text-brand-600"
            >
              {l.label}
            </Link>
          ))}
          <AuthHeader />
        </div>
        <MobileMenu className="md:hidden" />
      </div>
    </header>
  );
}

/** Mobile slide-down nav (secondary links + auth), closes on route change. */
function MobileMenu({ className = "" }: { className?: string }) {
  const [open, setOpen] = useState(false);
  return (
    <div className={className}>
      <button
        type="button"
        aria-expanded={open}
        aria-controls="mobile-nav"
        aria-label={open ? "Close menu" : "Open menu"}
        onClick={() => setOpen((v) => !v)}
        className="inline-flex h-11 w-11 items-center justify-center text-ink-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500"
      >
        <svg aria-hidden="true" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
          {open ? <path d="M6 6l12 12M18 6L6 18" /> : <path d="M4 7h16M4 12h16M4 17h16" />}
        </svg>
      </button>
      {open ? (
        <div
          id="mobile-nav"
          className="absolute left-0 right-0 top-16 z-40 border-b-[1.5px] border-kraft-200 bg-cream-50 px-5 shadow-pop"
        >
          <nav className="flex flex-col">
            <Link
              href="/search"
              onClick={() => setOpen(false)}
              className="flex min-h-11 items-center border-t border-kraft-100 px-1 py-2 text-body text-ink-700"
            >
              Search
            </Link>
          </nav>
        </div>
      ) : null}
    </div>
  );
}