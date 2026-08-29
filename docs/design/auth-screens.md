# Auth Screen Specs — Sign Up (sc-39) & Log In (sc-40)

Consumer: t_514f91af. Tokens: `tokens.md`. Radix/shadcn primitives where applicable (Form = react-hook-form + Radix; Button, Input, Label).

---

## Shared screen frame

- **Layout:** centered card on `neutral-50` page. Card: white, `radius-lg`, `shadow-card`, max-width 400px, padding `space-8` (32px). Mobile: card becomes full-width, padding `space-5`, no visible card edge (page = card), header above.
- **Header:** `text-title` heading ("Create your account" / "Welcome back"), one-line `text-body neutral-500` subcopy.
- **Brand mark:** small wordmark/lockup top of card (`space-4` below title area).
- **Footer cross-link:** Sign Up card → "Already have an account? **Log in**"; Log In card → "New here? **Sign up**". Link = brand-500.
- **Trust line** (Sign Up only, `text-small neutral-500`, centered below card): "By signing up you agree to our Terms and Privacy Policy." (links styled brand-500).

## Form field spec (both screens)

- Label: `text-body` weight 500, `neutral-700`, above input. Inputs: height 44px, `radius-md`, border `neutral-300`, focus border brand-500 + 2px ring (offset 0).
- Gap between fields: `space-4`. Label→input gap: `space-2`.
- Error display: below input, `text-small` `danger-600`, preceded by alert icon (14px, `aria-hidden`); input border → `danger-500`; `aria-invalid=true`, message linked via `aria-describedby`; container `role="alert"`.
- Helper text (idle): `text-small neutral-500` (e.g. password rules) — replaced by error when invalid.

---

## 1. Sign Up (sc-39)

**Fields:** Full name (text) · Email (type=email, autocomplete=email) · Password (autocomplete=new-password, show/hide toggle button inside input, right-aligned, 44px hit area) · [primary button] "Create account" (full width, brand-500, height 44px).

**States**

| State | Behavior |
|---|---|
| Default | Button enabled but performs client-side validation on submit; inline helper text under password: "At least 8 characters." |
| Field error | Per-field message (see error treatments). Focus moves to first invalid field. Summary not used (fields ≤ 3). |
| Submitting | Button → disabled, label "Creating account…" + inline spinner (16px) replacing icon slot; all inputs disabled (`neutral-400` text); form does not accept Enter resubmission. |
| Success | Navigate to post-signup destination (email verification notice per backend flow — **unresolved, see Open Questions**). |
| Server/network error | Banner at top of card: `danger-50` bg, `danger-100` border-left 3px, `danger-600` text, alert icon: "Something went wrong. Please try again." Banner gets `role="alert"` and focus. |

**Error message treatments (the three required paths)**

1. **Email not unique:** field error under Email — "An account with this email already exists. Try logging in instead." with the word "logging in" as a link to the Log In screen. (Do NOT say "email taken" cold — always offer the next step.)
2. **Weak password:** field error under Password — "Password is too weak. Use at least 8 characters." If the backend returns a specific rule failure (e.g. needs a number), surface its message verbatim, never a generic "invalid password".
3. **Validation errors generally:** single message per field, plain language, no error codes shown to users.

**Do/Don't**
- DO keep all three errors as inline field errors (not a banner) — the user needs to know WHICH field.
- DON'T clear the user's input on error. Password MAY be cleared only after a successful submit→error of type "bad credentials" (log in screen only).
- DON'T reveal whether an email exists during typing — only on submit (enumeration caution; confirm with Omar).

## 2. Log In (sc-40)

**Fields:** Email (autocomplete=email) · Password (autocomplete=current-password, show/hide toggle) · "Forgot password?" link right-aligned on the password label row (brand-500, `text-small`) · [primary button] "Log in" full width.

**States**

| State | Behavior |
|---|---|
| Default | As Sign Up, minus password helper. |
| Submitting | Button "Logging in…" + spinner, inputs disabled. |
| Success | Navigate to post-login destination (search/home). |
| Server/network error | Same top-of-card banner pattern as Sign Up. |

**Error paths**

1. **Bad credentials (email or password wrong):** ONE combined banner-style message — to avoid account enumeration, do not reveal which field failed. Render as an inline alert directly above the button: `danger-50` surface, alert icon, text: "Incorrect email or password." Do NOT clear the email field; DO clear the password field and focus it.
2. **Empty/invalid format:** inline field errors — Email: "Enter a valid email address." Password: "Enter your password."
3. **Rate-locked / too many attempts (if backend enforces):** inline alert above button: "Too many attempts. Try again in a few minutes." (**Open question** — confirm rate limiting exists in sc-40 backend scope; if not, omit.)

**Do/Don't**
- DO use the same alert anatomy everywhere; users learn the pattern.
- DON'T distinguish "no such user" vs "wrong password" in any UI copy, timing, or response shape (security requirement — align with Omar).
- DON'T add social/OAuth buttons — not in MVP scope; the card stays minimal (most interfaces have too many buttons).

## 3. Accessibility & responsive checklist

- Every input has a programmatically associated `<label>`; errors via `aria-describedby`; alert containers `role="alert"` (announced once).
- Submit is the form's `type="submit"`; Enter submits from any field.
- Show/hide password toggle: `aria-label="Show password"/"Hide password"`, `aria-pressed`.
- Mobile (≤480px): full-bleed layout, 16px minimum font in inputs (prevents iOS zoom), button reachable without scroll where possible.
- Focus order: fields top→bottom → forgot-password link → submit → cross-link footer.
- Respect `prefers-reduced-motion`: disable button spinner animation substitution (static "…" suffices).

## 4. Open questions (escalate, do not guess)

1. **Post-signup destination:** email-verification interstitial vs straight to app? Depends on sc-39 backend flow. Unresolved.
2. **Password rules** beyond min-8: none ratified yet. Unresolved.
3. **Rate limiting** on login (affects copy above). Unresolved.
4. **Enumeration caution** on signup error — current spec names the existing account; security review (Omar) may require the same combined-message treatment as login. Flagged for review.
