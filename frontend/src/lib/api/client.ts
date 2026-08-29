import type {
  components,
  operations,
} from "./schema";

type SignupBody = operations["signup"]["requestBody"]["content"]["application/json"];
type LoginBody = operations["login"]["requestBody"]["content"]["application/json"];
type RefreshBody = operations["refresh"]["requestBody"]["content"]["application/json"];
export type SignupResponse = components["schemas"]["SignupResponse"];
export type AuthResponse = components["schemas"]["AuthResponse"];

// Same-origin by default: Next.js rewrites proxy /v1/* to the backend (see
// next.config.ts), so no CORS config is needed on the API. Override with
// NEXT_PUBLIC_API_BASE to point the client at an explicit backend origin.
const API_BASE = (process.env.NEXT_PUBLIC_API_BASE ?? "").replace(/\/$/, "");

/** One of the machine-readable error codes the backend returns. */
export type ApiErrorCode =
  | "invalid_input"
  | "email_already_exists"
  | "weak_password"
  | "invalid_credentials";

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
  signal?: AbortSignal,
): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
      signal,
    });
  } catch (error) {
    // Network / CORS / aborted — never a server response.
    if (signal?.aborted) throw error;
    throw new Error("network_error");
  }

  const text = await response.text();
  const data = text ? JSON.parse(text) : null;

  if (!response.ok) {
    const code =
      typeof data?.code === "string" ? data.code : "invalid_input";
    const detail = typeof data?.message === "string" ? data.message : undefined;
    throw new ApiError(response.status, code, detail);
  }

  return data as T;
}

export const api = {
  /** POST /v1/auth/signup — create an account. */
  signup: (body: SignupBody, signal?: AbortSignal): Promise<SignupResponse> =>
    request("/v1/auth/signup", body, signal),

  /** POST /v1/auth/login — authenticate, returns a token pair. */
  login: (body: LoginBody, signal?: AbortSignal): Promise<AuthResponse> =>
    request("/v1/auth/login", body, signal),

  /** POST /v1/auth/refresh — rotate a refresh token. */
  refresh: (body: RefreshBody, signal?: AbortSignal): Promise<AuthResponse> =>
    request("/v1/auth/refresh", body, signal),
};