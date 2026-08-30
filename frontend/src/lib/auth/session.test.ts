import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";

// Fresh module state per test so the memory-only store resets.
const LOAD = () => import("@/lib/auth/session");

vi.mock("@/lib/api/client", () => ({
  ApiError: class ApiError extends Error {
    status: number;
    code: string;
    constructor(status: number, code: string) {
      super(code);
      this.status = status;
      this.code = code;
    }
  },
  api: {
    refresh: vi.fn(),
    logout: vi.fn(),
  },
  setAccessToken: vi.fn(),
}));

import { api, setAccessToken } from "@/lib/api/client";

const auth = vi.mocked(api);
const authToken = vi.mocked(setAccessToken);

const AUTH_RESPONSE = {
  accessToken: "at-1",
  tokenType: "Bearer",
  expiresIn: 900, // 15 min
  accountId: "u-1",
  role: "USER",
};

describe("session store — sc-133 cookie lifecycle", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-08-29T12:00:00Z"));
    window.localStorage.clear();
    auth.refresh.mockReset();
    auth.logout.mockReset();
    authToken.mockReset();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.resetModules();
    window.localStorage.clear();
  });

  it("signIn keeps the access token memory-only and never writes a refresh token to storage", async () => {
    const { signIn, getSnapshot, subscribe } = await LOAD();
    const notify = vi.fn();
    subscribe(notify);

    signIn(AUTH_RESPONSE, "a@b.co");

    const { session } = getSnapshot();
    expect(session?.accessToken).toBe("at-1");
    expect(session?.email).toBe("a@b.co");
    expect(session?.expiresAt).toBe(Date.now() + 900_000);
    expect(notify).toHaveBeenCalled();

    // The persisted identity hint carries display metadata only — never a token.
    const stored = window.localStorage.getItem("hff.session.v1");
    expect(stored).toBeTruthy();
    expect(JSON.parse(stored!)).not.toHaveProperty("accessToken");
    expect(JSON.parse(stored!)).not.toHaveProperty("refreshToken");
    expect(JSON.parse(stored!)).toEqual({
      accountId: "u-1",
      role: "USER",
      email: "a@b.co",
    });
  });

  it("silently refreshes via the cookie just before access-token expiry", async () => {
    auth.refresh.mockResolvedValue({
      ...AUTH_RESPONSE,
      accessToken: "at-2",
      role: "ADMIN",
    });
    const { signIn, getSnapshot } = await LOAD();

    signIn(AUTH_RESPONSE, "a@b.co");
    expect(auth.refresh).not.toHaveBeenCalled();

    // Advance past the 5s lead before expiry (900s lifetime).
    await vi.advanceTimersByTimeAsync(900_000 - 5_000);
    expect(auth.refresh).toHaveBeenCalledTimes(1);

    // Access token + role rotated in memory; identity hint updated (no new storage key).
    const { session } = getSnapshot();
    expect(session?.accessToken).toBe("at-2");
    expect(session?.role).toBe("ADMIN");
    const stored = JSON.parse(window.localStorage.getItem("hff.session.v1")!);
    expect(stored).toEqual({ accountId: "u-1", role: "ADMIN", email: "a@b.co" });
    expect(stored).not.toHaveProperty("accessToken");
  });

  it("a 401 refresh (expired/rotated cookie) clears the session end-to-end", async () => {
    auth.refresh.mockRejectedValue(new ApiError(401, "invalid_credentials"));
    const { signIn, getSnapshot } = await LOAD();

    signIn(AUTH_RESPONSE, "a@b.co");
    await vi.advanceTimersByTimeAsync(900_000 - 5_000);

    const { session } = getSnapshot();
    expect(session).toBeNull();
    expect(window.localStorage.getItem("hff.session.v1")).toBeNull();
  });

  it("a 401 refresh during boot restore clears a stale persisted session", async () => {
    auth.refresh.mockRejectedValue(new ApiError(401, "invalid_credentials"));
    window.localStorage.setItem(
      "hff.session.v1",
      JSON.stringify({ accountId: "u-1", role: "USER", email: "old@b.co" }),
    );
    const { restore, getSnapshot } = await LOAD();

    restore();
    await vi.advanceTimersByTimeAsync(1);

    const { session } = getSnapshot();
    expect(session).toBeNull();
    expect(window.localStorage.getItem("hff.session.v1")).toBeNull();
  });

  it("a network error during refresh leaves the current memory token untouched", async () => {
    auth.refresh.mockRejectedValue(new Error("network_error"));
    const { signIn, getSnapshot } = await LOAD();

    signIn(AUTH_RESPONSE, "a@b.co");
    await vi.advanceTimersByTimeAsync(900_000 - 5_000);

    const { session } = getSnapshot();
    expect(session?.accessToken).toBe("at-1");
    // Identity hint stays too — nothing forced the session to end.
    expect(JSON.parse(window.localStorage.getItem("hff.session.v1")!)).toEqual({
      accountId: "u-1",
      role: "USER",
      email: "a@b.co",
    });
  });

  it("signOut calls POST /v1/auth/logout and clears local state", async () => {
    auth.logout.mockResolvedValue(undefined);
    const { signIn, signOut, getSnapshot } = await LOAD();

    signIn(AUTH_RESPONSE, "a@b.co");
    signOut();

    expect(auth.logout).toHaveBeenCalledTimes(1);
    const { session } = getSnapshot();
    expect(session).toBeNull();
    expect(window.localStorage.getItem("hff.session.v1")).toBeNull();
  });

  it("signOut clears local state even when logout fails", async () => {
    auth.logout.mockRejectedValue(new Error("network_error"));
    const { signIn, signOut, getSnapshot } = await LOAD();

    signIn(AUTH_RESPONSE, "a@b.co");
    signOut();

    await vi.advanceTimersByTimeAsync(1);
    const { session } = getSnapshot();
    expect(session).toBeNull();
    expect(window.localStorage.getItem("hff.session.v1")).toBeNull();
  });

  it("restore re-materializes a signed-in session from a persisted hint via the cookie", async () => {
    auth.refresh.mockResolvedValue({ ...AUTH_RESPONSE, accessToken: "at-boot" });
    window.localStorage.setItem(
      "hff.session.v1",
      JSON.stringify({ accountId: "u-1", role: "USER", email: "boot@b.co" }),
    );
    const { restore, getSnapshot, subscribe } = await LOAD();
    const notify = vi.fn();
    subscribe(notify);

    restore();
    expect(getSnapshot().restoring).toBe(true);

    await vi.advanceTimersByTimeAsync(1);
    expect(auth.refresh).toHaveBeenCalledTimes(1);
    expect(getSnapshot().session?.accessToken).toBe("at-boot");
    expect(getSnapshot().session?.role).toBe("USER");
    expect(getSnapshot().restoring).toBe(false);
  });

  it("restore is a no-op when no identity hint exists", async () => {
    const { restore, getSnapshot } = await LOAD();
    restore();
    await vi.advanceTimersByTimeAsync(1);
    expect(auth.refresh).not.toHaveBeenCalled();
    expect(getSnapshot().session).toBeNull();
    expect(getSnapshot().restoring).toBe(false);
  });

  it("single-flight: concurrent refresh calls share one in-flight request", async () => {
    let resolve!: (v: typeof AUTH_RESPONSE) => void;
    auth.refresh.mockReturnValue(
      new Promise((r) => {
        resolve = r;
      }),
    );
    const { signIn, refreshAccessToken } = await LOAD();
    signIn(AUTH_RESPONSE, "a@b.co");

    // Force a refresh right at expiry and issue a second overlapping call.
    await vi.advanceTimersByTimeAsync(900_000 - 5_000); // schedules + fires timer
    const p1 = refreshAccessToken();
    const p2: Promise<void> = refreshAccessToken();
    expect(auth.refresh).toHaveBeenCalledTimes(1);

    resolve({ ...AUTH_RESPONSE, accessToken: "at-2" });
    await Promise.all([p1, p2]);
    expect(auth.refresh).toHaveBeenCalledTimes(1);
  });

  it("signIn feeds the access token into the api client (sc-138 injection)", async () => {
    const { signIn } = await LOAD();
    signIn(AUTH_RESPONSE, "a@b.co");
    expect(authToken).toHaveBeenCalledWith("at-1");
  });

  it("signOut clears the access token from the api client (sc-138)", async () => {
    auth.logout.mockResolvedValue(undefined);
    const { signIn, signOut } = await LOAD();
    signIn(AUTH_RESPONSE, "a@b.co");
    signOut();
    // Last invocation hands the client a null token so no stale bearer leaks.
    expect(authToken).toHaveBeenLastCalledWith(null);
  });

  it("a 401 refresh (expired cookie) also clears the client access token (sc-138)", async () => {
    auth.refresh.mockRejectedValue(new ApiError(401, "invalid_credentials"));
    const { signIn } = await LOAD();
    signIn(AUTH_RESPONSE, "a@b.co");
    await vi.advanceTimersByTimeAsync(900_000 - 5_000);
    expect(authToken).toHaveBeenLastCalledWith(null);
  });
});