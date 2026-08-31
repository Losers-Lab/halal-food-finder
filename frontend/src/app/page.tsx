"use client";

import { useState } from "react";
import { SiteHeader } from "@/components/layout/SiteHeader";
import { SiteFooter } from "@/components/layout/SiteFooter";
import { MobileTabBar } from "@/components/layout/MobileTabBar";
import { SearchBar } from "@/components/search/SearchBar";
import { BrowseChips } from "@/components/search/BrowseChips";
import { ListingGrid } from "@/components/listing/ListingGrid";
import { useListings } from "@/lib/listings/useListings";
import type { BrowseFilter } from "@/lib/listings/data";
import { SealMark } from "@/components/trust";

/**
 * Home — the search-first browse screen (docs/design/search-browse.md).
 * Search box is the first thing the eye hits; browse chips filter the list;
 * cards link to the detail page. No map on the homepage (founder: map is at
 * most a list/view toggle, never the entry surface).
 */
export default function Home() {
  const [filter, setFilter] = useState<BrowseFilter>("ALL");
  const { restaurants, verifiedCount, status, retry } = useListings("", filter);

  return (
    <div className="min-h-screen bg-cream-50">
      <SiteHeader />
      <main className="mx-auto max-w-[1200px] px-5 py-12 pb-32">
        {/* Hero */}
        <section className="text-center">
          <SealMark
            className="mx-auto h-10 w-10 text-brand-500"
            srLabel="Tahir's List seal"
          />
          <h1 className="mx-auto mt-4 max-w-[640px] text-display text-ink-900">
            Find halal food near you. Stamped &amp; trusted.
          </h1>
          <p className="mx-auto mt-3 max-w-[56ch] text-body text-ink-500">
            Real certifications, reviewed by people — not just a label.
          </p>

          <div className="mx-auto mt-8 max-w-[720px] text-left">
            <SearchBar />
          </div>

          <div className="mx-auto mt-6 max-w-[720px]">
            <BrowseChips active={filter} onChange={setFilter} />
          </div>
        </section>

        {/* Results */}
        <section className="mt-12" aria-live="polite">
          <div className="mb-5 flex items-baseline justify-between">
            <p className="text-body text-ink-500" data-testid="result-count">
              {status === "success" && restaurants.length > 0
                ? `${verifiedCount} verified · ${restaurants.length} spots near you`
                : "Finding halal spots…"}
            </p>
          </div>
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