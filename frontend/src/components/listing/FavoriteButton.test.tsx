import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Restaurant } from "@/lib/listings/restaurants";
import { FavoriteButton } from "./FavoriteButton";

vi.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: vi.fn() }));
vi.mock("@/lib/favorites/FavoritesProvider", () => ({ useFavorites: vi.fn() }));

import { useAuth } from "@/lib/auth/AuthProvider";
import { useFavorites } from "@/lib/favorites/FavoritesProvider";

const mockedUseAuth = vi.mocked(useAuth);
const mockedUseFavorites = vi.mocked(useFavorites);

const toggleFavorites = vi.fn();
const clearFavoritesError = vi.fn();

function favorited(trueFalse: boolean) {
  mockedUseFavorites.mockReturnValue({
    favorites: [],
    status: "ready",
    pendingAuth: false,
    isFavorited: () => trueFalse,
    isPending: () => false,
    toggleFavorites,
    favoritesError: null,
    clearFavoritesError,
    retryFavorites: vi.fn(),
  });
}

function restaurant(over: Partial<Restaurant> = {}): Restaurant {
  return {
    id: "l-1",
    name: "Al-Amir Grill",
    address: "112 Atlantic Ave, Brooklyn, NY",
    lat: 40.6916,
    lng: -73.9788,
    cuisine: "Middle Eastern",
    cuttingMethod: "HAND_CUT",
    ...over,
  };
}

function signedIn() {
  mockedUseAuth.mockReturnValue({
    session: {
      accessToken: "at",
      tokenType: "Bearer",
      expiresAt: Date.now() + 60_000,
      accountId: "acc-1",
      role: "USER",
      email: "a@b.co",
    },
    restoring: false,
    signIn: vi.fn(),
    signOut: vi.fn(),
  });
}

function anonymous() {
  mockedUseAuth.mockReturnValue({
    session: null,
    restoring: false,
    signIn: vi.fn(),
    signOut: vi.fn(),
  });
}

describe("FavoriteButton (sc-50/51)", () => {
  beforeEach(() => {
    toggleFavorites.mockReset();
    clearFavoritesError.mockReset();
    favorited(false);
  });

  it("renders a login link (not a 401) for an anonymous visitor", () => {
    anonymous();
    render(<FavoriteButton listingId="l-1" variant="detail" />);

    // The next/link test mock drops aria-label, so assert on the visible label
    // + target. (Real component renders aria-label="Save to favorites — log in".)
    const link = screen.getByRole("link", { name: "Save" });
    expect(link).toHaveAttribute("href", "/login");
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("toggles a listing on for a signed-in user (heart flips + provider called)", async () => {
    signedIn();
    const user = userEvent.setup();
    render(
      <FavoriteButton listingId="l-1" restaurant={restaurant()} variant="detail" />,
    );

    const btn = screen.getByRole("button", { name: "Save to favorites" });
    expect(btn).toHaveAttribute("aria-pressed", "false");
    await user.click(btn);

    expect(toggleFavorites).toHaveBeenCalledWith("l-1", restaurant());
    expect(clearFavoritesError).toHaveBeenCalled();
  });

  it("labels a favourited listing as Saved and un-favourites on click", async () => {
    signedIn();
    favorited(true);
    const user = userEvent.setup();
    render(<FavoriteButton listingId="l-1" variant="detail" />);

    const btn = screen.getByRole("button", { name: "Remove from favorites" });
    expect(btn).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("Saved")).toBeInTheDocument();

    await user.click(btn);
    expect(toggleFavorites).toHaveBeenCalledWith("l-1", undefined);
  });

  it("disables the toggle while the favorites list is still loading", () => {
    signedIn();
    mockedUseFavorites.mockReturnValue({
      favorites: [],
      status: "loading",
      pendingAuth: false,
      isFavorited: () => false,
      isPending: () => false,
      toggleFavorites,
      favoritesError: null,
      clearFavoritesError,
      retryFavorites: vi.fn(),
    });
    render(<FavoriteButton listingId="l-1" variant="detail" />);

    expect(
      screen.getByRole("button", { name: "Save to favorites" }),
    ).toBeDisabled();
  });

  it("shows a toggle failure message on the detail variant (never a silent lie)", () => {
    signedIn();
    mockedUseFavorites.mockReturnValue({
      favorites: [],
      status: "ready",
      pendingAuth: false,
      isFavorited: () => false,
      isPending: () => false,
      toggleFavorites,
      favoritesError: "We couldn't save that spot.",
      clearFavoritesError,
      retryFavorites: vi.fn(),
    });
    render(<FavoriteButton listingId="l-1" variant="detail" />);

    expect(screen.getByRole("alert")).toHaveTextContent(
      "We couldn't save that spot.",
    );
  });
});
