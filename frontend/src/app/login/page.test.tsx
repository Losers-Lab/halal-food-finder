import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, type AuthResponse } from "@/lib/api/client";
import LoginPage from "./page";

const pushMock = vi.fn();
const searchParamsGet = vi.fn<(key: string) => string | null>(() => null);
const signInMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: pushMock }),
  useSearchParams: () => ({ get: searchParamsGet }),
}));

vi.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({
    session: null,
    restoring: false,
    signIn: signInMock,
    signOut: vi.fn(),
  }),
}));

vi.mock("@/lib/api/client", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/api/client")>();
  return {
    ...original,
    api: { ...original.api, login: vi.fn() },
  };
});

const authResponse: AuthResponse = {
  accessToken: "at",
  tokenType: "Bearer",
  expiresIn: 900,
  accountId: "u-1",
  role: "USER",
};

async function submitCredentials(user: ReturnType<typeof userEvent.setup>, email: string, password: string) {
  await user.type(screen.getByLabelText("Email"), email);
  await user.type(screen.getByLabelText("Password"), password);
  await user.click(screen.getByRole("button", { name: "Log in" }));
}

describe("LoginPage (#1)", () => {
  beforeEach(() => {
    vi.mocked(api.login).mockReset();
    pushMock.mockReset();
    signInMock.mockReset();
    searchParamsGet.mockReset();
    searchParamsGet.mockReturnValue(null);
  });

  it("signs in and routes home on success (sc-40 logged-in state)", async () => {
    vi.mocked(api.login).mockResolvedValueOnce(authResponse);
    const user = userEvent.setup();
    render(<LoginPage />);
    await submitCredentials(user, "a@b.co", "password1");

    await waitFor(() => expect(signInMock).toHaveBeenCalledWith(authResponse, "a@b.co"));
    expect(pushMock).toHaveBeenCalledWith("/");
  });

  it("renders the account-created notice when redirected with ?created=1 (sc-39)", () => {
    searchParamsGet.mockReturnValue("1");
    render(<LoginPage />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "Account created. Log in to continue.",
    );
  });

  it("shows one combined credential error, clears + refocuses the password, preserves the email (sc-40)", async () => {
    vi.mocked(api.login).mockRejectedValueOnce(
      new ApiError(401, "invalid_credentials"),
    );
    const user = userEvent.setup();
    render(<LoginPage />);
    const password = screen.getByLabelText("Password");
    await submitCredentials(user, "a@b.co", "password1");

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Incorrect email or password.");
    expect(screen.getAllByRole("alert")).toHaveLength(1);
    // password cleared + refocused; email preserved (anti-enumeration).
    expect(password).toHaveValue("");
    await waitFor(() => expect(password).toHaveFocus());
    expect(screen.getByLabelText("Email")).toHaveValue("a@b.co");
  });

  it("shows the generic banner for a non-credential server error (sc-40)", async () => {
    vi.mocked(api.login).mockRejectedValueOnce(new ApiError(500, "invalid_input"));
    const user = userEvent.setup();
    render(<LoginPage />);
    await submitCredentials(user, "a@b.co", "password1");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });

  it("shows the generic banner for a raw (non-ApiError) failure (sc-40)", async () => {
    vi.mocked(api.login).mockRejectedValueOnce(new Error("boom"));
    const user = userEvent.setup();
    render(<LoginPage />);
    await submitCredentials(user, "a@b.co", "password1");

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });

  it("validates client-side: empty password shows a field error and does not call the API (auth-screens)", async () => {
    const user = userEvent.setup();
    render(<LoginPage />);
    await user.type(screen.getByLabelText("Email"), "a@b.co");
    await user.click(screen.getByRole("button", { name: "Log in" }));

    expect(await screen.findByText("Enter your password.")).toBeInTheDocument();
    expect(api.login).not.toHaveBeenCalled();
  });

  it("disables inputs and shows the loading label while the request is in flight (UX loading)", async () => {
    let resolveLogin!: (v: AuthResponse) => void;
    vi.mocked(api.login).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveLogin = resolve;
      }),
    );
    const user = userEvent.setup();
    render(<LoginPage />);
    await submitCredentials(user, "a@b.co", "password1");

    expect(screen.getByRole("button", { name: "Logging in…" })).toBeDisabled();
    expect(screen.getByLabelText("Email")).toBeDisabled();
    expect(screen.getByLabelText("Password")).toBeDisabled();

    resolveLogin(authResponse);
    await waitFor(() => expect(signInMock).toHaveBeenCalled());
  });

  it("links to signup and presents the forgot-password link (auth-screens)", () => {
    render(<LoginPage />);
    const signup = screen.getByRole("link", { name: "Sign up" });
    expect(signup).toHaveAttribute("href", "/signup");
    expect(screen.getByRole("link", { name: "Forgot password?" })).toBeInTheDocument();
  });
});
