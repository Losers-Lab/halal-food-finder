import type {
  components,
  operations,
} from "./schema";

type SignupBody = operations["signup"]["requestBody"]["content"]["application/json"];
type LoginBody = operations["login"]["requestBody"]["content"]["application/json"];
export type CreateListingBody =
  operations["create"]["requestBody"]["content"]["application/json"];
export type SignupResponse = components["schemas"]["SignupResponse"];
export type AuthResponse = components["schemas"]["AuthResponse"];
export type ListingResponse = components["schemas"]["ListingResponse"];

// Same-origin by default: Next.js rewrites proxy /v1/* to the backend (see
// next.config.ts), so no CORS config is needed on the API. Override with
// NEXT_PUBLIC_API_BASE to point the client at an explicit backend origin.
const API_BASE = (process.env.NEXT_PUBLIC_API_BASE ?? "").replace(/\/$/, "");

/**
 * sc-133 cookie contract: the refresh token is delivered only as an HttpOnly
 * cookie scoped to the auth routes, never in a JSON body. Refresh and logout
 * therefore send NO request body — the browser presents the cookie itself.
 * `credentials: "include"` ensures the refresh cookie is sent same-origin.
 */
const COOKIE_REQUEST = { credentials: "include" as const };

/**
 * Access-token holder for authenticated API calls (sc-138). The session store
 * (`auth/session.ts`) feeds the current in-memory access token here on every
 * auth change; `request()` emits it as a Bearer Authorization header when
 * present. Kept in the client (not the session module) so the dependency stays
 * one-way (session → client) with no import cycle. Memory-only by contract —
 * never persisted, never written to storage.
 */
let accessToken: string | null = null;

/** Set the access token used for authenticated calls (pass null to clear it). */
export function setAccessToken(token: string | null): void {
  accessToken = token;
}

/** One of the machine-readable error codes the backend returns. */
export type ApiErrorCode =
  | "invalid_input"
  | "email_already_exists"
  | "weak_password"
  | "invalid_credentials"
  | "owner_not_found"
  | "not_found"
  | "internal_error";

/**
 * Typed error thrown for any non-2xx API response. `code` is the backend's
 * ErrorResponse.code, which drives which UI error treatment to render.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: ApiErrorCode;
  readonly detail?: string;

  constructor(status: number, code: string, detail?: string) {
    super(code);
    this.name = "ApiError";
    this.status = status;
    this.code = code as ApiErrorCode;
    this.detail = detail;
  }
}

async function request<T>(
  path: string,
  body: unknown,
  options: { credentials?: RequestCredentials; signal?: AbortSignal } = {},
): Promise<T> {
  let response: Response;
  try {
    const headers: Record<string, string> = {};
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (accessToken) headers.Authorization = `Bearer ${accessToken}`;
    response = await fetch(`${API_BASE}${path}`, {
      method: "POST",
      headers,
      credentials: options.credentials,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal: options.signal,
    });
  } catch (error) {
    // Network / CORS / aborted — never a server response.
    if (options.signal?.aborted) throw error;
    throw new Error("network_error");
  }

  const text = await response.text();

  // Defect 5a guard: a non-JSON body (a reverse-proxy 502 HTML page, malformed
  // or whitespace-padded JSON) must never leak a raw SyntaxError. Normalize it
  // to a typed error instead. An unexpected 2xx with an unparseable body is a
  // network-layer failure; an unparseable error body keeps the ApiError
  // contract so the UI can still render a typed, decorated error.
  let data: unknown = null;
  try {
    data = text ? (JSON.parse(text) as unknown) : null;
  } catch {
    if (response.ok) {
      throw new Error("network_error");
    }
    throw new ApiError(response.status, "invalid_input");
  }

  if (!response.ok) {
    const body = (data ?? null) as { code?: unknown; message?: unknown } | null;
    const code = typeof body?.code === "string" ? body.code : "invalid_input";
    const detail =
      typeof body?.message === "string" ? body.message : undefined;
    throw new ApiError(response.status, code, detail);
  }

  return (data ?? undefined) as T;
}

export const api = {
  /** POST /v1/auth/signup — create an account. */
  signup: (body: SignupBody, signal?: AbortSignal): Promise<SignupResponse> =>
    request("/v1/auth/signup", body, { signal }),

  /** POST /v1/auth/login — authenticate, returns the access token (refresh is a cookie). */
  login: (body: LoginBody, signal?: AbortSignal): Promise<AuthResponse> =>
    request("/v1/auth/login", body, { signal }),

  /** POST /v1/auth/refresh — rotate via the HttpOnly refresh cookie (no body). */
  refresh: (signal?: AbortSignal): Promise<AuthResponse> =>
    request("/v1/auth/refresh", undefined, { ...COOKIE_REQUEST, signal }),

  /** POST /v1/auth/logout — revoke the refresh cookie (no body). Resolves on 204. */
  logout: (signal?: AbortSignal): Promise<undefined> =>
    request("/v1/auth/logout", undefined, { ...COOKIE_REQUEST, signal }),

  /**
   * POST /v1/listings — add a restaurant listing (sc-138). Requires the
   * authenticated account (the access JWT is presented via the auth surface);
   * a listing is always created UNVERIFIED. The client supplies coordinates
   * directly — this endpoint does not geocode.
   */
  createListing: (
    body: CreateListingBody,
    signal?: AbortSignal,
  ): Promise<ListingResponse> => request("/v1/listings", body, { signal }),
};