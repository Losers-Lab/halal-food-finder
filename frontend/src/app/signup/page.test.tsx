import { render, screen, waitFor } from "@testing-library/react";
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

describe("SignUpPage — success + remaining error branches (#2)", () => {
  beforeEach(() => {
    vi.mocked(api.signup).mockReset();
    pushMock.mockReset();
  });

  it("routes to /login?created=1 on a valid submit (sc-39 account-created flow)", async () => {
    vi.mocked(api.signup).mockResolvedValueOnce({
      id: "u-1",
      email: "a@b.co",
      role: "USER",
    } as never);
    const user = userEvent.setup();
    render(<SignUpPage />);
    await user.type(screen.getByLabelText("Email"), "a@b.co");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    await waitFor(() => expect(pushMock).toHaveBeenCalledWith("/login?created=1"));
  });

  it("renders the backend weak-password detail as the password field error (sc-39)", async () => {
    vi.mocked(api.signup).mockRejectedValueOnce(
      new ApiError(422, "weak_password", "Password must be at least 8 characters."),
    );
    const user = userEvent.setup();
    render(<SignUpPage />);
    await user.type(screen.getByLabelText("Email"), "a@b.co");
    // Client-valid password (>=8 chars) so the request reaches the backend.
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Password must be at least 8 characters.");
    // Field error wired via aria-describedby on the password input.
    const password = screen.getByLabelText("Password");
    expect(password).toHaveAttribute("aria-invalid", "true");
    const describedBy = password.getAttribute("aria-describedby");
    expect(describedBy).toBeTruthy();
    expect(document.getElementById(describedBy!)).toBe(alert);
  });

  it("falls back to the generic copy when the weak-password detail is empty (sc-39)", async () => {
    vi.mocked(api.signup).mockRejectedValueOnce(
      new ApiError(422, "weak_password", ""),
    );
    const user = userEvent.setup();
    render(<SignUpPage />);
    await user.type(screen.getByLabelText("Email"), "a@b.co");
    // Client-valid password (>=8 chars) so the request reaches the backend.
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Password is too weak. Use at least 8 characters.",
    );
  });

  it("shows the generic banner for a raw (non-ApiError) failure (sc-39)", async () => {
    vi.mocked(api.signup).mockRejectedValueOnce(new TypeError("Failed to fetch"));
    const user = userEvent.setup();
    render(<SignUpPage />);
    await user.type(screen.getByLabelText("Email"), "a@b.co");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });

  it("disables inputs and shows the loading label while the request is in flight (UX loading)", async () => {
    let resolveSignup!: (v: { id: string; email: string; role: string }) => void;
    vi.mocked(api.signup).mockReturnValueOnce(
      new Promise<{ id: string; email: string; role: string }>((resolve) => {
        resolveSignup = resolve;
      }),
    );
    const user = userEvent.setup();
    render(<SignUpPage />);
    await user.type(screen.getByLabelText("Email"), "a@b.co");
    await user.type(screen.getByLabelText("Password"), "password123");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(
      screen.getByRole("button", { name: "Creating account…" }),
    ).toBeDisabled();
    expect(screen.getByLabelText("Email")).toBeDisabled();
    expect(screen.getByLabelText("Password")).toBeDisabled();

    resolveSignup({ id: "u-1", email: "a@b.co", role: "USER" });
    await waitFor(() => expect(pushMock).toHaveBeenCalled());
  });

  it("validates client-side: malformed email + short password block submission, no API call (sc-39)", async () => {
    const user = userEvent.setup();
    render(<SignUpPage />);
    await user.type(screen.getByLabelText("Email"), "not-an-email");
    await user.type(screen.getByLabelText("Password"), "short");
    await user.click(screen.getByRole("button", { name: "Create account" }));

    expect(
      await screen.findByText("Enter a valid email address."),
    ).toBeInTheDocument();
    expect(
      await screen.findByText("Password is too weak. Use at least 8 characters."),
    ).toBeInTheDocument();
    expect(api.signup).not.toHaveBeenCalled();
  });
});