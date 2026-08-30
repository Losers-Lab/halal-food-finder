import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError, setAccessToken } from "@/lib/api/client";

function mockFetchResponse(body: unknown, status = 200) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(body === undefined ? "" : JSON.stringify(body)),
  });
}

/** Return a fixed raw response body — e.g. a non-JSON HTML/proxy error. */
function mockFetchRawText(rawBody: string, status: number) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(rawBody),
  });
}

describe("api client", () => {
  beforeEach(() => {
    // Ensure a clean access-token slate so header assertions are deterministic.
    setAccessToken(null);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    setAccessToken(null);
  });

  it("signup returns the created account on 201", async () => {
    vi.stubGlobal("fetch", mockFetchResponse({ id: "u-1", email: "a@b.co", role: "USER" }, 201));
    const result = await api.signup({ email: "a@b.co", password: "password1" });
    expect(result.email).toBe("a@b.co");
    expect(result.role).toBe("USER");
    expect(fetch).toHaveBeenCalledWith(
      "/v1/auth/signup",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("maps 409 to email_already_exists ApiError", async () => {
    vi.stubGlobal("fetch", mockFetchResponse({ code: "email_already_exists" }, 409));
    await expect(api.signup({ email: "a@b.co", password: "password1" })).rejects.toMatchObject({
      status: 409,
      code: "email_already_exists",
    });
  });

  it("maps 422 to weak_password and carries the backend message", async () => {
    vi.stubGlobal(
      "fetch",
      mockFetchResponse({ code: "weak_password", message: "Password must be at least 8 characters." }, 422),
    );
    await expect(api.signup({ email: "a@b.co", password: "short" })).rejects.toMatchObject({
      status: 422,
      code: "weak_password",
      detail: "Password must be at least 8 characters.",
    });
  });

  it("maps 401 to invalid_credentials (generic, no enumeration)", async () => {
    vi.stubGlobal("fetch", mockFetchResponse({ code: "invalid_credentials" }, 401));
    await expect(api.login({ email: "a@b.co", password: "wrong" })).rejects.toMatchObject({
      status: 401,
      code: "invalid_credentials",
    });
  });

  it("login returns the access token (sc-133: no refreshToken in the JSON body)", async () => {
    const body = {
      accessToken: "at",
      tokenType: "Bearer",
      expiresIn: 900,
      accountId: "u-1",
      role: "USER",
    };
    vi.stubGlobal("fetch", mockFetchResponse(body, 200));
    const result = await api.login({ email: "a@b.co", password: "password1" });
    expect(result.accessToken).toBe("at");
    // The refresh token must never appear in the JSON response.
    expect(result).not.toHaveProperty("refreshToken");
  });

  it("refresh sends no body and presents the cookie (credentials include)", async () => {
    const body = {
      accessToken: "at2",
      tokenType: "Bearer",
      expiresIn: 900,
      accountId: "u-1",
      role: "USER",
    };
    vi.stubGlobal("fetch", mockFetchResponse(body, 200));
    const result = await api.refresh();
    expect(result.accessToken).toBe("at2");
    expect(result).not.toHaveProperty("refreshToken");
    expect(fetch).toHaveBeenCalledWith(
      "/v1/auth/refresh",
      expect.objectContaining({
        method: "POST",
        credentials: "include",
        // No request body — the refresh cookie is presented by the browser.
        body: undefined,
      }),
    );
  });

  it("refresh never attaches the bearer even when an access token is set (sc-138)", async () => {
    // Regression guard: an expired access token must not 401 the refresh call
    // before the (valid) refresh cookie is read by the controller.
    setAccessToken("stale-at");
    vi.stubGlobal("fetch", mockFetchResponse({ code: "invalid_credentials" }, 401));
    await api.refresh().catch(() => undefined);
    const [, options] = vi.mocked(fetch).mock.calls[0] as [string, RequestInit];
    expect(options.headers).not.toHaveProperty("Authorization");
  });

  it("maps a 401 refresh response to an ApiError (expired/rotated refresh cookie)", async () => {
    vi.stubGlobal("fetch", mockFetchResponse({ code: "invalid_credentials" }, 401));
    await expect(api.refresh()).rejects.toMatchObject({
      status: 401,
      code: "invalid_credentials",
    });
  });

  it("logout posts no body with credentials include and resolves on 204", async () => {
    vi.stubGlobal("fetch", mockFetchResponse(undefined, 204));
    await expect(api.logout()).resolves.toBeUndefined();
    expect(fetch).toHaveBeenCalledWith(
      "/v1/auth/logout",
      expect.objectContaining({ method: "POST", credentials: "include", body: undefined }),
    );
  });

  it("logout never attaches the bearer even when an access token is set (sc-138)", async () => {
    // Logout is cookie-authenticated; it must rely on the refresh cookie alone.
    setAccessToken("at-1");
    vi.stubGlobal("fetch", mockFetchResponse(undefined, 204));
    await api.logout();
    const [, options] = vi.mocked(fetch).mock.calls[0] as [string, RequestInit];
    expect(options.headers).not.toHaveProperty("Authorization");
  });

  it("surfaces a network failure as a network_error (never a fabricated response)", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    await expect(api.login({ email: "a@b.co", password: "password1" })).rejects.toThrow(
      "network_error",
    );
  });

  it("throws a decorated ApiError as a real Error subclass", async () => {
    vi.stubGlobal("fetch", mockFetchResponse({ code: "invalid_credentials" }, 401));
    await expect(api.login({ email: "a@b.co", password: "x" })).rejects.toBeInstanceOf(
      ApiError,
    );
  });

  // Gap 5 / Defect 5a — a non-JSON body (proxy 502 HTML, malformed JSON) must
  // never leak a raw SyntaxError; it should normalize to a decorated error.
  it("maps a non-JSON error body to a typed ApiError, never a bare SyntaxError", async () => {
    vi.stubGlobal("fetch", mockFetchRawText("<html>Bad Gateway</html>", 502));
    const err = await api.login({ email: "a@b.co", password: "password1" }).catch(
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(502);
    expect((err as ApiError).code).toBe("invalid_input");
  });

  it("normalizes a non-JSON 2xx body to a typed error, not a SyntaxError escaping", async () => {
    vi.stubGlobal("fetch", mockFetchRawText("not json at all", 200));
    const err = await api.login({ email: "a@b.co", password: "password1" }).catch(
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(Error);
    expect(err).not.toBeInstanceOf(SyntaxError);
  });

  it("createListing posts to /v1/listings and returns the created listing on 201 (sc-138)", async () => {
    const body = {
      id: "u-1",
      name: "Al-Amir Grill",
      address: "123 Main St",
      lat: 40.7,
      lng: -74.0,
      cuisine: "middle eastern",
      cuttingMethod: "HAND_CUT",
      ownerId: "acc-1",
      verificationStatus: "UNVERIFIED",
      createdAt: "2026-08-30T00:00:00Z",
    };
    vi.stubGlobal(
      "fetch",
      mockFetchResponse(body, 201),
    );
    const result = await api.createListing({
      name: "Al-Amir Grill",
      address: "123 Main St",
      lat: 40.7,
      lng: -74.0,
      cuisine: "Middle Eastern",
      cuttingMethod: "HAND_CUT",
    });
    expect(result.verificationStatus).toBe("UNVERIFIED");
    expect(result.id).toBe("u-1");
    expect(fetch).toHaveBeenCalledWith(
      "/v1/listings",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("maps a 400 invalid_input to an ApiError carrying the backend field message (sc-138)", async () => {
    vi.stubGlobal(
      "fetch",
      mockFetchResponse(
        { code: "invalid_input", message: "name is required; lat is required" },
        400,
      ),
    );
    await expect(
      api.createListing({
        name: "",
        address: "123 Main St",
        lat: 40.7,
        lng: -74.0,
        cuisine: "Middle Eastern",
        cuttingMethod: "UNSPECIFIED",
      }),
    ).rejects.toMatchObject({
      status: 400,
      code: "invalid_input",
      detail: "name is required; lat is required",
    });
  });

  it("maps a 404 owner_not_found (auth-tied) to an ApiError for the UI to surface (sc-138)", async () => {
    vi.stubGlobal(
      "fetch",
      mockFetchResponse({ code: "owner_not_found" }, 404),
    );
    await expect(
      api.createListing({
        name: "Al-Amir Grill",
        address: "123 Main St",
        lat: 40.7,
        lng: -74.0,
        cuisine: "Middle Eastern",
        cuttingMethod: "HAND_CUT",
      }),
    ).rejects.toMatchObject({ status: 404, code: "owner_not_found" });
  });

  it("maps a 401 to invalid_credentials for an unauthenticated listing submit (sc-138)", async () => {
    vi.stubGlobal(
      "fetch",
      mockFetchResponse({ code: "invalid_credentials" }, 401),
    );
    await expect(
      api.createListing({
        name: "Al-Amir Grill",
        address: "123 Main St",
        lat: 40.7,
        lng: -74.0,
        cuisine: "Middle Eastern",
        cuttingMethod: "HAND_CUT",
      }),
    ).rejects.toMatchObject({ status: 401, code: "invalid_credentials" });
  });

  it("attaches an Authorization header when an access token is set (sc-138)", async () => {
    setAccessToken("at-1");
    vi.stubGlobal(
      "fetch",
      mockFetchResponse(
        {
          id: "u-1",
          name: "Al-Amir Grill",
          address: "123 Main St",
          lat: 40.7,
          lng: -74.0,
          cuisine: "middle eastern",
          cuttingMethod: "HAND_CUT",
          ownerId: "acc-1",
          verificationStatus: "UNVERIFIED",
          createdAt: "2026-08-30T00:00:00Z",
        },
        201,
      ),
    );
    await api.createListing({
      name: "Al-Amir Grill",
      address: "123 Main St",
      lat: 40.7,
      lng: -74.0,
      cuisine: "Middle Eastern",
      cuttingMethod: "HAND_CUT",
    });
    expect(fetch).toHaveBeenCalledWith(
      "/v1/listings",
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: "Bearer at-1",
        }),
      }),
    );
  });

  it("sends no Authorization header when no access token is set (sc-138)", async () => {
    vi.stubGlobal(
      "fetch",
      mockFetchResponse(
        {
          id: "u-1",
          name: "Al-Amir Grill",
          address: "123 Main St",
          lat: 40.7,
          lng: -74.0,
          cuisine: "middle eastern",
          cuttingMethod: "HAND_CUT",
          ownerId: "acc-1",
          verificationStatus: "UNVERIFIED",
          createdAt: "2026-08-30T00:00:00Z",
        },
        201,
      ),
    );
    await api.createListing({
      name: "Al-Amir Grill",
      address: "123 Main St",
      lat: 40.7,
      lng: -74.0,
      cuisine: "Middle Eastern",
      cuttingMethod: "HAND_CUT",
    });
    const [, options] = vi.mocked(fetch).mock.calls[0] as [
      string,
      RequestInit,
    ];
    expect(options.headers).not.toHaveProperty("Authorization");
  });

  it("keeps a typed 401 on an authenticated call so sc-134 can prompt session-expired (sc-138)", async () => {
    setAccessToken("at-1");
    vi.stubGlobal(
      "fetch",
      mockFetchResponse({ code: "invalid_credentials" }, 401),
    );
    await expect(
      api.createListing({
        name: "Al-Amir Grill",
        address: "123 Main St",
        lat: 40.7,
        lng: -74.0,
        cuisine: "Middle Eastern",
        cuttingMethod: "HAND_CUT",
      }),
    ).rejects.toMatchObject({ status: 401, code: "invalid_credentials" });
    // The backend was reached with the bearer token even though it 401'd.
    expect(fetch).toHaveBeenCalledWith(
      "/v1/listings",
      expect.objectContaining({
        headers: expect.objectContaining({ Authorization: "Bearer at-1" }),
      }),
    );
  });
});