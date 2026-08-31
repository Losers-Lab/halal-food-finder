import Image from "next/image";
import tahirHead from "@/assets/tahir-head.jpg";

/**
 * Tahir's List brand primitives — spec: docs/design/tahir-brand.md (§3 logo
 * system), docs/design/tokens.md (C2 lockup). Canonical art:
 * brand/tahir/tahir-head.jpg. Consumed by the site header, footer, and auth
 * card. Never render "Tahir's List" without the red apostrophe.
 */

/**
 * Wordmark "Tahir's List" — Archivo Black (font-wordmark, weight 900),
 * tight tracking (-0.03em), ink-900 on paper; the apostrophe is always brand
 * red (#C6381F / brand-500). Size is left to the consumer via `className`.
 */
export function Wordmark({ className = "" }: { className?: string }) {
  return (
    <span
      className={`font-wordmark font-black tracking-[-0.03em] text-ink-900 ${className}`}
    >
      Tahir<span className="text-brand-500">&#39;</span>s List
    </span>
  );
}

/**
 * C2 head app-icon — the Tahir head crop (kufi + glasses) in a rounded square
 * with a hard ink offset shadow (logo-lockups.html). Size via `className`
 * (desktop header 56px, mobile 40px; smaller in compact slots). Decorative
 * within a labeled link, so the image is alt-empty.
 */
export function TahirHeadIcon({ className = "" }: { className?: string }) {
  return (
    <Image
      src={tahirHead}
      alt=""
      width={112}
      height={112}
      className={`rounded-[29%] object-cover shadow-pop ${className}`}
    />
  );
}