import { act, renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import { fetchVerificationQueue, type ReviewWithListing } from "./data";
import { useVerificationQueue } from "./useVerificationQueue";

vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: vi.fn() }));
vi.mock("@/lib/api/client", () => ({
  api: {
    approveVerificationReview: vi.fn(),
    denyVerificationReview: vi.fn(),
  },
  ApiError: class ApiError extends Error {
    status: number;
    code: string;
    detail?: string;
    constructor(status: number, code: string, detail?: string) {
      super(code);
      this.status = status;
      this.code = code;
      this.detail = detail;
    }
  },
}));
vi.mock("./data", () => ({ fetchVerificationQueue: vi.fn() }));

const mockedUseAuth = vi.mocked(useAuth);
const mockedFetch = vi.mocked(fetchVerificationQueue);
const mockedApprove = vi.mocked(api.approveVerificationReview);
const mockedDeny = vi.mocked(api.denyVerificationReview);

function committeeSession(over: Record<string, unknown> = {}) {
  return {
    session: {
      accessToken: "at",
      tokenType: "Bearer",
      expiresAt: Date.now() + 60_000,
      accountId: "acc-1",
      role: "VERIFICATION_COMMITTEE",
      email: "vc@tahirs.co",
      ...over,
    },
    restoring: false,
    signIn: vi.fn(),
    signOut: vi.fn(),
  };
}

function userSession() {
  return committeeSession({ role: "USER" });
}

function review(over: Partial<ReviewWithListing> = {}): ReviewWithListing {
  return {
    reviewId: "r-1",
    listingId: "l-1",
    submittedBy: "u-1",
    state: "AI_SUGGESTED",
    suggestedVerdict: "APPROVE",
    suggestionConfidence: 0.97,
    suggestionReasoning: null,
    decisionOutcome: null,
    decisionReason: null,
    decidedBy: null,
    listing: {
      id: "l-1",
      name: "Al-Amir Grill",
      address: "112 Atlantic Ave, Brooklyn, NY",
      lat: 40.6916,
      lng: -73.9788,
      cuisine: "Middle Eastern",
      cuttingMethod: "HAND_CUT",
    },
    ...over,
  };
}

/** A promise we can resolve/reject manually (for pausing decision in flight). */
function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (error: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function wrapper({ children }: { children: ReactNode }) {
  return <>{children}</>;
}

describe("useVerificationQueue (sc-73)", () => {
  beforeEach(() => {
    mockedUseAuth.mockReset();
    mockedFetch.mockReset();
    mockedApprove.mockReset();
    mockedDeny.mockReset();
  });

  it("reports isCommittee false and never fetches for a non-VC session", () => {
    mockedUseAuth.mockReturnValue(userSession() as never);
    const { result } = renderHook(() => useVerificationQueue(), { wrapper });

    expect(result.current.isCommittee).toBe(false);
    expect(mockedFetch).not.toHaveBeenCalled();
  });

  it("fetches the queue for a VC session and exposes it", async () => {
    mockedUseAuth.mockReturnValue(committeeSession() as never);
    mockedFetch.mockResolvedValue([review()]);
    const { result } = renderHook(() => useVerificationQueue(), { wrapper });

    expect(result.current.isCommittee).toBe(true);
    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.reviews).toHaveLength(1);
    expect(result.current.reviews[0].listing?.name).toBe("Al-Amir Grill");
  });

  it("surfaces an error status when the queue fetch fails", async () => {
    mockedUseAuth.mockReturnValue(committeeSession() as never);
    mockedFetch.mockRejectedValue(new Error("boom"));
    const { result } = renderHook(() => useVerificationQueue(), { wrapper });

    await waitFor(() => expect(result.current.status).toBe("error"));
  });

  it("approves: removes the review and shows a VERIFIED success notice", async () => {
    mockedUseAuth.mockReturnValue(committeeSession() as never);
    mockedFetch.mockResolvedValue([review()]);
    mockedApprove.mockResolvedValue(review() as never);
    const { result } = renderHook(() => useVerificationQueue(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe("ready"));

    act(() => result.current.approve("r-1"));
    await waitFor(() => expect(result.current.reviews).toHaveLength(0));
    expect(mockedApprove).toHaveBeenCalledWith("r-1", undefined);
    expect(result.current.notice).toMatch(/VERIFIED/i);
  });

  it("deny: requires a reason, removes the review and shows an unverified notice", async () => {
    mockedUseAuth.mockReturnValue(committeeSession() as never);
    mockedFetch.mockResolvedValue([review()]);
    mockedDeny.mockResolvedValue(review() as never);
    const { result } = renderHook(() => useVerificationQueue(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe("ready"));

    act(() => result.current.deny("r-1", "Cert image is expired."));
    await waitFor(() => expect(result.current.reviews).toHaveLength(0));
    expect(mockedDeny).toHaveBeenCalledWith("r-1", "Cert image is expired.");
    expect(result.current.notice).toMatch(/unverified/i);
  });

  it("reports a per-review decision error on failure and keeps the review", async () => {
    mockedUseAuth.mockReturnValue(committeeSession() as never);
    mockedFetch.mockResolvedValue([review()]);
    mockedApprove.mockRejectedValue(new ApiError(409, "review_not_pending") as never);
    const { result } = renderHook(() => useVerificationQueue(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe("ready"));

    act(() => result.current.approve("r-1"));
    await waitFor(() => expect(result.current.decisionError?.reviewId).toBe("r-1"));
    expect(result.current.reviews).toHaveLength(1);
    expect(result.current.decisionError?.message).toMatch(/already decided/i);
  });

  it("locks a review's buttons while its decision is in flight", async () => {
    mockedUseAuth.mockReturnValue(committeeSession() as never);
    mockedFetch.mockResolvedValue([review()]);
    const pending = deferred<never>();
    mockedApprove.mockReturnValue(pending.promise as never);
    const { result } = renderHook(() => useVerificationQueue(), { wrapper });
    await waitFor(() => expect(result.current.status).toBe("ready"));

    act(() => result.current.approve("r-1"));
    expect(result.current.isDeciding("r-1")).toBe(true);

    act(() => pending.resolve(review() as never));
    await waitFor(() => expect(result.current.isDeciding("r-1")).toBe(false));
  });
});