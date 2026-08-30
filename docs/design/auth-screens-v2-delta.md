# Auth Screens Reskin Delta — "Stamps & Search" v2 (restyle-only)

Scope: applies the ratified stamps system to the EXISTING auth spec (sections below override `auth-screens.md` M1 visual tokens). **Behavior, fields, error paths, copy, and accessibility logic are UNCHANGED.** Read together with auth-screens.md — that file remains the source of truth for states/errors.

## What changes (visual only)

1. **Page background:** `neutral-50` → `cream-50` (warm paper). No texture image in v1 (flat cream; kraft texture is a follow-up if founder wants).
2. **Card:** white → `ink-0`, `radius-lg` (10px), border 1.5px `kraft-200` (new), shadow-card → hard offset `shadow-card: 3px 3px 0 rgb(43 33 24 / 0.9)`. Max-width 400px and mobile full-bleed behavior unchanged.
3. **Brand mark:** keep the small wordmark slot; render placeholder "HalalMarket" in Archivo 800 `brand-500` (matches site header). No real logo (founder TBD).
4. **Typography:** headings → Archivo (`text-title` = 24/30, weight 700); body/labels → Space Grotesk; labels become `text-label` (Archivo 600, 14px, `ink-700`). Copy unchanged.
5. **Inputs:** height 44px, `radius-md` (6px), bg `ink-0`, border 1.5px `kraft-300`; focus border + 2px ring `brand-500` (offset 0). Helper text `ink-500`.
6. **Primary submit button:** brand-500 bg, `cream-50` text, Archivo 600 (`text-label`), hard `shadow-chip`. Press: translate(2px,2px) + shadow collapses ("stamp down"); reduced-motion: background darkens to brand-600 instead. Submitting state (disabled + "Creating account…"/"Logging in…") unchanged.
7. **Links** ("Forgot password?", "Log in"/"Sign up" cross-links, Terms/Privacy): `brand-500`, underline on hover.
8. **Field errors:** same anatomy/messages, but colors → `danger-500` border, `danger-600` text; alert triangle icon unchanged. Red remains error-only; it is visually distinct from brand red by the alert-icon pairing (binding rule in tokens.md §1).
9. **Top-of-card server error banner:** `danger-50` bg, `danger-100` → keep, left border 3px `danger-500`, alert triangle + `danger-600` text. `role="alert"` + focus behavior unchanged.
10. **Password show/hide toggle:** ghost icon button inside input, `ink-400`, 44px hit area, `aria-pressed` — unchanged logic.
11. **Trust line** ("By signing up you agree…"): `text-small ink-500`, links `brand-500`.

## What must NOT change (explicit non-goals)

- Field set, order, autocompletes, validation rules, all error copy, the enumeration-caution login behavior, focus order, mobile 16px-input rule, `aria` wiring — all per auth-screens.md as written.
- No new buttons (no social/OAuth), no extra trust badges on auth cards. The auth card stays minimal.

## Mapping cheat-sheet (old → new token)

| Old (M1) | New (v2) |
|---|---|
| neutral-50 page | cream-50 |
| white card | ink-0 |
| brand-500 teal | brand-500 #C6381F |
| neutral-* text/borders | ink-500 / ink-700 / kraft-200·300 |
| danger-* | danger-* (new hexes) |
| soft shadow-card | hard offset shadow-card |
| Inter | Archivo (display/labels) + Space Grotesk (body) |

## Open questions

None new — auth open questions from auth-screens.md §4 carry over unchanged.
