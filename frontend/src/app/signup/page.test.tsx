import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api/client";
import SignUpPage from "./page";

const pushMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock("@/lib/api/client", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/api/client")>();
  return {
    ...original,
    api: { ...original.api, signup: vi.fn() },
  };
});

describe("SignUpPage — email-not-unique error (#2)", () => {
  beforeEach(() => {
    vi.mocked(api.signup).mockReset();
  });

  async function submitDuplicateEmail() {
    const user = userEvent.setup();
    vi.mocked(api.signup).mockRejectedValueOnce(
      new ApiError(409, "email_already_exists"),
    );
    render(<SignUpPage />);
    await user.type(screen.getByLabelText("Email"), "dup@example.com");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Create account" }));
    // The server error surfaces as a field error under Email.
    return screen.findByRole("alert");
  }

  it("renders 'logging in' as a link to /login inside the email field error", async () => {
    const alert = await submitDuplicateEmail();

    expect(alert).toHaveTextContent(
      "An account with this email already exists. Try logging in instead.",
    );

    const link = screen.getByRole("link", { name: "logging in" });
    expect(link).toHaveAttribute("href", "/login");
    expect(alert).toContainElement(link);

    // a11y wiring preserved: the email input is invalid and described by this alert.
    const email = screen.getByLabelText("Email");
    expect(email).toHaveAttribute("aria-invalid", "true");
    const describedBy = email.getAttribute("aria-describedby");
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy!)).toBe(alert);
  });

  it("clears the conflict error once the user edits the email", async () => {
    const user = userEvent.setup();
    vi.mocked(api.signup).mockRejectedValueOnce(
      new ApiError(409, "email_already_exists"),
    );
    render(<SignUpPage />);
    const email = screen.getByLabelText("Email");
    await user.type(email, "dup@example.com");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    await screen.findByRole("link", { name: "logging in" });

    await user.clear(email);
    await user.type(email, "fresh@example.com");
    expect(
      screen.queryByRole("link", { name: "logging in" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});