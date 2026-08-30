import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "@/lib/api/client";
import { useAuth } from "@/lib/auth/AuthProvider";
import AddListingPage from "./page";

vi.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: vi.fn(),
}));

vi.mock("@/lib/api/client", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/api/client")>();
  return {
    ...original,
    api: { ...original.api, createListing: vi.fn() },
  };
});

const mockedUseAuth = vi.mocked(useAuth);

const signedIn = {
  session: { email: "owner@example.com" },
  restoring: false,
};

/** Fill and submit the listing form with valid values (session pre-mocked). */
async function fillValidForm() {
  const user = userEvent.setup();
  render(<AddListingPage />);
  await user.type(screen.getByLabelText("Restaurant name"), "Al-Amir Grill");
  await user.type(screen.getByLabelText("Address"), "123 Main St");
  await user.type(screen.getByLabelText("Latitude"), "40.7128");
  await user.type(screen.getByLabelText("Longitude"), "-74.0060");
  await user.type(screen.getByLabelText("Cuisine"), "Middle Eastern");
  await user.selectOptions(
    screen.getByLabelText("Cutting method"),
    "HAND_CUT",
  );
  await user.click(screen.getByRole("button", { name: "Add listing" }));
}

describe("AddListingPage (sc-138) — auth gate", () => {
  beforeEach(() => {
    vi.mocked(api.createListing).mockReset();
    mockedUseAuth.mockReset();
  });

  it("prompts an unauthenticated visitor to log in (no form, link to /login)", () => {
    mockedUseAuth.mockReturnValue({
      session: null,
      restoring: false,
    } as never);
    render(<AddListingPage />);

    expect(
      screen.getByText("You need to be signed in to add a listing."),
    ).toBeInTheDocument();
    const link = screen.getByRole("link", { name: "Log in to continue" });
    expect(link).toHaveAttribute("href", "/login");
    expect(screen.queryByLabelText("Restaurant name")).not.toBeInTheDocument();
  });

  it("holds a neutral state while a persisted session is restoring", () => {
    mockedUseAuth.mockReturnValue({ session: null, restoring: true } as never);
    render(<AddListingPage />);
    expect(screen.getByRole("main").firstChild).toHaveAttribute(
      "aria-busy",
      "true",
    );
    expect(
      screen.queryByText("You need to be signed in"),
    ).not.toBeInTheDocument();
  });
});

