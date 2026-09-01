import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { UseVerificationQueue } from "@/lib/reviews/useVerificationQueue";
import type { ReviewWithListing } from "@/lib/reviews/data";
import VerificationPage from "./page";

vi.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock("@/components/layout/SiteHeader", () => ({
  SiteHeader: () => <header>SiteHeader</header>,
}));
vi.mock("@/components/layout/SiteFooter", () => ({
  SiteFooter: () => <footer>SiteFooter</footer>,
}));
vi.mock("@/components/layout/MobileTabBar", () => ({
  MobileTabBar: () => <nav>MobileTabBar</nav>,
}));
vi.mock("@/components/reviews/ReviewCard", () => ({
  ReviewCard: ({ review }: { review: ReviewWithListing }) => (
    <article>{review.listing?.name ?? "Listing unavailable"}</article>
  ),
}));

vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: vi.fn() }));
vi.mock("@/lib/reviews/useVerificationQueue", () => ({
  useVerificationQueue: vi.fn(),
}));

import { useAuth } from "@/lib/auth/AuthProvider";
import { useVerificationQueue } from "@/lib/reviews/useVerificationQueue";

const mockedUseAuth = vi.mocked(useAuth);
const mockedQueue = vi.mocked(useVerificationQueue);

function queue(over: Partial<UseVerificationQueue> = {}): UseVerificationQueue {
  return {
    reviews: [],
    status: "idle",
    isCommittee: false,
    isDeciding: () => false,
    approve: vi.fn(),
    deny: vi.fn(),
    notice: null,
    clearNotice: vi.fn(),
    decisionError: null,
    clearDecisionError: vi.fn(),
    retry: vi.fn(),
    ...over,
  };
}

function session(role: string) {
  return {
    session: {
      accessToken: "at",
      tokenType: "Bearer",
      expiresAt: Date.now() + 60_000,
      accountId: "acc-1",
      role,
      email: "vc@tahirs.co",
    },
    restoring: false,
    signIn: vi.fn(),
    signOut: vi.fn(),
  };
}

describe("VerificationPage — /verification (sc-73)", () => {
  beforeEach(() => {
    mockedUseAuth.mockReset();
    mockedQueue.mockReset();
  });

  it("prompts an anonymous visitor to log in (never a 401)", () => {
    mockedUseAuth.mockReturnValue({
      session: null,
      restoring: false,
      signIn: vi.fn(),
      signOut: vi.fn(),
    });
    mockedQueue.mockReturnValue(queue());
    render(<VerificationPage />);

    expect(
      screen.getByRole("heading", { name: /Verification Committee sign in/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Log in" })).toHaveAttribute(
      "href",
      "/login",
    );
  });

  it("blocks an authenticated non-VC user with a no-access message (403 gate)", () => {
    mockedUseAuth.mockReturnValue(session("USER") as never);
    mockedQueue.mockReturnValue(queue());
    render(<VerificationPage />);

    expect(
      screen.getByRole("heading", { name: /No access to this queue/i }),
    ).toBeInTheDocument();
    // The queue must never be rendered for a non-VC session.
    expect(screen.queryByText("Al-Amir Grill")).not.toBeInTheDocument();
  });

  it("renders an empty state when the queue has no pending reviews", () => {
    mockedUseAuth.mockReturnValue(session("VERIFICATION_COMMITTEE") as never);
    mockedQueue.mockReturnValue(
      queue({ isCommittee: true, status: "ready", reviews: [] }),
    );
    render(<VerificationPage />);

    expect(
      screen.getByRole("heading", { name: /No pending verifications/i }),
    ).toBeInTheDocument();
  });

  it("renders the pending reviews for a VC session", () => {
    mockedUseAuth.mockReturnValue(session("VERIFICATION_COMMITTEE") as never);
    mockedQueue.mockReturnValue(
      queue({
        isCommittee: true,
        status: "ready",
        reviews: [
          {
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
              address: "1 St",
              lat: 0,
              lng: 0,
              cuisine: "Middle Eastern",
              isHandCut: true,
            },
          },
        ],
      }),
    );
    render(<VerificationPage />);

    expect(screen.getByText("Al-Amir Grill")).toBeInTheDocument();
    expect(screen.queryByText("No pending verifications")).not.toBeInTheDocument();
  });

  it("shows an error state with a retry action when the queue fetch fails", () => {
    const retry = vi.fn();
    mockedUseAuth.mockReturnValue(session("VERIFICATION_COMMITTEE") as never);
    mockedQueue.mockReturnValue(
      queue({ isCommittee: true, status: "error", retry }),
    );
    render(<VerificationPage />);

    const button = screen.getByRole("button", { name: "Retry" });
    button.click();
    expect(retry).toHaveBeenCalled();
  });
});