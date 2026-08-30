import Link from "next/link";
import { ListingCard } from "./ListingCard";
import { SealMark } from "@/components/trust";
import type { Restaurant } from "@/lib/listings/restaurants";

/**
 * Listing results grid — docs/design/search-browse.md §"Listing cards" and
 * "Card states". Responsive columns (4 ≥1280 / 3 ≥1024 / 2 ≥768 / 1 below,
 * gap space-6). Owns the loading, empty and network-error states.
 */
export function ListingGrid({
  restaurants,
  status,
  onRetry,
  emptyHref = "/search",
}: {
  restaurants: Restaurant[];
  status: "loading" | "success" | "error";
  onRetry?: () => void;
  emptyHref?: string;
}) {
  if (status === "loading") {
    return (
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <div key={i} className="overflow-hidden rounded-lg border-[1.5px] border-kraft-200 bg-ink-0 shadow-card">
            <div className="aspect-video bg-ink-100" />
            <div className="space-y-3 p-5">
              <div className="h-4 w-3/4 rounded bg-ink-100" />
              <div className="h-3 w-1/2 rounded bg-ink-100" />
            </div>
          </div>
        ))}
      </div>
    );
  }

  if (status === "error") {
    return (
      <>
        <div
          role="alert"
          className="mb-5 flex items-start gap-2 border border-danger-100 bg-danger-50 px-3 py-3 text-small text-danger-600"
        >
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span className="flex flex-1 flex-wrap items-center gap-2">
            <span>We couldn&apos;t load results.</span>
            {onRetry ? (
              <button
                type="button"
                onClick={onRetry}
                className="rounded-full bg-ink-900 px-3 py-1 text-small font-medium text-cream-50 shadow-chip hover:bg-ink-700"
              >
                Retry
              </button>
            ) : null}
          </span>
        </div>
        <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {/* header/hero are parent-owned; grid body still renders so layout holds */}
        </div>
      </>
    );
  }

  if (restaurants.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-kraft-300 bg-ink-0 p-10 text-center">
        <SealMark className="mx-auto h-12 w-12 text-ink-300" srLabel="Empty" />
        <h2 className="mt-4 text-title text-ink-900">Nothing matches yet</h2>
        <p className="mx-auto mt-2 max-w-md text-body text-ink-500">
          Try a different search — or add the spot you know.
        </p>
        <Link
          href={emptyHref}
          className="mt-6 inline-flex h-11 items-center justify-center rounded-full bg-ink-900 px-6 text-label text-cream-50 shadow-chip hover:bg-ink-700"
        >
          Add a restaurant
        </Link>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
      {restaurants.map((r) => (
        <ListingCard key={r.id} restaurant={r} />
      ))}
    </div>
  );
}

function AlertTriangle({ className = "" }: { className?: string }) {
  return (
    <svg
      aria-hidden="true"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
    >
      <path d="M8 2.5 14.5 13h-13L8 2.5Z" />
      <path d="M8 6.5v3M8 11.5h.01" />
    </svg>
  );
}