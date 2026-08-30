# Mobile Bottom Tab Bar Spec — Halal Food Finder ("Stamps & Search")

Status: implementation-ready · Matches approved sketch `sketches/008-stamps-mobile` · Owner: Fatima
Tokens: `tokens.md` (v2). Applies to all screens at ≤768px. Desktop is unaffected (header nav only).

## Why a tab bar

MVP is web-first, but the mobile web experience should already feel app-like so the future native app doesn't require a relearn. Four destinations, no hamburger buried features (hamburger is reserved for secondary links: About, How verification works, Log out).

## 1. Anatomy

```
┌──────────────────────────────────────────┐
│  content (padding-bottom = tab height    │
│  + safe-area + space-4)                  │
├──────────────────────────────────────────┤
│   🔍        ➕        🔖        👤       │
│  Search    Add     Saved   Account      │
└──────────────────────────────────────────┘
```

- Fixed to viewport bottom, full width. `bg-ink-0`, top border 1.5px `kraft-200`, `shadow-pop` inverted (hard offset upward: `-2px -2px 0 rgb(43 33 24 / 0.9)` is NOT used — shadow only on top edge via 0 -2px 0 kraft-300, keep it quiet).
- Height: 56px bar + `env(safe-area-inset-bottom)` padding.
- 4 tabs, equal width, each ≥44×44px touch target.

### Tab items (fixed order)
| # | Tab | Icon | Route |
|---|---|---|---|
| 1 | Search | magnifier (line) | `/` or `/search?q=` |
| 2 | Add | plus in square (line) | `/add` |
| 3 | Saved | bookmark (line) | `/saved` |
| 4 | Account | person (line) | `/account` |

Icons: 24px line style, 2px stroke, `ink-500`. Active tab: icon `brand-500` + label `text-label` brand-500 + 3px hard underline bar (3px, brand-500, radius-full, directly under icon) — state is icon+label+position, never color alone.

### Labels
- Always visible under icon (`text-small`, 11px min). No icon-only tabs — labels are the accessibility and comprehension layer.
- Each tab: `<a>` with `aria-current="page"` when active.

## 2. Behavior

- Tab switches preserve each destination's scroll position (restore on return) — standard app behavior, low cost with Next.js.
- "Add" is intentionally elevated in the flow (users adding restaurants is core to listing-first) but styled identically to other tabs — no floating action button, no special color. One look.
- When auth required (Saved, Account for logged-out users): route to Log In (sc-40) with a `?next=` param; after login return to the intended tab. Do not silently show an empty state.
- Keyboard focus (BT keyboards, a11y): tabs are in normal tab order; 2px brand-500 focus ring, offset -2px inside the bar so it isn't clipped.

## 3. Interactions with other screens

- Detail page: tab bar visible; page content gets bottom padding = 56px + safe-area + 16px so action buttons/panel never sit under the bar.
- Search screen: tab bar sits below the results list; when the suggestion dropdown is open it renders above the bar (z-index: dropdown 30, tab bar 20).
- Modals/lightbox (view certificate): overlay z-index above the tab bar; tab bar inert (aria-hidden + inert) while modal is open.

## 4. States

| State | Display |
|---|---|
| Loading route transition | No skeleton in the bar; tabs are instant (client nav). |
| Saved — empty | Saved screen (not the bar) shows the empty state: stamp illustration, "Nothing saved yet", dark chip "Find halal spots". |
| Offline | Normal bar; screens themselves show the shared offline banner. The bar never indicates connectivity. |

## 5. Accessibility checklist

- `<nav aria-label="Primary">` wrapping the tab list.
- Touch targets ≥44px, separated by the full tab width (no adjacent tiny targets).
- Labels are real text (not background images); icons `aria-hidden`.
- Active state announced via `aria-current="page"`.
- Respect `prefers-reduced-motion`: any active-state transition (underline slide) is ≤150ms or instant.

## 6. What the tab bar is NOT

- No badge counts/notifications in v1.
- No hamburger inside the bar; secondary links stay in the header menu.
- No "Map" tab — founder rejected map-first; map is at most a view toggle inside Search (see search-browse.md).

## 7. Open questions

1. Saved persists server-side or localStorage in MVP? Affects whether logged-out users can use the Saved tab without an account wall. Ask Maryam/Adnan — default proposal: localStorage for logged-out, merge on login (do not build the merge silently; confirm first).
