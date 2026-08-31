import Link from "next/link";
import { Wordmark } from "@/components/brand/BrandLockup";

/**
 * Site footer (all screens) — docs/design/search-browse.md §"Footer" + C2
 * wordmark ("Tahir's List", red apostrophe) with the tagline. The
 * "How verification works" link is a mandatory trust anchor. NOTE: the
 * /how-verification-works route is not built yet (follow-up); until it lands we
 * render the anchor pointing at that route so the footer is stable across the
 * site.
 */
export function SiteFooter() {
  return (
    <footer className="mt-16 border-t-[1.5px] border-kraft-200 bg-cream-50">
      <div className="mx-auto flex max-w-[1200px] flex-col gap-4 px-5 py-8 text-small text-ink-500 sm:flex-row sm:items-center sm:justify-between">
        <span>
          <Wordmark className="text-heading" /> · find halal food
        </span>
        <nav aria-label="Footer" className="flex flex-wrap gap-4">
          <Link href="/how-verification-works" className="hover:text-brand-600">
            How verification works
          </Link>
          <Link href="/add-listing" className="hover:text-brand-600">
            Add a restaurant
          </Link>
          <Link href="/about" className="hover:text-brand-600">
            About
          </Link>
        </nav>
      </div>
    </footer>
  );
}