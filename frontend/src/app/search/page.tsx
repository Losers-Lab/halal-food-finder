"use client";

import { Suspense, useState } from "react";
import { useSearchParams } from "next/navigation";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { MobileTabBar } from "@/components/layout/MobileTabBar";
import { SearchBar } from "@/components/search/SearchBar";
import { BrowseChips } from "@/components/search/BrowseChips";
import { ListingGrid } from "@/components/listing/ListingGrid";
import { useListings } from "@/lib/listings/useListings";
import type { BrowseFilter } from "@/lib/listings/data";

/**
 * Search results — route /search reads ?q=, or browse-all when no query.
 * Shares the search-first hero + chips + grid with the homepage (search-browse.md).
 */
function SearchResults() {
  const searchParams = useSearchParams();
  const q = searchParams.get("q") ?? "";
  const [filter, setFilter] = useState<BrowseFilter>("ALL");
  const { restaurants, verifiedCount, status, retry } = useListings(q, filter);

  return (
    <div className="min-h-screen bg-cream-50">
      <SiteHeader />
      <main className="mx-auto max-w-[1200px] px-5 py-12 pb-32 lg:pb-12">
        <section className="text-center">
          <h1 className="text-title text-ink-900">
            {q ? (
              <>
                Results for <span className="text-brand-500">“{q}”</span>
              </>
            ) : (
              "Browse halal food"
            )}
          </h1>

          <div className="mx-auto mt-6 max-w-[720px] text-left">
            <SearchBar defaultValue={q} />
          </div>

          <div className="mx-auto mt-6 max-w-[720px]">
            <BrowseChips active={filter} onChange={setFilter} />
          </div>
        </section>

        <section className="mt-10" aria-live="polite">
          <p className="mb-5 text-body text-ink-500" data-testid="result-count">
            {status === "success"
              ? restaurants.length === 0
                ? "No exact match — showing similar"
                : `${verifiedCount} verified · ${restaurants.length} spots near you`
              : "Finding halal spots…"}
          </p>
          <ListingGrid
            restaurants={restaurants}
            status={status}
            onRetry={retry}
            emptyHref="/add-listing"
          />
        </section>
      </main>
      <SiteFooter />
      <MobileTabBar />
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense>
      <SearchResults />
    </Suspense>
  );
}