describe("AddListingPage (sc-138) — form success + data contract", () => {
  beforeEach(() => {
    vi.mocked(api.createListing).mockReset();
    mockedUseAuth.mockReturnValue(signedIn as never);
  });

  it("posts to the backend with numeric lat/lng and shows the honest unverified success state", async () => {
    vi.mocked(api.createListing).mockResolvedValueOnce({
      id: "l-1",
      name: "Al-Amir Grill",
      address: "123 Main St",
      lat: 40.7128,
      lng: -74.006,
      cuisine: "middle eastern",
      cuttingMethod: "HAND_CUT",
      ownerId: "acc-1",
      verificationStatus: "UNVERIFIED",
      createdAt: "2026-08-30T00:00:00Z",
    });

    await fillValidForm();

    await waitFor(() =>
      expect(api.createListing).toHaveBeenCalledWith({
        name: "Al-Amir Grill",
        address: "123 Main St",
        lat: 40.7128,
        lng: -74.006,
        cuisine: "Middle Eastern",
        cuttingMethod: "HAND_CUT",
      }),
    );

    expect(await screen.findByText("Listing saved")).toBeInTheDocument();
    expect(screen.getByText("Al-Amir Grill")).toBeInTheDocument();
    // Unverified semantics: neutral tag present, no premium "Verified" styling.
    expect(screen.getByText("Unverified")).toBeInTheDocument();
    expect(screen.queryByText("Verified")).not.toBeInTheDocument();
  });

  it("logs the generic copy for a non-ApiError failure (network)", async () => {
    vi.mocked(api.createListing).mockRejectedValueOnce(
      new TypeError("Failed to fetch"),
    );
    await fillValidForm();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });

  it("defaults the cutting method to UNSPECIFIED (any) until the user chooses", async () => {
    vi.mocked(api.createListing).mockResolvedValueOnce({
      id: "l-1",
      name: "Al-Amir Grill",
      address: "123 Main St",
      lat: 40.7128,
      lng: -74.006,
      cuisine: "middle eastern",
      cuttingMethod: "UNSPECIFIED",
      ownerId: "acc-1",
      verificationStatus: "UNVERIFIED",
      createdAt: "2026-08-30T00:00:00Z",
    });

    const user = userEvent.setup();
    render(<AddListingPage />);
    await user.type(screen.getByLabelText("Restaurant name"), "Al-Amir Grill");
    await user.type(screen.getByLabelText("Address"), "123 Main St");
    await user.type(screen.getByLabelText("Latitude"), "40.7128");
    await user.type(screen.getByLabelText("Longitude"), "-74.0060");
    await user.type(screen.getByLabelText("Cuisine"), "Middle Eastern");
    await user.click(screen.getByRole("button", { name: "Add listing" }));

    await waitFor(() =>
      expect(api.createListing).toHaveBeenCalledWith(
        expect.objectContaining({ cuttingMethod: "UNSPECIFIED" }),
      ),
    );
  });

  it("disables inputs and shows the loading label while the request is in flight", async () => {
    let resolveCreate!: (v: never) => void;
    vi.mocked(api.createListing).mockReturnValueOnce(
      new Promise<never>((resolve) => {
        resolveCreate = resolve;
      }),
    );
    await fillValidForm();

    expect(
      screen.getByRole("button", { name: "Saving listing…" }),
    ).toBeDisabled();
    expect(screen.getByLabelText("Restaurant name")).toBeDisabled();

    resolveCreate({
      id: "l-1",
      name: "Al-Amir Grill",
      address: "123 Main St",
      lat: 40.7128,
      lng: -74.006,
      cuisine: "middle eastern",
      cuttingMethod: "HAND_CUT",
      ownerId: "acc-1",
      verificationStatus: "UNVERIFIED",
      createdAt: "2026-08-30T00:00:00Z",
    } as never);
    await waitFor(() => screen.findByText("Listing saved"));
  });
});

describe("AddListingPage (sc-138) — validation + backend error surfacing", () => {
  beforeEach(() => {
    vi.mocked(api.createListing).mockReset();
    mockedUseAuth.mockReturnValue(signedIn as never);
  });

  it("blocks submission client-side on blank required fields, no API call", async () => {
    const user = userEvent.setup();
    render(<AddListingPage />);
    await user.click(screen.getByRole("button", { name: "Add listing" }));

    expect(
      await screen.findByText("Restaurant name is required."),
    ).toBeInTheDocument();
    expect(screen.getByText("Address is required.")).toBeInTheDocument();
    expect(screen.getByText("Latitude is required.")).toBeInTheDocument();
    expect(screen.getByText("Longitude is required.")).toBeInTheDocument();
    expect(screen.getByText("Cuisine is required.")).toBeInTheDocument();

    // cuttingMethod is defaulted, so only the text fields are invalid.
    expect(api.createListing).not.toHaveBeenCalled();
  });

  it("rejects an out-of-range lat client-side", async () => {
    const user = userEvent.setup();
    render(<AddListingPage />);
    await user.type(screen.getByLabelText("Restaurant name"), "Al-Amir Grill");
    await user.type(screen.getByLabelText("Address"), "123 Main St");
    await user.type(screen.getByLabelText("Latitude"), "91");
    await user.type(screen.getByLabelText("Longitude"), "-74.0");
    await user.type(screen.getByLabelText("Cuisine"), "Middle Eastern");
    await user.click(screen.getByRole("button", { name: "Add listing" }));

    expect(
      await screen.findByText("Latitude must be between -90 and 90."),
    ).toBeInTheDocument();
    expect(api.createListing).not.toHaveBeenCalled();
  });

  it("surfaces a 400 invalid_input with the backend field message (sc-134 fail-fast)", async () => {
    vi.mocked(api.createListing).mockRejectedValueOnce(
      new ApiError(400, "invalid_input", "name is required; address is required"),
    );
    await fillValidForm();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "name is required; address is required",
    );
  });

  it("surfaces a 401 as a session-expired prompt", async () => {
    vi.mocked(api.createListing).mockRejectedValueOnce(
      new ApiError(401, "invalid_credentials"),
    );
    await fillValidForm();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Your session has expired. Please log in again to add a listing.",
    );
  });
});