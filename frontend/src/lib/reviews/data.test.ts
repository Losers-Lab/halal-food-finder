import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, type VerificationReview } from "@/lib/api/client";
import { getRestaurant } from "@/lib/listings/data";
import { fetchVerificationQueue } from "./data";

vi.mock("@/lib/api/client", () => ({
  api: { getVerificationReviews: vi.fn() },
  ApiError: class ApiError extends Error {},
}));

vi.mock("@/lib/listings/data", () => ({
  getRestaurant: vi.fn(),
}));

const mockedGetReviews = vi.mocked(api.getVerificationReviews);
const mockGetRestaurant = vi.mocked(getRestaurant);

function review(over: Partial<VerificationReview> = {}): VerificationReview {
  return {
    reviewId: "r-1",
    listingId: "l-1",
    submittedBy: "u-1",
    state: "AI_SUGGESTED",
    suggestedVerdict: "APPROVE",
    suggestionConfidence: 0.97,
    suggestionReasoning: "Certification names the restaurant and is current.",
    decisionOutcome: null,
    decisionReason: null,
    decidedBy: null,
    ...over,
  };
}

describe("fetchVerificationQueue (sc-73)", () => {
  beforeEach(() => {
    mockedGetReviews.mockReset();
    mockGetRestaurant.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("calls getVerificationReviews, not the listing fetch when queue is empty", async () => {
    mockedGetReviews.mockResolvedValue([]);
    const result = await fetchVerificationQueue();
    expect(result).toEqual([]);
    expect(mockGetRestaurant).not.toHaveBeenCalled();
  });

  it("enriches each review with its resolved listing details", async () => {
    mockedGetReviews.mockResolvedValue([review()]);
    mockGetRestaurant.mockResolvedValue({
      id: "l-1",
      name: "Al-Amir Grill",
      address: "112 Atlantic Ave, Brooklyn, NY",
      lat: 40.6916,
      lng: -73.9788,
      cuisine: "Middle Eastern",
      isHandCut: true,
      isDelivery: false,
    });

    const result = await fetchVerificationQueue();
    expect(result[0]).toMatchObject({ reviewId: "r-1", listingId: "l-1" });
    expect(result[0].listing).toMatchObject({
      id: "l-1",
      name: "Al-Amir Grill",
    });
    expect(mockGetRestaurant).toHaveBeenCalledWith("l-1");
  });

  it("keeps a review when its listing 404s (listing unavailable, queue intact)", async () => {
    mockedGetReviews.mockResolvedValue([review()]);
    mockGetRestaurant.mockResolvedValue(undefined);

    const result = await fetchVerificationQueue();
    expect(result).toHaveLength(1);
    expect(result[0].reviewId).toBe("r-1");
    expect(result[0].listing).toBeUndefined();
  });

  it("keeps a review when its listing read throws (one bad listing ≠ dead queue)", async () => {
    mockedGetReviews.mockResolvedValue([review(), review({ reviewId: "r-2", listingId: "l-2" })]);
    mockGetRestaurant.mockImplementation(async (id: string) =>
      id === "l-1" ? ({ id: "l-1", name: "OK Grill", address: "1 St", lat: 0, lng: 0, cuisine: "X", isHandCut: true, isDelivery: false }) : Promise.reject(new Error("boom")),
    );

    const result = await fetchVerificationQueue();
    expect(result).toHaveLength(2);
    expect(result[0].listing?.name).toBe("OK Grill");
    expect(result[1].listing).toBeUndefined();
  });
});