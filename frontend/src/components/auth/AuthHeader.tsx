"use client";

import Link from "next/link";
import { useAuth } from "@/lib/auth/AuthProvider";

/**
 * Header auth strip — shows the signed-in account (post-login state) or
 * Sign up / Log in links. Surface the "success → logged-in" outcome on the
 * home page; session is persisted via AuthProvider.
 */
export function AuthHeader() {
  const { session, restoring, signOut } = useAuth();

  // While a persisted session is re-materializing, show a neutral placeholder
  // rather than flashing between logged-out links and the signed-in strip.
  if (restoring && !session) {
    return <div aria-busy="true" className="h-8 w-56 animate-pulse rounded bg-neutral-100" />;
  }

  if (session) {
    return (
      <div className="flex items-center gap-3 text-neutral-500">
        <span className="text-small">
          Signed in as <strong className="text-neutral-700">{session.email}</strong>
        </span>
        <button
          type="button"
          onClick={() => {
            signOut();
            // signOut() flushes local state; no navigation needed on home.
          }}
          className="rounded-md px-2 py-1 text-small text-brand-500 hover:bg-neutral-100 hover:text-brand-600"
        >
          Sign out
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-2">
      <Link
        href="/login"
        className="rounded-md px-2 py-1 text-small text-brand-500 hover:bg-neutral-100 hover:text-brand-600"
      >
        Log in
      </Link>
      <Link
        href="/signup"
        className="rounded-md bg-brand-500 px-3 py-1.5 text-small font-medium text-white hover:bg-brand-600"
      >
        Sign up
      </Link>
    </div>
  );
}