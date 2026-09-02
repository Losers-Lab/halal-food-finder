"use client";

import type { BrowseFilter } from "@/lib/listings/data";

/**
 * Browse chips — docs/design/search-browse.md §"Browse chips row". Dark chips
 * (bg-ink-900 text-cream-50 shadow-chip radius-full); active chip = brand-500 bg.
 * Each is a real control with aria-pressed. "ALL" = no filtered read; the
 * "Hand-cut" chip narrows to hand-cut listings only and "Delivery" to listings
 * offering delivery (sc-184) — two independent on/off boolean extras, exactly as
 * the sc-42 hand-cut filter. Single-select (one active chip) to stay consistent
 * with the current control.
 */
export function BrowseChips({
  active,
  onChange,
}: {
  active: BrowseFilter;
  onChange: (f: BrowseFilter) => void;
}) {
  const chips: { value: BrowseFilter; label: string }[] = [
    { value: "ALL", label: "All" },
    { value: "HAND_CUT", label: "Hand-cut" },
    { value: "DELIVERY", label: "Delivery" },
  ];

  return (
    <div role="group" aria-label="Browse filters" className="flex flex-wrap gap-2">
      {chips.map((chip) => {
        const isActive = active === chip.value;
        return (
          <button
            key={chip.value}
            type="button"
            aria-pressed={isActive}
            onClick={() => onChange(chip.value)}
            className={`inline-flex min-h-11 items-center rounded-full px-5 text-label transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-500 ${
              isActive
                ? "bg-brand-500 text-cream-50"
                : "bg-ink-900 text-cream-50 shadow-chip hover:bg-ink-700"
            }`}
          >
            {chip.label}
          </button>
        );
      })}
    </div>
  );
}