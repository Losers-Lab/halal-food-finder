import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import type { Restaurant } from "@/lib/listings/restaurants";
import { FavoritesProvider, useFavorites } from "./FavoritesProvider";

vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: vi.fn() }));
vi.mock("@/lib/listings/data", () => ({ getFavorites: vi.fn() }));

import { useAuth } from "@/lib/auth/AuthProvider";
import { getFavorites } from "@/lib/listings/data";

const mockedUseAuth = vi.mocked(useAuth);
const getFavoritesMock = vi.mocked(getFavorites);

function restaurant(id = "l-1", name = "Al-Amir Grill"): Restaurant {
  return {
    id,
    name,
    address: "112 Atlantic Ave, Brooklyn, NY",
    lat: 40.6916,
    lng: -73.9788,
    cuisine: "Middle Eastern",
    cuttingMethod: "HAND_CUT",
  };
}

function signedIn(accountId = "acc-1") {
  mockedUseAuth.mockReturnValue({
    session: {
      accessToken: "at",
      tokenType: "Bearer",
      expiresAt: Date.now() + 60_000,
      accountId,
      role: "USER",
      email: "a@b.co",
    },
    restoring: false,
    signIn: vi.fn(),
    signOut: vi.fn(),
  });
}

/** Renders the provider behind a consumer that exposes its state + toggle. */
function Consumer({ id, rest }: { id: string; rest?: Restaurant }) {
  const {
    favorites,
    status,
    isFavorited,
    toggleFavorites,
    favoritesError,
  } = useFavorites();
  return (
    <div>
      <span data-testid="status">{status}</span>
      <span data-testid="count">{favorites.length}</span>
      <span data-testid="favorited">{isFavorited(id) ? "yes" : "no"}</span>
      <button type="button" onClick={() => toggleFavorites(id, rest)}>
        toggle
      </button>
      <span data-testid="error">{favoritesError ?? ""}</span>
    </div>
  );
}

describe("FavoritesProvider (sc-50/51/52)", () => {
  beforeEach(() => {
    getFavoritesMock.mockReset();
    vi.restoreAllMocks();
    getFavoritesMock.mockResolvedValue([]);
  });

  it("fetches the favorites list once a session is present", async () => {
    signedIn();
    getFavoritesMock.mockResolvedValue([restaurant()]);
    render(
      <FavoritesProvider>
        <Consumer id="l-1" />
      </FavoritesProvider>,
    );
    expect(screen.getByTestId("status").textContent).toBe("loading");
    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("ready"),
    );
    expect(getFavoritesMock).toHaveBeenCalledTimes(1);
    expect(screen.getByTestId("count").textContent).toBe("1");
    expect(screen.getByTestId("favorited").textContent).toBe("yes");
  });

  it("holds nothing and never fetches for an anonymous visitor", async () => {
    mockedUseAuth.mockReturnValue({
      session: null,
      restoring: false,
      signIn: vi.fn(),
      signOut: vi.fn(),
    });
    render(
      <FavoritesProvider>
        <Consumer id="l-1" />
      </FavoritesProvider>,
    );
    expect(getFavoritesMock).not.toHaveBeenCalled();
    expect(screen.getByTestId("status").textContent).toBe("idle");
    expect(screen.getByTestId("count").textContent).toBe("0");
  });

  it("favourites optimistically: flips the heart and grows the /saved list, then POSTs (sc-50)", async () => {
    signedIn();
    getFavoritesMock.mockResolvedValue([]);
    const favoriteListing = vi
      .spyOn(api, "favoriteListing")
      .mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <FavoritesProvider>
        <Consumer id="l-1" rest={restaurant()} />
      </FavoritesProvider>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("ready"),
    );

    await user.click(screen.getByRole("button", { name: "toggle" }));
    // Optimistic — the heart flips and the list grows before the POST resolves.
    expect(screen.getByTestId("favorited").textContent).toBe("yes");
    expect(screen.getByTestId("count").textContent).toBe("1");

    await waitFor(() =>
      expect(favoriteListing).toHaveBeenCalledWith("l-1"),
    );
  });

  it("unfavourites optimistically: removes from the /saved list, then DELETEs (sc-51)", async () => {
    signedIn();
    getFavoritesMock.mockResolvedValue([restaurant()]);
    const unfavoriteListing = vi
      .spyOn(api, "unfavoriteListing")
      .mockResolvedValue(undefined);
    const user = userEvent.setup();
    render(
      <FavoritesProvider>
        <Consumer id="l-1" rest={restaurant()} />
      </FavoritesProvider>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("ready"),
    );

    await user.click(screen.getByRole("button", { name: "toggle" }));
    expect(screen.getByTestId("favorited").textContent).toBe("no");
    expect(screen.getByTestId("count").textContent).toBe("0");

    await waitFor(() =>
      expect(unfavoriteListing).toHaveBeenCalledWith("l-1"),
    );
  });

  it("reverts the optimistic flip on failure and surfaces a message (never lies)", async () => {
    signedIn();
    getFavoritesMock.mockResolvedValue([]);
    // Deferred rejection so we can observe the optimistic flip before the failure.
    let fail: (e: Error) => void = () => undefined;
    vi.spyOn(api, "favoriteListing").mockImplementation(
      () =>
        new Promise<undefined>((_resolve, reject) => {
          fail = reject;
        }),
    );
    const user = userEvent.setup();
    render(
      <FavoritesProvider>
        <Consumer id="l-1" rest={restaurant()} />
      </FavoritesProvider>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("status").textContent).toBe("ready"),
    );

    await user.click(screen.getByRole("button", { name: "toggle" }));
    // Flips optimistically before the POST settles…
    expect(screen.getByTestId("favorited").textContent).toBe("yes");
    expect(screen.getByTestId("count").textContent).toBe("1");

    // …then reverts once the POST fails, and a message surfaces.
    fail(new Error("boom"));
    await waitFor(() =>
      expect(screen.getByTestId("favorited").textContent).toBe("no"),
    );
    expect(screen.getByTestId("error").textContent).toMatch(/couldn't save/i);
    await waitFor(() => expect(screen.getByTestId("count").textContent).toBe("0"));
  });
});
