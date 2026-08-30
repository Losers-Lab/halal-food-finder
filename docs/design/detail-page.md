# Restaurant Detail Page Spec — Halal Food Finder ("Stamps & Search")

Status: implementation-ready · Matches approved sketch `sketches/007-detail-stamps` · Owner: Fatima
Tokens: `tokens.md` (v2). Trust components: `trust-components.md`. Route: `/restaurants/[slug]`.

## Primary user flow

Arrives from a search card (or deep link) → confirms at a glance this place is genuinely halal (stamp badge + certificate trust panel) → checks hours/cut method/location → acts (call / directions / website).

The detail page is where trust is PROVEN, not just claimed. The certificate trust panel is the page's centerpiece.

---

## 1. Page anatomy (desktop)

```
┌──────────────────────────────────────────────────────────────┐
│ header (shared with search-browse.md)                        │
├──────────────────────────────────────────────────────────────┤
│ ← Back to results                                            │
│ ┌──────────────────────────────────────┐                     │
│ │  hero photo 16:7 (rounded-lg)        │  [✓ Verified stamp] │ ← badge overlaid top-right of photo
│ └──────────────────────────────────────┘                     │
│ Al-Amir Grill                          [dark chip] Directions│
│ Middle Eastern · 1.2 mi · ★ 4.6 (89)   [dark chip] Call     │
│ [✂ Hand-cut]                                                 │
├───────────────────────────────────────┬──────────────────────┤
│ CERTIFICATE TRUST PANEL               │  Hours               │
│ (dashed stamp-200 border panel)       │  Location            │
│ ┌ stamp seal ┐ Reviewed by committee  │                      │
│  Certifier: HFSAA        └───────────┴──────────────────────┘
│  Last reviewed: Aug 12, 2026          │                      │
│  Expires: Jan 12, 2027                │                      │
│  [View certificate →]                 │                      │
├───────────────────────────────────────┴──────────────────────┤
│ footer (shared)                                              │
└──────────────────────────────────────────────────────────────┘
```

### 1.1 Header block
- "← Back to results": ghost link, ink-700, preserves search query (history.back() with `/search?q=` fallback).
- Hero photo: 16:7 on desktop, 16:9 mobile, `radius-lg`, kraft-200 border, shadow-card. Loading: kraft-100 block with subtle stamp-line illustration; broken image → same placeholder (never a broken-image icon).
- VerifiedBadge (on-photo variant: solid `stamp-500`, cream text, shadow-stamp) top-right over photo, only when verified. Position must not collide with photo credit (bottom-left, `text-small` cream on scrim).
- Below photo: name `text-display`-scaled-down (use 32/38 Archivo 800 ink-900) + meta row + CutMethodIndicator chip.
- Action buttons, right column on desktop / stacked full-width on mobile, in priority order: **Directions** (dark chip, primary-of-this-group), **Call** (dark chip, tel: link, only if phone exists), **Website** (ghost, external, only if exists). Max 3 actions — most interfaces have too many buttons. Each ≥44px tall.

### 1.2 Certificate trust panel (REQUIRED — the trust centerpiece)

A distinct panel, visually framed like a certificate:
- Container: `bg-ink-0`, border **1.5px dashed `stamp-200`**, `radius-lg`, `shadow-stamp`, padding `space-6`. Header row: stamp/seal icon (stamp-500, 24px) + `text-heading` "Halal verification" (ink-900).
- Fields (definition list, `text-body`):
  - **Certifier** — issuing body name (e.g. "HFSAA"), ink-700, weight 500.
  - **Last reviewed** — human date ("Aug 12, 2026"), never raw ISO.
  - **Expires** — human date; see expiry states below.
  - **[View certificate →]** — link, brand-500, `text-label`, opens the certificate image/PDF in a lightbox or new tab (`aria-label="View halal certificate for {name}"`).
- Copy anchor under the panel: `text-small` ink-500: "Reviewed by our verification committee — not self-reported." Links to /how-verification-works.

Expiry states (binding):
| State | Display |
|---|---|
| Valid (>60 days) | Expires date in ink-700. |
| Expires soon (≤60 days) | "Expires Jan 12, 2027 — review in progress" with warning-700 icon+text line (icon + text, never color alone). |
| Expired | Badge downgrades to UnverifiedTag everywhere; panel shows warning line: "Certification lapsed on {date}. We're following up with the restaurant." Never danger-red — a lapse is an informational state, not a user error. |
| Unknown certifier/expiry | Omit the field; if panel has no data at all, render the unverified empty-state instead (below). |

Unverified detail page: no certificate panel. Instead a quiet panel, `bg-ink-100`, solid `ink-300` border, outlined circle icon: "This listing hasn't been verified yet. Owners can claim it and submit certification for review." + ghost link "How verification works". NEVER red, NEVER warning triangles.

### 1.3 Sidebar: Hours & Location
- **Hours:** 7-row list, today's row bold ink-900 with a small "Open now / Closed" pill (pill = stamp-700 text on stamp-50 when open / ink-500 on ink-100 when closed — again icon+text: ●/○). Collapsible on mobile (`<details>`-style, today expanded by default).
- **Location:** full address (`text-body`), "Get directions" link (brand-500) → maps deep link. Static map image optional — founder dislikes map-first, but a small static map on detail is acceptable; default v1: address + directions link only (simpler, no map dependency).

---

## 2. States (whole page)

| State | Behavior |
|---|---|
| Loading | Full-page skeleton: photo block, title bar, meta bar, certificate panel skeleton with dashed border preserved (so layout doesn't shift). |
| Not found (404) | `bg-ink-0` panel, stamp illustration, "We couldn't find that restaurant." + dark chip "Browse all spots" → /search. Header/footer intact. |
| Error (fetch fail) | Same panel with danger alert line + Retry dark chip. |
| Gallery (if multiple photos) | v1: hero + thumbnail strip (64px squares, kraft-200 border; selected gets brand-500 border). Thumbnails are real buttons with aria-label "Photo N of M". |

## 3. Mobile (≤768px)

- Single column: photo → title/badge → meta → action buttons (full-width stacked, Directions first) → certificate panel → hours (collapsed except today) → address → footer.
- Certificate panel fields stack label-above-value; "View certificate" is a full-width row button (44px).
- Bottom tab bar present (see mobile-bottom-tab.md); page bottom padding = tab bar height + safe-area.

## 4. Accessibility

- Badge/tag text carries meaning; icons aria-hidden.
- Certificate fields as a `<dl>`; "View certificate" a real link (not a div).
- Today's-hours emphasis must be font-weight, not color alone.
- Focus order: back link → photo (no tabindex) → actions → certificate panel link → hours → address. All interactive: 2px brand-500 focus ring, never removed.
- Panel and its downgrade states announced via `aria-live="polite"` only on client-side transitions, not initial load.

## 5. Trust messaging summary

- The word "Verified" always appears with the stamp mark and, in the panel, the sentence "Reviewed by our verification committee — not self-reported."
- Never show danger red anywhere on this page except genuine action errors (e.g. failed "Report a problem" submission).
- v1 keeps actions to Directions/Call/Website; "Report a problem" and "Claim this listing" are follow-ups (founder to prioritize) — noted, not designed here.

## 6. Open questions

1. Certificate artifact: image vs PDF vs external URL — affects lightbox vs new tab. Ask Omar/Maryam what the data provides; design supports either.
2. "Expires soon" threshold (60 days) is my proposal — confirm with Adnan.
3. Claim/Report flows deferred to follow-up cards.
