import type { ReactNode, SVGProps } from "react";

/**
 * Trust-language primitives — spec: docs/design/trust-components.md.
 * Tokens: docs/design/tokens.md (consume tokens only, no local hex values).
 * These are stubs (static UI, no data wiring); props kept minimal.
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

function GearIcon(props: SVGProps<SVGSVGElement>) {
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
      <circle cx="8" cy="8" r="2" />
      <path d="M8 1.5v2M8 12.5v2M1.5 8h2M12.5 8h2M3.4 3.4l1.4 1.4M11.2 11.2l1.4 1.4M12.6 3.4l-1.4 1.4M4.8 11.2 3.4 12.6" />
    </svg>
  );
}

/** VerifiedBadge — `[✓ Verified]`, positive treatment, pill. */
export function VerifiedBadge() {
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full border border-positive-200 bg-positive-50 px-2.5 py-1 text-small font-semibold text-positive-700"
      title="Verified — certification reviewed by our committee"
    >
      <CheckIcon />
      Verified
    </span>
  );
}

/** UnverifiedTag — `[○ Unverified]`, neutral treatment (never error-red). */
export function UnverifiedTag() {
  return (
    <span
      className="inline-flex items-center gap-1 rounded-full border border-neutral-300 bg-neutral-100 px-2.5 py-1 text-small font-semibold text-neutral-500"
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
      className="inline-flex items-center gap-1.5 rounded-sm bg-neutral-50 px-2 py-1 text-small text-neutral-500"
      title={label}
    >
      <span className="text-neutral-700">{icon}</span>
      {children}
    </span>
  );
}

/** HandCutIndicator — `[✂ Hand-cut]`, quiet neutral chip. */
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

/** MachineCutIndicator — `[⚙ Machine-cut]`, same neutral chip (icon + word differ, never hue). */
export function MachineCutIndicator() {
  return (
    <CutMethodIndicatorBase
      icon={<GearIcon />}
      label="Zabiha method: animal slaughtered by automated mechanical process"
    >
      Machine-cut
    </CutMethodIndicatorBase>
  );
}
