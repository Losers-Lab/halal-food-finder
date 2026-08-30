"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

/**
 * Search bar — docs/design/search-browse.md §"Hero + search". 56px tall,
 * radius-md, ink-0 bg, kraft-300 border, hard shadow-card; focus → brand-500
 * border + ring. sr-only label (combobox is the accessible name for the input).
 * Enter or button → /search?q=…
 */
export function SearchBar({
  defaultValue = "",
  className = "",
}: {
  defaultValue?: string;
  className?: string;
}) {
  const router = useRouter();
  const [value, setValue] = useState(defaultValue);

  function submit(e: React.FormEvent) {
    e.preventDefault();
    const q = value.trim();
    router.push(q ? `/search?q=${encodeURIComponent(q)}` : "/search");
  }

  return (
    <form
      role="search"
      onSubmit={submit}
      className={`flex flex-col gap-3 sm:flex-row ${className}`}
    >
      <label htmlFor="site-search" className="sr-only">
        Search restaurants, dishes, or area
      </label>
      <input
        id="site-search"
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="Search restaurants, dishes, or area…"
        autoComplete="off"
        className="h-14 w-full rounded-md border-[1.5px] border-kraft-300 bg-ink-0 px-4 text-body text-ink-900 shadow-card placeholder:text-ink-400 focus:border-brand-500 focus:outline-2 focus:outline-offset-2 focus:outline-brand-500"
      />
      <button
        type="submit"
        className="inline-flex h-14 items-center justify-center gap-2 rounded-md bg-brand-500 px-6 text-label text-cream-50 shadow-chip hover:bg-brand-600 active:translate-x-0.5 active:translate-y-0.5 active:shadow-none motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0"
      >
        <SearchIcon />
        Search
      </button>
    </form>
  );
}

function SearchIcon() {
  return (
    <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.8-3.8" />
    </svg>
  );
}