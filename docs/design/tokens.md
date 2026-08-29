# Design Tokens — Halal Food Finder (M1 Foundation)

Status: handoff-ready · Owner: Fatima (design) · Consumer: t_a69abcf7 (frontend skeleton), t_514f91af (auth UI)

## Design principles

1. **Trust first.** The palette and components exist to make verification status legible and credible at a glance.
2. **Colorblind-safe.** Never rely on hue alone to convey meaning; pair every color signal with an icon or text label. Verified/unverified are distinguished by shape + icon + neutral-vs-positive treatment, not red/green alone.
3. **Few colors, consistent roles.** One brand hue, one positive hue (verification), one neutral scale, one semantic error hue used ONLY for genuine errors.
4. **Fresh, not cloned.** Values are chosen from first principles; Figma is reference-only.

## 1. Color tokens

Consumable as Tailwind v4 `@theme` variables (see §5). Scale naming: numeric lightness ramp, 500 = base.

### Brand (primary) — deep teal, calm and trustworthy

| Token | Hex | Role |
|---|---|---|
| --color-brand-50 | #eff8f8 | subtle backgrounds |
| --color-brand-100 | #d7ecec | hover fills |
| --color-brand-200 | #aed9da | borders on brand surfaces |
| --color-brand-300 | #7fc0c3 | accents |
| --color-brand-400 | #4a9fa4 | secondary buttons |
| --color-brand-500 | #2b7f86 | PRIMARY actions, links |
| --color-brand-600 | #22676d | primary hover |
| --color-brand-700 | #1c5257 | pressed |
| --color-brand-800 | #174246 | headings on light |
| --color-brand-900 | #122f32 | darkest brand |

### Positive / verified — green (paired with checkmark icon, never alone)

| Token | Hex | Role |
|---|---|---|
| --color-positive-50 | #edf7ef | badge background |
| --color-positive-100 | #d6eeda | badge hover |
| --color-positive-200 | #a9dcb4 | border |
| --color-positive-500 | #2e8b47 | icon fill, text on light bg |
| --color-positive-600 | #247038 | hover/pressed |
| --color-positive-700 | #1d5a2e | text on positive-50 for AA |

### Neutral — slate (all "unverified" and general UI)

| Token | Hex | Role |
|---|---|---|
| --color-neutral-0 | #ffffff | page background |
| --color-neutral-50 | #f7f8f9 | card/section background |
| --color-neutral-100 | #eef0f2 | subtle fill |
| --color-neutral-200 | #dfe3e8 | borders (default) |
| --color-neutral-300 | #c6cdd5 | stronger borders, dividers |
| --color-neutral-400 | #94a0ac | disabled text, icons |
| --color-neutral-500 | #64707d | secondary text |
| --color-neutral-700 | #3d4650 | primary text (body) |
| --color-neutral-900 | #1d2329 | headings, high-emphasis text |

### Error — reserved exclusively for genuine errors (never for "Unverified")

| Token | Hex | Role |
|---|---|---|
| --color-danger-50 | #fdf1f0 | error surface |
| --color-danger-100 | #f9dbd8 | error border tint |
| --color-danger-500 | #c0392b | error icon/text |
| --color-danger-600 | #a32f23 | error text (AA on 50) |
| --color-danger-700 | #8a2620 | pressed |

### Warning (informational only — e.g. "certification expires soon")

| Token | Hex | Role |
|---|---|---|
| --color-warning-50 | #fdf6ec | surface |
| --color-warning-500 | #b0731c | icon/text (AA on 50) |
| --color-warning-100 | #f5e6cd | border |

### Focus

- --color-focus: #2b7f86 (brand-500); focus ring = 2px solid, offset 2px. Never remove outlines.

### Colorblind safety rules (binding)

- DO pair green "Verified" with a checkmark glyph and the word "Verified".
- DO keep "Unverified" in neutral slate with a neutral (outlined) shape — differentiation is hue-independent.
- DO use icon + text for cut-method indicators (see trust-components spec).
- DON'T encode meaning in red vs green alone; ~8% of male users cannot distinguish them.
- DON'T use red for anything other than errors.

## 2. Typography

Font stack: `Inter, ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif`. (Inter is free, excellent numerals for ratings/distances, ships variable weights.)

