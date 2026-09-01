The word is exactly "Unverified".
- DON'T place it adjacent to error text styling or shake/pulse animations.

**Colorblind check:** differentiation from Verified is (a) filled-positive vs outlined-neutral, (b) checkmark vs circle glyph, (c) label text. Passing all three means deuteranopia/protanopia/tritanopia users get identical information.

## 3. HandCutIndicator

**Anatomy:** `[icon] Hand-cut` — icon 16px + label. There is NO machine-cut
state (sc-42 founder ruling): hand-cut is an extra boolean a listing either
claims or not, and the indicator is shown ONLY when the listing is hand-cut.
When hand-cut is false/unknown, omit the component entirely.

> **sc-119 note (09-01):** the cutting method is a **boolean hand-cut** field
> (`isHandCut`) on the listing. The indicator renders only when the data is
> present: `isHandCut = true` shows the hand-cut chip; when `isHandCut` is
> `false`/`null` the component is omitted. The partial-halal scope surfaces
> via a separate disclosure (see HalalScopeIndicator) so the hand-cut chip
> never implies whole-restaurant halal-ness. Frontend re-render is a follow-up
> (Maryam).

| | Icon | Color | Glyph concept |
|---|---|---|---|
| Hand-cut | `scissors` (line icon) | `neutral-700` icon, `neutral-500` text on `neutral-0/50` | hands/scissors |

- Presentation: quiet chip, radius `sm`, padding 4px 8px, `text-small`. NOT a colored pill — hand-cut is information, not a judgment.
- Tooltip/`title`: "Zabiha method: animal slaughtered by hand".

**Don't:**
- DON'T render a distinct machine-cut state — it does not exist.
- DON'T add a second style for "unknown" — if not hand-cut, omit the component entirely (see Empty State rule).

## 4. Shared rules (all three components)