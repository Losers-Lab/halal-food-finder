import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Restaurant } from "@/lib/listings/restaurants";
import SearchPage from "./page";

function restaurant(over: Partial<Restaurant>): Restaurant {
  return {
    id: "l-1",
    slug: "al-amir-grill",
    name: "Al-Amir Grill",
    address: "112 Atlantic Ave, Brooklyn, NY",
    lat: 40.6916,
    lng: -73.9788,
    cuisine: "Middle Eastern",
    cuttingMethod: "HAND_CUT",
    rating: 4.6,
    reviewCount: 89,
    distanceMi: 1.2,
    ...over,
  };
}

const searchParamsGet = vi.fn<(key: string) => string | null>(() => null);

vi.mock("next/navigation", () => ({
  useSearchParams: () => ({ get: searchParamsGet }),
  useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
}));

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
vi.mock("@/lib/auth/AuthProvider", () => ({
  useAuth: () => ({ session: null, restoring: false, signOut: vi.fn() }),
}));

vi.mock("@/lib/listings/seed", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/listings/seed")>();
  return { ...original, searchListings: vi.fn() };
});
import { searchListings } from "@/lib/listings/seed";
const searchMock = vi.mocked(searchListings);

describe("SearchPage — search-first + browse chips (search-browse.md)", () => {
  beforeEach(() => {
    searchMock.mockReset();
    searchParamsGet.mockReset();
    searchParamsGet.mockReturnValue(null);
  });

  it("renders a result grid from the data layer and links each card to its detail page", async () => {
    searchMock.mockReturnValue([
      restaurant({ id: "l-1", name: "Al-Amir Grill" }),
      restaurant({ id: "l-2", slug: "karachi-kitchen", name: "Karachi Kitchen" }),
    ]);
    render(<SearchPage />);

    expect(
      await screen.findByRole("link", { name: "Al-Amir Grill" }),
    ).toHaveAttribute("href", "/restaurants/al-amir-grill");
    expect(
      screen.getByRole("link", { name: "Karachi Kitchen" }),
    ).toHaveAttribute("href", "/restaurants/karachi-kitchen");
  });

  it("cards request the SMALL thumbnail variant only — never the full-res original (sc-157)", async () => {
    searchMock.mockReturnValue([
      restaurant({
        id: "l-1",
        name: "Al-Amir Grill",
        imageThumbnailUrl: "/v1/listings/l-1/image?variant=thumbnail",
        imageUrl: "/v1/listings/l-1/image?variant=full",
      }),
    ]);
    render(<SearchPage />);

    const img = await screen.findByRole("img", { name: "Al-Amir Grill" });
    expect(img).toHaveAttribute(
      "src",
      "/v1/listings/l-1/image?variant=thumbnail",
    );
    // The full-res URL exists on the read-model but a card must not request it.
    expect(img).not.toHaveAttribute("src", "/v1/listings/l-1/image?variant=full");
    // Lazy: off-screen cards defer the transfer.
    expect(img).toHaveAttribute("loading", "lazy");
  });

  it("shows a verified badge on verified cards and an unverified tag otherwise", async () => {
    const future = new Date(Date.now() + 300 * 86_400_000).toISOString();
    searchMock.mockReturnValue([
      restaurant({
        id: "l-1",
        name: "Verified Spot",
        certificate: {
          certifier: "HFSAA",
          reviewedOn: new Date().toISOString(),
          expiresOn: future,
        },
      }),
      restaurant({ id: "l-2", slug: "plain", name: "Plain Spot" }),
    ]);
    render(<SearchPage />);

    const verified = await screen.findAllByText("Verified");
    expect(verified.length).toBe(2); // on-photo + card body badge
    expect(screen.getByText("Unverified")).toBeInTheDocument();
  });

  it("empty results render the quiet empty panel, not an error", async () => {
    searchMock.mockReturnValue([]);
    render(<SearchPage />);

    expect(await screen.findByText("Nothing matches yet")).toBeInTheDocument();
    // Never an error surface for zero results.
    expect(screen.queryByText(/couldn't load/i)).not.toBeInTheDocument();
  });

  it("browse chip filters through the data layer with aria-pressed", async () => {
    searchMock.mockImplementation(() => []);
    render(<SearchPage />);

    const handCut = await screen.findByRole("button", { name: "Hand-cut" });
    expect(handCut).toHaveAttribute("aria-pressed", "false");
    await userEvent.click(handCut);
    expect(searchMock).toHaveBeenCalledWith("", "HAND_CUT");
    await waitFor(() => expect(handCut).toHaveAttribute("aria-pressed", "true"));
  });
});