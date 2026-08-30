# Search & Browse Screen Spec — Halal Food Finder ("Stamps & Search")

Status: implementation-ready · Matches approved sketch `sketches/006-stamps-search` · Owner: Fatima
Tokens: `tokens.md` (v2 Stamps & Search). Trust components: `trust-components.md` (v2 restyle below).
Stack: Next.js + TS + Tailwind v4. Brand wordmark: "Tahir's List" (apostrophe brand red) — founder-ratified 2026-08-30.

## Primary user flow

Land on page → search box is the first thing the eye hits (search-FIRST, no map on the homepage — founder rejected map-first) → type a query or use browse chips → results grid → tap a card → detail page.

---

## 1. Page anatomy (desktop)

```
┌──────────────────────────────────────────────────────────────┐
│ [Tahir's List wordmark, apostrophe brand-500] Log in   [Sign up]│  ← header, cream-50, kraft-200 bottom border
├──────────────────────────────────────────────────────────────┤
│        Find halal food near you. Stamped & trusted.          │  ← text-display, ink-900
│  ┌──────────────────────────────────────┐  ┌───────────┐     │
│  │ 🔍 Search restaurants, dishes…       │  │  Search   │     │  ← input ink-0, brand-500 button
│  └──────────────────────────────────────┘  └───────────┘     │
│   Browse: [All] [Hand-cut] [Machine-cut] [Near me]           │  ← dark chips (ink-900)
├──────────────────────────────────────────────────────────────┤
│  24 spots near you                    [Map] [List]           │  ← results toolbar (v1: List only)
│  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐                │
│  │ card   │ │ card   │ │ card   │ │ card   │                │  ← listing cards grid
│  └────────┘ └────────┘ └────────┘ └────────┘                │
├──────────────────────────────────────────────────────────────┤
│  Footer: About · How verification works · Add a restaurant   │
└──────────────────────────────────────────────────────────────┘
```

### Header (all screens)
- Height 64px, bg `cream-50`, bottom border 1.5px `kraft-200`.
- Wordmark slot left: "Tahir's List" Archivo 800, `brand-500`, apostrophe brand red, hard shadow-chip on a small red seal behind it (lockup spec: `brand/tahir/logo-lockups.html`).
- Right: "Log in" = ghost button (ink-700 text, transparent bg); "Sign up" = primary (brand-500, shadow-chip, press-down interaction).
- Mobile: header collapses to wordmark + hamburger → slide-down panel (bg cream-50, links stacked, min 44px rows). Hamburger `aria-expanded`, panel closes on Esc/route change.

### Hero + search
- Hero: `bg-cream-50`, headline `text-display` ink-900, max-width ~640px. Optional one-line subcopy `text-body` ink-500: "Real certifications, reviewed by people — not just a label." (final copy can be tuned).
- Search bar: 56px tall, `radius-md`, bg `ink-0`, border 1.5px `kraft-300`, hard shadow-card; focus → border brand-500 + 2px focus ring. Placeholder: "Search restaurants, dishes, or area…".
- Search button: primary brand-500, 56px, to the right on desktop; on mobile full-width below the input, icon+label.
- Behavior: Enter or button submits → `/search?q=…`. While typing ≥2 chars, show suggestion dropdown (bg ink-0, shadow-pop): up to 6 restaurant names + matching areas; each row 44px min, keyboard navigable (↑/↓/Enter, Esc closes, `aria-activedescendant`).
- Browse chips row: dark chips (`bg-ink-900 text-cream-50 shadow-chip radius-full`); active chip = brand-500 bg. Chip press-down interaction. These filter the list (cut method, distance sort). Each chip is a real control with `aria-pressed`.

### Results toolbar
- Left: result count, `text-body` ink-500 ("24 spots near you" / "No exact match — showing similar").
- Right: view toggle **List | Map**. v1 ships List only; Map toggle renders but shows "Map coming soon" popover on click (do not fake a map). Toggle = dark chip group, active segment brand-500.