| Token | Size / Line | Weight | Use |
|---|---|---|---|
| --text-display | 30/36 | 700 | Page titles (marketing/auth hero) |
| --text-title | 22/28 | 600 | Screen titles ("Create your account") |
| --text-heading | 17/24 | 600 | Card headings, restaurant names |
| --text-body | 15/24 | 400 | Body, form labels 500 |
| --text-small | 13/18 | 400 | Helper text, captions |
| --text-mono | 14/20 | 400 | cert IDs, technical strings (`ui-monospace, "SF Mono", Menlo`) |

Rules: body text = neutral-700; headings = neutral-900; links = brand-500 with underline on hover; minimum touch target 44px for interactive elements.

## 3. Spacing scale

Base 4px. Tokens: `--space-1:4px · 2:8px · 3:12px · 4:16px · 5:20px · 6:24px · 8:32px · 10:40px · 12:48px · 16:64px`.

Conventions: form field gaps = space-4; card padding = space-5 (mobile) / space-6 (desktop); section rhythm = space-12.

## 4. Radius & elevation

| Token | Value | Use |
|---|---|---|
| --radius-sm | 6px | tags, inputs (small) |
| --radius-md | 8px | inputs, buttons |
| --radius-lg | 12px | cards, modals |
| --radius-full | 9999px | badges/pills, avatars |

Shadows (subtle; trust comes from clarity, not depth):
- `--shadow-card: 0 1px 2px rgb(29 35 41 / 0.06), 0 1px 3px rgb(29 35 41 / 0.08)`
- `--shadow-pop: 0 4px 12px rgb(29 35 41 / 0.12)` (dropdowns, toasts)

## 5. Tailwind v4 consumption spec

Drop-in `@theme` block for Maryam (`styles/tokens.css`, imported first):

```css
@theme {
  /* Color */
  --color-brand-50:  #eff8f8;  --color-brand-100:  #d7ecec;
  --color-brand-200: #aed9da;  --color-brand-300:  #7fc0c3;
  --color-brand-400: #4a9fa4;  --color-brand-500:  #2b7f86;
  --color-brand-600: #22676d;  --color-brand-700:  #1c5257;
  --color-brand-800: #174246;  --color-brand-900:  #122f32;

  --color-positive-50:  #edf7ef; --color-positive-100: #d6eeda;
  --color-positive-200: #a9dcb4; --color-positive-500: #2e8b47;
  --color-positive-600: #247038; --color-positive-700: #1d5a2e;

  --color-neutral-0:  #ffffff; --color-neutral-50:  #f7f8f9;
  --color-neutral-100: #eef0f2; --color-neutral-200: #dfe3e8;
  --color-neutral-300: #c6cdd5; --color-neutral-400: #94a0ac;
  --color-neutral-500: #64707d; --color-neutral-700: #3d4650;
  --color-neutral-900: #1d2329;

  --color-danger-50:  #fdf1f0; --color-danger-100: #f9dbd8;
  --color-danger-500: #c0392b; --color-danger-600: #a32f23;
  --color-danger-700: #8a2620;

  --color-warning-50: #fdf6ec; --color-warning-100: #f5e6cd;
  --color-warning-500: #b0731c;

  /* Type */
  --font-sans: Inter, ui-sans-serif, system-ui, -apple-system, "Segoe UI", sans-serif;
  --text-display: 1.875rem; --text-display--line-height: 2.25rem; --text-display--font-weight: 700;
  --text-title: 1.375rem;  --text-title--line-height: 1.75rem;  --text-title--font-weight: 600;
  --text-heading: 1.0625rem; --text-heading--line-height: 1.5rem; --text-heading--font-weight: 600;
  --text-body: 0.9375rem;  --text-body--line-height: 1.5rem;
  --text-small: 0.8125rem; --text-small--line-height: 1.125rem;

  /* Space / radius / shadow */
  --spacing: 0.25rem; /* default 4px base already; explicit for clarity */
  --radius-sm: 6px; --radius-md: 8px; --radius-lg: 12px;
  --shadow-card: 0 1px 2px rgb(29 35 41 / 0.06), 0 1px 3px rgb(29 35 41 / 0.08);
  --shadow-pop: 0 4px 12px rgb(29 35 41 / 0.12);
}
```

Usage: `bg-brand-500 text-white rounded-md shadow-card` etc. Do not hardcode hex values in components — always consume tokens.

## Accessibility baseline

- Text contrast ≥ 4.5:1 (body) / 3:1 (large text ≥ 18.66px bold or 24px). All listed text tokens on their stated backgrounds meet this.
- Focus ring: 2px brand-500, offset 2px, on all interactive elements.
- Hit areas ≥ 44×44px; form errors announced via `aria-describedby` + `role="alert"`.
