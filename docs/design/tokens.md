# Design Tokens — Tahir's List ("Stamps & Search" v2)

Status: FOUNDER-RATIFIED 2026-08-30 (Option A) · **Rebranded for Tahir's List 2026-08-30** · Owner: Fatima (design) · Consumer: Maryam (implementation)
Supersedes the teal M1 palette entirely. One look across every page — consistency is an explicit founder requirement.
Brand: **Tahir's List** (tahirslist.com) — founder pivot 2026-08-30 from "Halal Food Finder"/"HalalMarket". Tagline: **"find halal food"** (lowercase).
- Wordmark: Archivo 900, tracking ≈ -3%. The **apostrophe is always brand red** (#C6381F; soft red #E07B63 on dark surfaces). Never render the wordmark without it.
- Primary lockup (C2): Tahir head app-icon + wordmark + tagline — **used as the site header on every screen** (desktop: 56px icon; mobile: 40px icon), plus favicon/app icon. Mascot canonical image & rules: `brand/tahir/CHARACTER.md`. Logo lockups: `brand/tahir/logo-lockups.html`. Decision log: `brand/tahir/BRAND-DECISIONS.md`.
- **Dark theme:** planned future pass (light is the launch default). Direction: ink ground, cream text, soft-red accent — see the dark cell in `logo-lockups.html`.

## Design principles

1. **Warm & appetizing.** Pages feel like kraft paper and a butcher's stamp — cream surfaces, red brand accents, ink-dark type. Nothing clinical, nothing resembling zabihah.com.
2. **Trust = a stamp.** Verification is rendered as a literal stamp/seal mark (stamp green), never as traffic-light color coding.
3. **Colorblind-safe (binding).** Never hue alone: every status pairs icon + text. Verified/Unverified differ by filled-stamp vs outlined-neutral + glyph + word.
4. **Error-red is reserved.** Genuine errors ONLY. Brand red and error red never appear in the same component; errors always carry an alert icon so they're distinguishable from brand red by shape, not hue.
5. **Focus rings are never removed.**

## 1. Color tokens

Numeric ramps, base = 500. Consumable as Tailwind v4 `@theme` (§5).

### Cream / paper (page + card surfaces)

| Token | Hex | Role |
|---|---|---|
| --color-cream-50 | #FBF7EE | page background (every page) |
| --color-cream-100 | #F5EEDF | alternate section bands |
| --color-cream-200 | #EFE6D2 | subtle fills, hover on cream |

### Kraft (borders, paper texture accents)

| Token | Hex | Role |
|---|---|---|
| --color-kraft-100 | #EADFC9 | card fills on cream, kraft-tinted panels |
| --color-kraft-200 | #DCCFAF | default borders |
| --color-kraft-300 | #C9B98F | stronger borders, dashed seal borders |

### Brand — butcher red

| Token | Hex | Role |
|---|---|---|
| --color-brand-50 | #FBEDE9 | subtle brand tint |
| --color-brand-100 | #F6D9D1 | hover fills on brand surfaces |
| --color-brand-300 | #E07B63 | accents, secondary highlights |
| --color-brand-500 | #C6381F | PRIMARY actions, links, brand marks |
| --color-brand-600 | #A82D17 | primary hover |
| --color-brand-700 | #8A2411 | pressed |
| --color-brand-900 | #5C180B | darkest brand |

Brand red is used for: primary buttons, links, the wordmark/logo, price/urgent accents. It NEVER renders an alert/warning icon, never borders an error message.

### Stamp green — verification only

| Token | Hex | Role |
|---|---|---|
| --color-stamp-50 | #EDF3EE | badge background |
| --color-stamp-100 | #DCE8DF | badge hover |
| --color-stamp-200 | #C2D6C8 | badge border |
| --color-stamp-500 | #1F5C3D | stamp mark, icon fill, AA text on light |
| --color-stamp-600 | #17492F | hover/pressed |
| --color-stamp-700 | #113A25 | text on stamp-50 (AA) |

Exclusive to the Verified stamp badge / certificate trust panel. Don't use stamp green for generic "success toasts" — form success uses neutral ink confirmation, keeping green synonymous with certification.

### Ink — warm neutrals (text, dark chip buttons)

| Token | Hex | Role |
|---|---|---|
| --color-ink-0 | #FFFDF6 | card background (paper white, warm) |
| --color-ink-100 | #F1EAD9 | subtle fill |
| --color-ink-200 | #DCD2BE | disabled fills |
| --color-ink-300 | #B8AB90 | borders (strong), dividers |
| --color-ink-400 | #8A7D63 | disabled text, placeholder icons |
| --color-ink-500 | #6B5F4B | secondary text |
| --color-ink-700 | #453A2B | body text |
| --color-ink-900 | #2B2118 | headings, dark chip button background |

Dark chip buttons: bg `ink-900`, text `cream-50`. These are the SECONDARY button style (filters, chips, "Map" toggle); primary actions stay brand red.

### Error — reserved exclusively for genuine errors

| Token | Hex | Role |
|---|---|---|
| --color-danger-50 | #FBEFEF | error surface |
| --color-danger-100 | #F3D8D8 | error border tint |
| --color-danger-500 | #B3261E | error icon/text, error input border |
| --color-danger-600 | #921D16 | error text (AA on 50) |

Binding separation rules:
- Danger always appears with the alert triangle icon + text; brand red never does. Users distinguish error vs brand by glyph and context, not hue.
- "Unverified" NEVER uses danger tokens, red, or warning iconography — it is a neutral ink outline tag (see trust-components.md).
- Danger-500 is visually distinct from brand-500 (#C6381F is orange-leaning; #B3261E is cooler/deeper) but the icon+text pairing is the binding rule.

### Warning (informational only — e.g. "certification expires soon")

| Token | Hex | Role |
|---|---|---|
| --color-warning-50 | #FBF3E2 | surface |
| --color-warning-100 | #F3E5C4 | border |
| --color-warning-700 | #8A6114 | icon/text (AA on 50) |

### Focus

- `--color-focus: #C6381F` (brand-500). Ring: 2px solid, offset 2px. Never remove outlines; on brand-red surfaces use ink-900 ring instead.

### Colorblind safety rules (binding)

- DO pair stamp green "Verified" with the stamp/seal glyph and the word "Verified".
- DO keep "Unverified" in warm ink neutral with an outlined shape — hue-independent differentiation.
- DO use icon + text for hand-cut/machine-cut indicators.
- DON'T encode meaning in red vs green alone (~8% of male users can't distinguish them).
- DON'T use stamp green or brand red decoratively where they'd read as status.

## 2. Typography

Fonts (Google, free): **Archivo** — headings, buttons, labels, wordmark. **Space Grotesk** — body, UI text, numerals (ratings/distances).

| Token | Size / Line | Weight / Family | Use |
|---|---|---|---|
| --text-display | 40/44 | Archivo 800 | Homepage hero, page titles |
| --text-title | 24/30 | Archivo 700 | Screen titles |
| --text-heading | 18/24 | Archivo 700 | Card headings, restaurant names |
| --text-label | 14/18 | Archivo 600 | Buttons, chips, badges, form labels |
| --text-body | 15/24 | Space Grotesk 400 | Body copy |
| --text-small | 13/18 | Space Grotesk 400 | Helper text, captions |
| --text-mono | 13/18 | Space Grotesk / ui-monospace | cert IDs, technical strings |

Rules: body = ink-700; headings = ink-900; links = brand-500, underlined on hover; minimum interactive touch target 44px.

## 3. Spacing scale

Base 4px: `--space-1:4 · 2:8 · 3:12 · 4:16 · 5:20 · 6:24 · 8:32 · 10:40 · 12:48 · 16:64`. Form field gaps = space-4; card padding = space-5 (mobile) / space-6 (desktop); section rhythm = space-12.

## 4. Radius, borders & elevation

The stamps look uses squarish shapes and HARD offset shadows — no soft blurs.

| Token | Value | Use |
|---|---|---|
| --radius-sm | 4px | chips, small tags |
| --radius-md | 6px | inputs, buttons |
| --radius-lg | 10px | cards, modals |
| --radius-full | 9999px | pills, avatars only |

Dashed motif (the "seal"): `--border-dashed: 1.5px dashed kraft-300` — used on the verification stamp badge border, certificate panel edge, and dividers under section titles. Never used on interactive controls (reads as non-interactive by convention).

Shadows (hard offset, ink):

- `--shadow-card: 3px 3px 0 rgb(43 33 24 / 0.9)` — cards, listing tiles
- `--shadow-chip: 2px 2px 0 rgb(43 33 24 / 0.9)` — buttons, chips
- `--shadow-stamp: 0 0 0 1px var(--color-stamp-200), 2px 2px 0 rgb(31 92 61 / 0.35)` — verification stamp badge
- `--shadow-pop: 4px 4px 0 rgb(43 33 24 / 0.9)` — dropdowns, toasts, modals

On press (buttons/chips): translate(2px, 2px) + shadow collapses to 0–1px (the element "stamps down").

## 5. Tailwind v4 consumption spec

Drop-in `@theme` block for Maryam (`styles/tokens.css`, imported first):

```css
@theme {
  /* Paper surfaces */
  --color-cream-50: #FBF7EE; --color-cream-100: #F5EEDF; --color-cream-200: #EFE6D2;
  --color-kraft-100: #EADFC9; --color-kraft-200: #DCCFAF; --color-kraft-300: #C9B98F;

  /* Brand — butcher red */
  --color-brand-50: #FBEDE9; --color-brand-100: #F6D9D1; --color-brand-300: #E07B63;
  --color-brand-500: #C6381F; --color-brand-600: #A82D17; --color-brand-700: #8A2411;
  --color-brand-900: #5C180B;

  /* Verification — stamp green */
  --color-stamp-50: #EDF3EE; --color-stamp-100: #DCE8DF; --color-stamp-200: #C2D6C8;
  --color-stamp-500: #1F5C3D; --color-stamp-600: #17492F; --color-stamp-700: #113A25;

  /* Warm ink neutrals */
  --color-ink-0: #FFFDF6; --color-ink-100: #F1EAD9; --color-ink-200: #DCD2BE;
  --color-ink-300: #B8AB90; --color-ink-400: #8A7D63; --color-ink-500: #6B5F4B;
  --color-ink-700: #453A2B; --color-ink-900: #2B2118;

  /* Errors — reserved */
  --color-danger-50: #FBEFEF; --color-danger-100: #F3D8D8;
  --color-danger-500: #B3261E; --color-danger-600: #921D16;

  /* Warning — informational only */
  --color-warning-50: #FBF3E2; --color-warning-100: #F3E5C4; --color-warning-700: #8A6114;

  --color-focus: #C6381F;

  /* Type */
  --font-sans: "Space Grotesk", ui-sans-serif, system-ui, sans-serif;
  --font-display: "Archivo", ui-sans-serif, system-ui, sans-serif;
  --text-display: 2.5rem; --text-display--line-height: 2.75rem; --text-display--font-weight: 800; --text-display--font-family: var(--font-display);
  --text-title: 1.5rem; --text-title--line-height: 1.875rem; --text-title--font-weight: 700; --text-title--font-family: var(--font-display);
  --text-heading: 1.125rem; --text-heading--line-height: 1.5rem; --text-heading--font-weight: 700; --text-heading--font-family: var(--font-display);
  --text-label: 0.875rem; --text-label--line-height: 1.125rem; --text-label--font-weight: 600; --text-label--font-family: var(--font-display);
  --text-body: 0.9375rem; --text-body--line-height: 1.5rem;
  --text-small: 0.8125rem; --text-small--line-height: 1.125rem;

  /* Space / radius / shadow */
  --spacing: 0.25rem;
  --radius-sm: 4px; --radius-md: 6px; --radius-lg: 10px;
  --shadow-card: 3px 3px 0 rgb(43 33 24 / 0.9);
  --shadow-chip: 2px 2px 0 rgb(43 33 24 / 0.9);
  --shadow-stamp: 0 0 0 1px var(--color-stamp-200), 2px 2px 0 rgb(31 92 61 / 0.35);
  --shadow-pop: 4px 4px 0 rgb(43 33 24 / 0.9);
}
```

Load fonts via `next/font/google` (Archivo weights 600/700/800; Space Grotesk 400/500/700), `display: swap`.

Usage: `bg-cream-50 text-ink-700 shadow-card`, primary button `bg-brand-500 text-cream-50 shadow-chip`, secondary "dark chip" `bg-ink-900 text-cream-50 shadow-chip`. Never hardcode hex values.

## 6. Component color bindings (summary — details in trust-components.md / screen specs)

- Primary button: brand-500 bg, cream-50 text, ink-900 hard shadow.
- Secondary chip/button: ink-900 bg, cream-50 text.
- Tertiary/ghost: transparent bg, ink-700 text, kraft-200 border.
- Verified: stamp badge (stamp-50 bg, stamp-200 border, stamp-700 text/icon, dashed seal edge, shadow-stamp). On photos: solid stamp-500, cream text.
- Unverified: ink-100 bg, ink-300 border, ink-500 text, outlined circle icon. NEVER red.
- Errors: danger tokens + alert triangle, always.
- Links: brand-500.

## Accessibility baseline

- Contrast: ink-700 on cream-50 = 9.2:1 ✓; stamp-700 on stamp-50 ≥ 4.5:1 ✓; cream-50 on brand-500 = 4.6:1 ✓; cream-50 on ink-900 = 13:1 ✓; ink-500 on cream-50 ≥ 4.5:1 ✓. (Verify final values at implementation with a contrast checker.)
- Focus ring 2px brand-500 offset 2px everywhere; ink-900 ring on brand-red surfaces.
- Hit areas ≥ 44×44px; errors announced via `aria-describedby` + `role="alert"`.
