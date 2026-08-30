import { api, ApiError, setAccessToken, type AuthResponse } from "@/lib/api/client";

/**
 * Session store for the sc-133 cookie contract.
 *
 * Threading model:
 *  - The **access token lives in memory only** — it is never written to
 *    localStorage or any other JS-readable persistence.
 *  - The **refresh token never exists in JS at all**. It is delivered by the
 *    backend as an HttpOnly; Secure; SameSite=Lax cookie on /v1/auth/* and is
 *    presented transparently by `/v1/auth/refresh` and `/v1/auth/logout` (both
 *    send no request body).
 *  - Only a **non-secret identity hint** (accountId, role, email) is persisted
 *    in localStorage. It is display metadata, not a credential; it lets the UI
 *    render "Signed in as …" and decide to re-materialize an access token on
 *    reload without ever touching the refresh cookie.
 *
 * Lifecycle:
 *  - `signIn` stores the access token in memory, persists the identity hint,
 *    and schedules a silent refresh just before the access token expires.
 *  - `refreshAccessToken` rotates via the cookie; on failure with 401/400 the
 *    session is cleared (expired/rotated refresh cookie).
 *  - `signOut` calls POST /v1/auth/logout (cookie-revocation) and clears local
 *    state regardless of the network result.
 *  - `restore` re-materializes an access token on boot when an identity hint
 *    exists (the hint is why we know a session plausibly exists; the refresh
 *    cookie carries the actual continuation).
 */

export type Session = {
  accessToken: string;
  tokenType: string;
  expiresAt: number; // epoch ms — derived from expiresIn at issue/refresh time
  accountId: string;
  role: string;
  email: string;
};

type IdentityHint = Pick<Session, "accountId" | "role" | "email">;

export type SessionView = {
  session: Session | null;
  /** True while a persisted identity is being re-materialized into an access token. */
  restoring: boolean;
};

const IDENTITY_KEY = "hff.session.v1";
const REFRESH_LEAD_MS = 5_000; // rotate the access token 5s before it expires

type Listener = () => void;
const listeners = new Set<Listener>();

// --- Memory-only access-token state (never persisted).
let accessToken: string | null = null;
let tokenType = "Bearer";
let expiresAt = 0;

// --- Identity hint (persisted via localStorage, non-secret).
let identity: IdentityHint | null = null;
let restoring = false;

// --- Refresh scheduling / single-flight guard.
let refreshTimer: ReturnType<typeof setTimeout> | null = null;
let refreshInFlight: Promise<void> | null = null;

// Stable snapshot cache so getSnapshot returns the same reference until state changes.
let cachedView: SessionView = { session: null, restoring: false };
let cacheValid = false;

function emit() {
  cacheValid = false;
  for (const listener of listeners) listener();
}

function now(): number {
  return Date.now();
}

function readIdentityHint(): IdentityHint | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(IDENTITY_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as IdentityHint;
    if (!parsed.accountId || !parsed.role) return null;
    return parsed;
  } catch {
    return null;
  }
}

function writeIdentityHint(hint: IdentityHint | null) {
  if (typeof window === "undefined") return;
  try {
    if (hint) window.localStorage.setItem(IDENTITY_KEY, JSON.stringify(hint));
    else window.localStorage.removeItem(IDENTITY_KEY);
  } catch {
    // Storage unavailable (private mode / quota) — session is memory-only anyway.
  }
}

function buildSession(): Session | null {
  if (!accessToken || !identity) return null;
  return {
    accessToken,
    tokenType,
    expiresAt,
    accountId: identity.accountId,
    role: identity.role,
    email: identity.email,
  };
}

export function getSnapshot(): SessionView {
  if (cacheValid) return cachedView;
  cachedView = { session: buildSession(), restoring };
  cacheValid = true;
  return cachedView;
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function clearRefreshTimer() {
  if (refreshTimer) {
    clearTimeout(refreshTimer);
    refreshTimer = null;
  }
}

/**
 * Apply a refreshed (or freshly issued) auth payload. `email` may come from an
 * existing identity hint because refresh responses do not carry the email.
 */
function applyAuth(auth: AuthResponse, emailFromHint: string | undefined) {
  accessToken = auth.accessToken;
  setAccessToken(auth.accessToken);
  tokenType = auth.tokenType;
  expiresAt = now() + auth.expiresIn * 1000;
  const email = emailFromHint || identity?.email || "";
  identity = { accountId: auth.accountId, role: auth.role, email };
  writeIdentityHint(identity);
  restoring = false;
  scheduleRefresh();
  emit();
}

/** Clear all local session state without calling the server. */
function clearSessionLocally() {
  clearRefreshTimer();
  accessToken = null;
  setAccessToken(null);
  tokenType = "Bearer";
  expiresAt = 0;
  identity = null;
  restoring = false;
  writeIdentityHint(null);
  emit();
}

function scheduleRefresh() {
  clearRefreshTimer();
  const delay = Math.max(0, expiresAt - now() - REFRESH_LEAD_MS);
  // Guard: extremely long-lived values fall back to the largest safe timeout.
  refreshTimer = setTimeout(() => void refreshAccessToken(), Math.min(delay, 2_147_483_647));
}

/** Silent rotation: presents the refresh cookie; never a request body. */
export async function refreshAccessToken(): Promise<void> {
  if (refreshInFlight) return refreshInFlight;
  if (!identity) return; // nothing to restore
  refreshInFlight = (async () => {
    try {
      const auth = await api.refresh();
      applyAuth(auth, identity?.email);
    } catch (error) {
      if (error instanceof ApiError && (error.status === 401 || error.status === 400)) {
        // Expired / rotated / missing refresh cookie → session is over.
        clearSessionLocally();
      }
      // Network errors leave the current (memory-only) state untouched.
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export function signIn(auth: AuthResponse, email: string): void {
  accessToken = auth.accessToken;
  setAccessToken(auth.accessToken);
  tokenType = auth.tokenType;
  expiresAt = now() + auth.expiresIn * 1000;
  identity = { accountId: auth.accountId, role: auth.role, email };
  writeIdentityHint(identity);
  restoring = false;
  scheduleRefresh();
  emit();
}

/**
 * Sign out: revoke the refresh cookie server-side, then clear local state even
 * if the network call fails (local sign-out must never be blocked by logout).
 */
export function signOut(): void {
  clearSessionLocally();
  api.logout().catch(() => {
    // Best-effort server-side revocation. Local state is already cleared.
  });
}

/**
 * Called once on app boot. If a persisted identity hint exists, re-materialize
 * the access token via the refresh cookie; otherwise the user is anonymous.
 */
export function restore(): void {
  if (typeof window === "undefined") return;
  const hint = readIdentityHint();
  if (!hint) return;
  identity = hint;
  restoring = true;
  emit();
  void refreshAccessToken();
}