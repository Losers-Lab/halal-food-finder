import { afterEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/lib/api/client";

function mockFetchResponse(body: unknown, status = 200) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    text: () => Promise.resolve(body === undefined ? "" : JSON.stringify(body)),
  });
}

describe("api client", () => {
  afterEach(() => {
    vi.restoreAllMocks();
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
});