### Listing cards (grid)
- Grid: 4-col ≥1280px, 3-col ≥1024, 2-col ≥768, 1-col below. Gap `space-6`.
- Card: bg `ink-0`, `radius-lg`, border 1.5px `kraft-200`, shadow-card, hover: translateY(-2px) + shadow-pop (200ms ease-out; disabled under prefers-reduced-motion). Whole card clickable (name link inside is the accessible element; card click is enhancement, never the only path).
- Card anatomy (top→bottom): photo (16:9, kraft-100 placeholder with stamp-line illustration while image loads) → name `text-heading` ink-900 + VerifiedBadge/UnverifiedTag (right-aligned, same slot both states — no layout shift) → meta row `text-small` ink-500 (cuisine · distance · ★ rating) → CutMethodIndicator chip if known.
- Verification badge restyle (v2, binding): stamp mark — bg `stamp-50`, 1.5px **dashed** `stamp-200` border, stamp/seal icon + word "Verified" in `stamp-700`, `radius-sm`, shadow-stamp. On-photo variant: solid `stamp-500`, cream text/icon. UnverifiedTag: `ink-100` bg, 1.5px solid `ink-300` border, outlined circle + "Unverified" `ink-500`, `radius-sm` — identical metrics, quiet, never red. (Full rules in trust-components.md.)

### Card states
- **Loading:** skeleton per card — photo block + two text bars, `ink-100` bg with a 1.5s shimmer (reduced-motion: static blocks). 8 skeletons.
- **Empty:** centered panel, `bg-ink-0 border dashed kraft-300 radius-lg`, stamp illustration, heading "Nothing matches yet" (`text-title`), body: "Try a different search — or add the spot you know." + secondary button "Add a restaurant" (dark chip → /add). Never show an error state for zero results.
- **Error (network/API):** danger alert banner above grid: `danger-50` bg, alert triangle, "We couldn't load results. [Retry]" — Retry = dark chip. Body of page still renders (header/hero intact).
- **Partial/low data:** cards render with what exists; missing meta simply omitted (no "N/A", no placeholder dashes).

### Sort & filter (v1 scope)
- Only the browse chips + implicit distance sort. No filter drawer in v1. If result count > 50, add a simple "Sort: Distance | Rating" select (native `<select>` styled dark-chip) — confirm with Maryam before adding.

---

## 2. Mobile (≤768px) — full spec in `mobile-bottom-tab.md`

Summary: header stays (wordmark + hamburger); search input full-width, button below; cards single column; bottom tab bar (Search / Add / Saved / Account) fixed, `bg-ink-0`, top border kraft-200, 56px tabs + safe-area inset; content padded bottom so tab bar never covers cards.

---

## 3. Accessibility checklist

- Search input has `<label class="sr-only">`; suggestions implemented as `role="listbox"` + `aria-expanded` on the combobox.
- Cards: one primary link (restaurant name); photo `alt="Photo of {name}"` or empty alt if decorative placeholder.
- Badges/chips: text carries meaning; icons `aria-hidden`.
- Focus order: header links → search → chips → cards → footer. Focus ring on EVERY interactive element (2px brand-500, offset 2px).
- Result-count region `aria-live="polite"` so filter changes are announced.

## 4. Responsive summary

| Breakpoint | Layout |
|---|---|
| ≥1280 | 4-col grid, inline search+button |
| 1024–1279 | 3-col |
| 768–1023 | 2-col, search stacks |
| <768 | 1-col, bottom tab bar, full-width search + button |

## 5. Trust messaging

- Hero subcopy names the promise: reviewed certifications.
- Every verified card carries the stamp badge; every unverified card the quiet neutral tag with the "How verification works" tooltip link (see trust-components.md §2).
- Footer link "How verification works" is mandatory on all screens (trust anchor).

## 6. Open questions

1. Map view: v1 placeholder popover — confirm with Adnan whether map should ship in MVP at all.
2. Final hero subcopy wording (placeholder provided).
3. Sort control needed only if result sets get large — flag to Maryam.
