import type { ReactNode, SVGProps } from "react";

/**
 * Trust-language primitives — spec: docs/design/trust-components.md, reskinned
 * to tokens.md "Stamps & Search" v2 (2026-08-30 restyle note). Tokens only — no
 * local hex values. These are static UI primitives; consumers wire data.
 *
 * Binding rules (never violate):
 * - Verification signal always = icon + word, never hue alone (colorblind-safe).
 * - "Unverified" NEVER uses danger red or warning iconography — quiet ink neutral.
 * - Stamp green is exclusive to Verified / certificate trust; danger red is
 *   reserved for genuine errors.
 */
function CheckIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      aria-hidden="true"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      {...props}
    >
      <path d="M3 8.5 6.5 12 13 4.5" />
    </svg>
  );
}

function OutlineCircleIcon(props: SVGProps<SVGSVGElement>) {
  return (
    <svg
      aria-hidden="true"
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      {...props}
    >
      <circle cx="8" cy="8" r="5.5" />
    </svg>
  );
}

function ScissorsIcon(props: SVGProps<SVGSVGElement>) {
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
      {...props}
    >
      <circle cx="4" cy="12" r="2" />
      <circle cx="4" cy="4" r="2" />
      <path d="M5.8 5.2 13 12M5.8 10.8 13 4" />
    </svg>
  );
}

type SealMarkProps = SVGProps<SVGSVGElement> & { srLabel?: string };

/**
 * Seal/stamp mark — the trust seal glyph. Used as the brand logo slot, the
 * certificate panel header, and empty-state illustration. Renders a solid
 * currentColor stamp badge with a cream check. Always decorative; consume with
 * an aria-hidden unless given an srLabel.
 */
export function SealMark({ srLabel, ...props }: SealMarkProps) {
  return (
    <svg
      aria-hidden={srLabel ? undefined : true}
      viewBox="0 0 24 24"
      fill="none"
      width="24"
      height="24"
      {...props}
    >
      {srLabel ? <title>{srLabel}</title> : null}
      <rect x="2" y="2" width="20" height="20" rx="5" fill="currentColor" />
      <path
        d="M7.5 12.5 10.8 15.5 16.8 8.8"
        stroke="var(--color-cream-50)"
        strokeWidth="2.4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

type VerifiedBadgeProps = {
  /** default = quiet dashed stamp badge; on-photo = solid stamp over imagery. */
  variant?: "default" | "on-photo";
};

/** VerifiedBadge — `[✓ Verified]` stamp badge, stamp-green treatment. v2 (binding). */
export function VerifiedBadge({ variant = "default" }: VerifiedBadgeProps) {
  if (variant === "on-photo") {
    return (
      <span
        className="inline-flex items-center gap-1 rounded-sm bg-stamp-500 px-2.5 py-1 text-small font-semibold text-cream-50 shadow-stamp"
        title="Verified — certification reviewed by our committee"
      >
        <CheckIcon />
        Verified
      </span>
    );
  }
  return (
    <span
      className="inline-flex items-center gap-1 rounded-sm border-[1.5px] border-dashed border-stamp-200 bg-stamp-50 px-2.5 py-1 text-small font-semibold text-stamp-700 shadow-stamp"
      title="Verified — certification reviewed by our committee"
    >
      <CheckIcon />
      Verified
    </span>
  );
}

/** UnverifiedTag — `[○ Unverified]`, quiet warm-ink neutral. NEVER error-red. */
export function UnverifiedTag() {
  return (
    <span
      className="inline-flex items-center gap-1 rounded-sm border border-ink-300 bg-ink-100 px-2.5 py-1 text-small font-semibold text-ink-500"
      title="This listing hasn't been verified yet. Owners can claim it and submit their halal certification for review."
    >
      <OutlineCircleIcon />
      Unverified
    </span>
  );
}

type CutMethodProps = { children: ReactNode; icon: ReactNode; label: string };

function CutMethodIndicatorBase({ children, icon, label }: CutMethodProps) {
  return (
    <span
      className="inline-flex items-center gap-1.5 rounded-sm border border-kraft-200 bg-ink-0 px-2 py-1 text-small text-ink-500"
      title={label}
    >
      <span className="text-ink-700">{icon}</span>
      {children}
    </span>
  );
}

/** HandCutIndicator — `[✂ Hand-cut]`, quiet neutral chip (border kraft-200). */
export function HandCutIndicator() {
  return (
    <CutMethodIndicatorBase
      icon={<ScissorsIcon />}
      label="Zabiha method: animal slaughtered by hand"
    >
      Hand-cut
    </CutMethodIndicatorBase>
  );
}