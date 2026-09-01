import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { ReviewWithListing } from "@/lib/reviews/data";
import { ReviewCard } from "./ReviewCard";

vi.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const onApprove = vi.fn();
const onDeny = vi.fn();

function review(over: Partial<ReviewWithListing> = {}): ReviewWithListing {
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
    listing: {
      id: "l-1",
      name: "Al-Amir Grill",
      address: "112 Atlantic Ave, Brooklyn, NY",
      lat: 40.6916,
      lng: -73.9788,
      cuisine: "Middle Eastern",
      isHandCut: true,
    },
    ...over,
  };
}

function renderCard(over: {
  review?: ReviewWithListing;
  isDeciding?: boolean;
  error?: string;
} = {}) {
  return render(
    <ReviewCard
      review={over.review ?? review()}
      isDeciding={over.isDeciding ?? false}
      onApprove={onApprove}
      onDeny={onDeny}
      error={over.error}
    />,
  );
}

describe("ReviewCard (sc-73)", () => {
  beforeEach(() => {
    onApprove.mockReset();
    onDeny.mockReset();
  });

  it("shows the listing being decided and links to its detail page", () => {
    renderCard();
    const name = screen.getByRole("link", { name: "Al-Amir Grill" });
    expect(name).toHaveAttribute("href", "/restaurants/l-1");
    expect(screen.getByText(/112 Atlantic Ave/)).toBeInTheDocument();
  });

  it("labels the AI suggestion with the verdict and confidence", () => {
    renderCard();
    expect(screen.getByText(/AI suggests: Approve/)).toBeInTheDocument();
    expect(screen.getByText(/97%/)).toBeInTheDocument();
    expect(screen.getByText(/names the restaurant/)).toBeInTheDocument();
  });

  it("approve calls onApprove (no reason required)", async () => {
    const user = userEvent.setup();
    renderCard();
    await user.click(screen.getByRole("button", { name: "Approve verification" }));
    expect(onApprove).toHaveBeenCalledWith();
  });

  it("deny without a reason shows a validation error and does not submit", async () => {
    const user = userEvent.setup();
    renderCard();
    await user.click(screen.getByRole("button", { name: "Deny" }));

    const input = screen.getByPlaceholderText(/why are you denying/i);
    await user.click(screen.getByRole("button", { name: "Confirm deny" }));

    expect(
      screen.getByRole("alert"),
    ).toHaveTextContent(/enter a reason/i);
    expect(input).toBeInTheDocument(); // still inline, not submitted
    expect(onDeny).not.toHaveBeenCalled();
  });

  it("deny with a reason calls onDeny and passes the trimmed reason", async () => {
    const user = userEvent.setup();
    renderCard();
    await user.click(screen.getByRole("button", { name: "Deny" }));

    await user.type(
      screen.getByPlaceholderText(/why are you denying/i),
      "  Cert image appears to be expired.  ",
    );
    await user.click(screen.getByRole("button", { name: "Confirm deny" }));

    expect(onDeny).toHaveBeenCalledWith("Cert image appears to be expired.");
  });

  it("disables both decide controls while a decision is in flight", () => {
    renderCard({ isDeciding: true });
    // While loading, the primary renders its loading label ("Approving…").
    expect(
      screen.getByRole("button", { name: "Approving…" }),
    ).toBeDisabled();
    expect(screen.getByRole("button", { name: "Deny" })).toBeDisabled();
  });

  it("renders a per-review decision error inline", () => {
    renderCard({ error: "This review is no longer pending." });
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This review is no longer pending.",
    );
  });

  it("shows an unavailable-listing treatment when the listing is missing", () => {
    renderCard({ review: review({ listing: undefined }) });
    expect(screen.getByText("Listing unavailable")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Deny" }),
    ).toBeInTheDocument();
  });
});