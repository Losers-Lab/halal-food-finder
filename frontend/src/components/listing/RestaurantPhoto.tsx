import Image from "next/image";
import { useState, type CSSProperties } from "react";

/**
 * Restaurant hero/card photo — sc-157 (responsible renderer) + sc-183.
 *
 * Renders the image variant the current surface needs and nothing else:
 * - Cards (sc-183) pass the WIDEST `imageSrcset` title variant with
 *   `sizes="100vw"` (founder: monitor-max-resolution srcset baseline), so
 *   next/image emits a responsive multi-width srcset downscaled from a
 *   high-res source — sharp from mobile 100vw @ DPR3 to desktop. Lazy.
 * - The detail hero passes the FULL `imageUrl` (full-res original) with `eager`.
 *
 * The URL is consumed straight from the payload — the frontend never builds it.
 * Contract (docs/design/sc-157-image-variants.md): same-origin
 * `GET /v1/listings/{listingId}/image?variant=thumbnail|thumbnail_768|
 * thumbnail_1280|thumbnail_1920|full`. Because the variant is selected at the
 * source and the source is same-origin, no `images.remotePatterns` or CSP
 * change is needed — next/image's built-in optimizer serves it through the
 * existing `/_next/image` route.
 *
 * `fill` + the parent's aspect-ratio box reserve space so there is no layout
 * shift; `object-fit: cover` crops to the box. A quiet kraft-stamp placeholder
 * is shown only when there is NO source at all (listing has no photo yet /
 * loading) OR every source has failed — never the browser's broken-image icon
 * (design spec detail-page.md §1.1: "broken image -> same placeholder").
 *
 * Graceful retry (sc-183, review round 2): a listing ingested BEFORE the
 * multi-width variants existed stores only the ≤400px thumbnail + full, yet
 * the backend advertises `imageSrcset` [400..1920] — so the card's WIDEST
 * source can 404. `fallbackSrc` (cards pass `cardThumbFallback`) lets a failed
 * primary swap to the guaranteed stored thumbnail instead of dropping straight
 * to the placeholder, preserving sc-157's "a listing with a valid photo always
 * renders its thumbnail". Fully-ingested rows serve their widest source 200 and
 * never hit the retry. The detail hero omits `fallbackSrc` (its full source is
 * always stored; failure → placeholder, as before).
 *
 * Note: this renderer is deliberately ONE static `sizes` + `src` per attempt —
 * the browser resolves the srcset ONCE at initial load from viewport +
 * devicePixelRatio. No re-render / re-fetch happens on viewport resize (correct
 * srcset behavior; founder sc-183 non-goal). The retry chain only advances on
 * an explicit load ERROR, never on resize.
 */
export function RestaurantPhoto({
  src,
  fallbackSrc,
  alt,
  sizes,
  eager = false,
}: {
  src?: string;
  /**
   * sc-183 retry: when `src` fails (e.g. a widest srcset variant that a legacy
   * pre-multi-width row never stored → 404), fall back to this guaranteed
   * source before the placeholder. Cards pass `cardThumbFallback`
   * (imageThumbnailUrl / narrowest srcset entry); the detail hero omits it.
   */
  fallbackSrc?: string;
  alt: string;
  sizes: string;
  /** Detail hero (LCP) loads eager; cards stay lazy. */
  eager?: boolean;
}) {
  // Source chain: primary, then fallback, deduped. Advancing on each `onError`
  // lets a 404 swap to the guaranteed thumbnail instead of a blank card; the
  // placeholder appears only when every candidate has failed (or none exist).
  const candidates = [src, fallbackSrc]
    .filter((s): s is string => !!s)
    .filter((s, i, all) => all.indexOf(s) === i);
  const [attempt, setAttempt] = useState(0);

  // The retry chain is per-image: when a different listing's `src` arrives
  // (client-side nav), reset it so a prior image's failure isn't carried over.
  const [prevSrc, setPrevSrc] = useState(src);
  if (src !== prevSrc) {
    setPrevSrc(src);
    setAttempt(0);
  }

  const current = candidates[attempt];
  if (!current) {
    return <Placeholder />;
  }

  const style: CSSProperties = { objectFit: "cover" };

  return (
    <Image
      // Remount per candidate so next/image issues a fresh request + srcset for
      // the fallback (and hooks onError to the newly rendered element).
      key={current}
      src={current}
      alt={alt}
      fill
      sizes={sizes}
      loading={eager ? "eager" : "lazy"}
      style={style}
      onError={() => setAttempt((a) => a + 1)}
    />
  );
}

/** Decorative kraft stamp-line placeholder (reused for absent AND failed images). */
function Placeholder() {
  return (
    <div className="absolute inset-0 flex items-center justify-center opacity-40">
      {/* decorative stamp-line illustration placeholder */}
      <svg
        aria-hidden="true"
        width="48"
        height="48"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.5"
        className="text-ink-400"
      >
        <rect x="3" y="3" width="18" height="18" rx="3" />
        <path d="M8 12h8M8 15.5h5" />
      </svg>
    </div>
  );
}