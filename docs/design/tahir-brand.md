# Tahir's List — Brand Direction (mascot & logo)

Status: EXPLORATION → **DIRECTION A PICKED + LOGO DECIDED (founder 2026-08-30)** · Owner: Fatima (design) · Branch: `design/sc-159-tahir-brand`
Companion artifacts: `sketches/010-tahir-brand/` (exploration board: `index.html` + `preview.png`), `brand/tahir/` (canonical art + character bible).
Supersedes: placeholder "HalalMarket" wordmark usage in `docs/design/tokens.md` mockups — the brand name is **Tahir's List** (tahirslist.com), founder-ratified 2026-08-30.

## 1. The character (locked)

Tahir (طاهر — "pure") is the personification of the product. Canonical art and the full character bible live at `brand/tahir/CHARACTER.md` (v2 "kurta Tahir"); **all future Tahir imagery is generated image-to-image from `brand/tahir/tahir-canonical.jpg`** — the face never changes.

Non-negotiables (apply in every direction):

- Flat-vector Duolingo-style: bold simple shapes, no outlines, big head, oval eyes, dot eyebrows.
- Medium-brown skin, black swoosh hair, plain white topi, round black glasses, simple smile, **no beard**.
- Plain green band-collar kurta — **no badge, no emblem, no logo on clothing** (explicit founder correction; trust badges live in product UI, never on the character).
- Dark teal pants. Never depicted with haram items; unverified places are treated kindly. (v1 mustard-sweater art is archived under `brand/tahir/` and not for reuse.)

## 2. Three directions

The face is locked; directions differ in **role, costume, and logo behavior** — not in redrawn character design. Board with visuals: `sketches/010-tahir-brand/`.

### Direction A — The Foodie Friend (current canonical pose)

- **Personality:** your food-obsessed peer; fork raised in a toast, hand on hip. Not a chef, not an inspector — the friend who already knows the spot.
- **Visual style:** no costume, no props beyond the fork. Purest read of the character.
- **Logo mark:** head-only crop (topi + glasses) in a rounded square → favicon/app icon; "peekaboo over the apostrophe" for hero scale.
- **Extensibility:** highest. Any future context (searching, checking, celebrating) is a pose change on the same clean base. "Tahir's Favorites" needs no wardrobe system.
- **Trade-off:** carries no verification story on his own — the VerifiedSeal in UI completes it.

### Direction B — The Host

- **Personality:** Tahir as host of the product, presenting the list like a waiter with the day's specials.
- **Visual style:** small butcher-red (#C6381F) neckerchief over the green band-collar kurta + wooden serving board with a simple plate. Neckerchief is a one-color, infinitely scalable brand cue tying outfit to brand red.
- **Logo mark:** same head crop works for icons; full-body "presenting" pose is the hero/announcement asset.
- **Extensibility:** strong for sponsor/featured slots ("Tahir presents…"), email art, empty states with food illustration.
- **Trade-off:** busiest at tiny sizes (mitigate: head-only crop below 48px); props must stay simple in all future poses.

### Direction C — The Scout

- **Personality:** Tahir actively making the list — clipboard checklist, thumbs-up, he checks and he ticks.
- **Visual style:** kraft clipboard with checklist + scalloped butcher-red seal behind him, echoing the stamp language of the trust system.
- **Logo mark:** seal-crest lockup (character in seal above wordmark) — strong as social avatar / splash / badge.
- **Extensibility:** the seal backdrop is a reusable container ("Tahir's Favorites" seal, milestone badges).
- **⚠ Trust-language caveat (binding):** in production use, the clipboard checkmark must be **stamp green (#1F5C3D)** or ink — not red — and the red seal skirt must never frame a *verified* claim, because verification color is reserved for stamp green and the colorblind-safe rule pairs color with glyph+word. Red here reads "brand energy", not "verified".

### Recommendation

**A as the primary mascot and app icon, with B's neckerchief adopted as the everyday look** (one-color brand cue, no prop burden), and **C's seal reserved as a container motif** for future "Tahir's Favorites"/milestone badges. This keeps the icon clean, gives daily art a brand tie-in, and banks C's strongest idea without its trust-language risk. Founder decision overrides this.

## 3. Logo system (independent of direction choice)

Wordmark: **Archivo Black (900), tight tracking (-0.03em), ink on paper; the apostrophe is butcher red (#C6381F)** — `Tahir's List`, never all-caps (the apostrophe play needs lowercase height).

| Lockup | Composition | Use |
|---|---|---|
| **Icon row (C2 — PRIMARY, all contexts)** | Rounded-square head icon (hard 4px offset ink shadow) + wordmark + tagline "find halal food"; apostrophe brand red | Site header, footer, email, marketing — everywhere |
| Wordmark-only | Type + red apostrophe | Legal, small spaces, print |
| Inversion | Cream wordmark on ink, apostrophe in soft red #E8937D (AA) | Dark bands, future dark mode |
| ~~Peekaboo / hang-off~~ | ~~Tahir interacting with the wordmark~~ | **REJECTED by founder 2026-08-30** — shelve |

Favicon/app icon: head-only crop (topi + glasses), tested to 16px (topi + glasses remain legible).

## 4. Adaptation of the "Stamps & Search" tokens

- **No token changes.** The mascot palette is drawn entirely from the existing ramp: green kurta ≈ the verified/stamp-adjacent green family (character art only, never used as a UI status color — the UI green remains exclusive to the VerifiedBadge), teal pants ≈ desaturated stamp-adjacent neutral, kraft/cream backgrounds from --color-cream-*.
- Character backgrounds in-product are transparent or --color-cream-50/100; the canonical sky-blue is for brand illustration only.
- **Colorblind-safe rules unchanged:** red = brand/action, green = verified (glyph + word always), ink = neutral. The mascot's green kurta is character art, not a UI status signal — the verified green remains exclusive to the VerifiedBadge in the interface (founder-ratified).
- Voice examples already in `brand/tahir/CHARACTER.md` ("Tahir is checking the certificate… 🔍") apply to all mascot-fronted UI copy.

## 5. Resolved (founder decisions 2026-08-30) & remaining open items

**Resolved by founder:**
1. Direction: **A** (the Foodie Friend, current canonical pose) — no neckerchief adoption recorded; C2 lockup everywhere.
2. Logo system: **C2 (Icon row) is the primary lockup for ALL contexts**; "Peekaboo" hang-off variant **rejected**. Tagline finalized as **"find halal food"** (not "find pure halal food"). Apostrophe is red in all lockups including the icon row.
3. **Dark theme:** founder likes the ink/cream inversion — planned as a future optimization pass; light is the launch default. Tracked in `brand/tahir/BRAND-DECISIONS.md`.
4. Future mascot work (backlog, not now): Tahir loading animation (eating random food), expression/pose sheet, "Tahir's Favorites" sponsor slot.

**Still open:**
1. Food photography direction — mascot vs photography balance on the homepage hero.
2. Custom lettering for the wordmark (current treatment is type-only, no licensing issue).
3. Canonical pose set (searching / checking / celebrating) as the next mascot increment.
