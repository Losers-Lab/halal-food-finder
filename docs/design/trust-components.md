# Trust-Language Component Specs — Tahir's List

> **v2 restyle note (2026-08-30):** tokens are superseded by `tokens.md` ("Stamps & Search", founder-ratified). Structure/behavior below stands; colors change as follows — VerifiedBadge: `stamp-50` bg, 1.5px **dashed** `stamp-200` border, `stamp-700` text/icon, `radius-sm`, `shadow-stamp`; on-photo variant: solid `stamp-500`, cream text; compact variant: solid stamp-500 circle. UnverifiedTag: `ink-100` bg, `ink-300` border, `ink-500` text (was neutral-slate; now warm ink). CutMethodIndicator unchanged except border `kraft-200`. All other rules (icon+text pairing, never-red-for-unverified, omit-when-unknown) remain binding.

MVP-wide primitives, defined once here. Consumers: listing cards, restaurant pages, search results, auth UI (t_514f91af). Tokens: see `tokens.md`.

The core UX question: **"What does the user think is happening here?"** — a verification signal must be instantly readable and impossible to misread. These components carry the product's central promise (amanah), so their rules are binding.

---

## 1. VerifiedBadge (positive)

**Anatomy:** `[✓ icon] Verified` — checkmark SVG (stroke, 16px) + word "Verified".

- Background: `positive-50`; border 1px `positive-200`; text/icon `positive-700`.
- Radius: `full` (pill). Padding: 4px 10px. Text: `text-small`, weight 600.
- Icon is mandatory. Hue is supporting, never the sole signal.

**Variants:**
- `default` — as above (listing cards, search results).
- `on-photo` — used overlaid on restaurant photos: solid `positive-500` background, white icon+text, `shadow-pop`. Same anatomy.
- `compact` — icon-only 24×24 circle (solid `positive-500`, white check, `shadow-pop`); **requires `aria-label="Verified restaurant"`** and a tooltip "Verified — certification reviewed by our committee". Icon-only allowed ONLY where the full badge appears elsewhere on the same screen (e.g. photo overlay + badge in card body).

**Do:**
- DO always include the word "Verified" (or the compact rule above).
- DO link/tap-through on restaurant pages → opens the Verified Certification Display (cert image, issuer, expiry).
**Don't:**
- DON'T use red, amber, or gray for verified status.
- DON'T animate pulse/spin — a trust mark is calm.
- DON'T scale below 24px hit/visual size.

## 2. UnverifiedTag (neutral — NEVER error-red)

**Anatomy:** `[○ outline icon] Unverified` — outlined circle SVG (16px) + word "Unverified".

- Background: `neutral-100`; border 1px `neutral-300`; text/icon `neutral-500`.
- Radius: `full`. Same metrics as VerifiedBadge so the pair reads as siblings.

**Explicit do/don't (binding):**
- DO keep it visually quiet — it is a state, not a punishment. Listing-first means anyone can add; users adding restaurants should not feel scolded.
- DO show the explanatory affordance: hover/focus tooltip or tap → popover: "This listing hasn't been verified yet. Owners can claim it and submit their halal certification for review." with a link "How verification works".
- DON'T use `danger-*` tokens, red, or error iconography anywhere in this component.
- DON'T use language like "Not certified", "Failed", "Untrusted", warning triangles. The word is exactly "Unverified".
- DON'T place it adjacent to error text styling or shake/pulse animations.

**Colorblind check:** differentiation from Verified is (a) filled-positive vs outlined-neutral, (b) checkmark vs circle glyph, (c) label text. Passing all three means deuteranopia/protanopia/tritanopia users get identical information.

## 3. HandCutIndicator

**Anatomy:** `[icon] Hand-cut` — icon 16px + label. There is NO machine-cut
state (sc-42 founder ruling): hand-cut is an extra boolean a listing either
claims or not, and the indicator is shown ONLY when the listing is hand-cut.
When hand-cut is false/unknown, omit the component entirely.

| | Icon | Color | Glyph concept |
|---|---|---|---|
| Hand-cut | `scissors` (line icon) | `neutral-700` icon, `neutral-500` text on `neutral-0/50` | hands/scissors |

- Presentation: quiet chip, radius `sm`, padding 4px 8px, `text-small`. NOT a colored pill — hand-cut is information, not a judgment.
- Tooltip/`title`: "Zabiha method: animal slaughtered by hand".

**Don't:**
- DON'T render a distinct machine-cut state — it does not exist.
- DON'T add a second style for "unknown" — if not hand-cut, omit the component entirely (see Empty State rule).

## 4. Shared rules (all three components)

1. **One source of truth:** implement as React components `VerifiedBadge`, `UnverifiedTag`, `HandCutIndicator` in `components/trust/`; consume tokens only; no local hex values.
2. **Never derive status styling ad hoc** on screens; always use these components.
3. **Empty/unknown state:** when data is absent (e.g. cut method unknown), omit the component — never render a placeholder-styled shell that looks like a third status.
4. **Loading state:** skeleton shimmer blocks matching final size (badge: 84×28px pill; indicator: 92×24px chip). No spinners inside trust marks.
5. **Motion:** at most a 150ms ease-out opacity on appearance; nothing else.
6. **A11y:** all icons `aria-hidden`; component text carries the meaning; interactive tooltips are focusable and dismissible (Esc).

## 5. Composition example (listing card)

```
┌────────────────────────────────────────────┐
│ [photo 16:9, on-photo badge if verified]   │
│                                          │
│ Al-Amir Grill              [✓ Verified]   │
│ Middle Eastern · 1.2 mi · ★ 4.6 (89)      │
│ [✂ Hand-cut]                              │
└────────────────────────────────────────────┘
```

Unverified card: same layout, `Unverified` tag in the same slot — spacing identical so verified/unverified cards align in a grid (no layout shift between states).
