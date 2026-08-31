import Image from "next/image";
import { useState, type CSSProperties } from "react";

/**
 * Restaurant hero/card photo — sc-157 (responsible renderer) + sc-171.
 *
 * Renders the image variant the current surface needs and nothing else:
 * - Cards pass the SMALL `imageThumbnailUrl` (≤400px, low bandwidth).
 * - The detail hero passes the FULL `imageUrl` (full-res original) with `eager`.
 *
 * The URL is consumed straight from the payload — the frontend never builds it.
 * Contract (docs/design/sc-157-image-variants.md): same-origin
 * `GET /v1/listings/{listingId}/image?variant=thumbnail|full`. Because the
 * variant is selected at the source and the source is same-origin, no
 * `images.remotePatterns` or CSP change is needed — next/image's built-in
 * optimizer serves it through the existing `/_next/image` route.
 *
 * `fill` + the parent's aspect-ratio box reserve space so there is no layout
 * shift; `object-fit: cover` crops to the box. When `src` is absent (listing
 * has no ingested photo yet / loading state) OR the image FAILS to load
 * (network / 500 / broken bytes), the same quiet kraft-stamp placeholder is
 * rendered — never the browser's broken-image icon (design spec detail-page.md
 * §1.1: "broken image -> same placeholder"). Applies to both the card thumbnail
 * and the detail hero.
 */
export function RestaurantPhoto({
  src,
  alt,
  sizes,
  eager = false,
}: {
  src?: string;
  alt: string;
  sizes: string;
  /** Detail hero (LCP) loads eager; cards stay lazy. */
  eager?: boolean;
}) {
  const [errored, setErrored] = useState(false);

  if (!src || errored) {
    return <Placeholder />;
  }

  const style: CSSProperties = { objectFit: "cover" };

  return (
    <Image
      src={src}
      alt={alt}
      fill
      sizes={sizes}
      loading={eager ? "eager" : "lazy"}
      style={style}
      onError={() => setErrored(true)}
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