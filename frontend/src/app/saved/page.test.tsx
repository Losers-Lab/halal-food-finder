import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Restaurant } from "@/lib/listings/restaurants";
import SavedPage from "./page";

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

vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: vi.fn() }));
vi.mock("@/lib/favorites/FavoritesProvider", () => ({ useFavorites: vi.fn() }));

import { useAuth } from "@/lib/auth/AuthProvider";
import { useFavorites } from "@/lib/favorites/FavoritesProvider";

const mockedUseAuth = vi.mocked(useAuth);
const mockedUseFavorites = vi.mocked(useFavorites);

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

function favoritesReady(list: Restaurant[]) {
  mockedUseFavorites.mockReturnValue({
    favorites: list,
    status: "ready",
    pendingAuth: false,
    isFavorited: (id: string) => list.some((f) => f.id === id),
    isPending: () => false,
    toggleFavorites: vi.fn(),
    favoritesError: null,
    clearFavoritesError: vi.fn(),
    retryFavorites: vi.fn(),
  });
}

describe("SavedPage — /saved (sc-52)", () => {
  beforeEach(() => {
    mockedUseAuth.mockReset();
    mockedUseFavorites.mockReset();
  });

  it("prompts an anonymous visitor to log in (never a 401)", () => {
    mockedUseAuth.mockReturnValue({
      session: null,
      restoring: false,
      signIn: vi.fn(),
      signOut: vi.fn(),
    });
    favoritesReady([]);
    render(<SavedPage />);

    expect(
      screen.getByRole("heading", { name: /log in to see your saved spots/i }),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Log in" })).toHaveAttribute(
      "href",
      "/login",
    );
  });

  it("renders the favourited listings as browse cards", () => {
    signedIn();
    favoritesReady([restaurant({ name: "Al-Amir Grill" })]);
    render(<SavedPage />);

    expect(
      screen.getByRole("link", { name: "Al-Amir Grill" }),
    ).toHaveAttribute("href", "/restaurants/l-1");
  });

  it("renders a quiet empty state with a browse CTA when nothing is saved", () => {
    signedIn();
    favoritesReady([]);
    render(<SavedPage />);

    expect(
      screen.getByRole("heading", { name: "No saved spots yet" }),
    ).toBeInTheDocument();
    const browse = screen.getByRole("link", { name: "Browse restaurants" });
    expect(browse).toHaveAttribute("href", "/search");
  });
});
