import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Restaurant } from "@/lib/listings/restaurants";
import SearchPage from "./page";

function restaurant(over: Partial<Restaurant>): Restaurant {
  return {
    id: "l-1",
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

vi.mock("@/lib/listings/data", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/listings/data")>();
  return { ...original, searchListings: vi.fn() };
});
import { searchListings } from "@/lib/listings/data";
const searchMock = vi.mocked(searchListings);

describe("SearchPage — search-first + browse chips (search-browse.md)", () => {
  beforeEach(() => {
    searchMock.mockReset();
    searchParamsGet.mockReset();
    searchParamsGet.mockReturnValue(null);
  });

  it("renders a result grid from the data layer and links each card to its detail page", async () => {
    searchMock.mockResolvedValue([
      restaurant({ id: "l-1", name: "Al-Amir Grill" }),
      restaurant({ id: "l-2", name: "Karachi Kitchen" }),
    ]);
    render(<SearchPage />);

    expect(
      await screen.findByRole("link", { name: "Al-Amir Grill" }),
    ).toHaveAttribute("href", "/restaurants/l-1");
    expect(
      screen.getByRole("link", { name: "Karachi Kitchen" }),
    ).toHaveAttribute("href", "/restaurants/l-2");
  });

  it("cards source the WIDEST imageSrcset title variant — never the full-res original (sc-183)", async () => {
    searchMock.mockResolvedValue([
      restaurant({
        id: "l-1",
        name: "Al-Amir Grill",
        imageThumbnailUrl: "/v1/listings/l-1/image?variant=thumbnail",
        imageSrcset: [
          { width: 400, url: "/v1/listings/l-1/image?variant=thumbnail" },
          { width: 768, url: "/v1/listings/l-1/image?variant=thumbnail_768" },
          { width: 1280, url: "/v1/listings/l-1/image?variant=thumbnail_1280" },
          { width: 1920, url: "/v1/listings/l-1/image?variant=thumbnail_1920" },
        ],
        imageUrl: "/v1/listings/l-1/image?variant=full",
      }),
    ]);
    render(<SearchPage />);

    const img = await screen.findByRole("img", { name: "Al-Amir Grill" });
    // next/image's srcset is downscaled from the widest title variant so every
    // srcset width is sharp (mobile 100vw @ DPR3 → desktop).
    expect(img).toHaveAttribute(
      "src",
      "/v1/listings/l-1/image?variant=thumbnail_1920",
    );
    // sizes="100vw" = founder's max-monitor-context baseline (sc-183).
    expect(img).toHaveAttribute("sizes", "100vw");
    // The full-res URL exists on the read-model but a card must not request it.
    expect(img).not.toHaveAttribute("src", "/v1/listings/l-1/image?variant=full");
    // Lazy: off-screen cards defer the transfer.
    expect(img).toHaveAttribute("loading", "lazy");
  });

  it("cards fall back to the small thumbnail src when the backend sends no srcset", async () => {
    searchMock.mockResolvedValue([
      restaurant({
        id: "l-1",
        name: "Al-Amir Grill",
        imageThumbnailUrl: "/v1/listings/l-1/image?variant=thumbnail",
      }),
    ]);
    render(<SearchPage />);

    const img = await screen.findByRole("img", { name: "Al-Amir Grill" });
    expect(img).toHaveAttribute(
      "src",
      "/v1/listings/l-1/image?variant=thumbnail",
    );
  });

  it("shows a verified badge on verified cards and an unverified tag otherwise", async () => {
    const future = new Date(Date.now() + 300 * 86_400_000).toISOString();
    searchMock.mockResolvedValue([
      restaurant({
        id: "l-1",
        name: "Verified Spot",
        certificate: {
          certifier: "HFSAA",
          reviewedOn: new Date().toISOString(),
          expiresOn: future,
        },
      }),
      restaurant({ id: "l-2", name: "Plain Spot" }),
    ]);
    render(<SearchPage />);

    const verified = await screen.findAllByText("Verified");
    expect(verified.length).toBe(2); // on-photo + card body badge
    expect(screen.getByText("Unverified")).toBeInTheDocument();
  });

  it("empty results render the quiet empty panel, not an error", async () => {
    searchMock.mockResolvedValue([]);
    render(<SearchPage />);

    expect(await screen.findByText("Nothing matches yet")).toBeInTheDocument();
    // Never an error surface for zero results.
    expect(screen.queryByText(/couldn't load/i)).not.toBeInTheDocument();
  });

  it("browse chip filters through the data layer with aria-pressed", async () => {
    searchMock.mockResolvedValue([]);
    render(<SearchPage />);

    const handCut = await screen.findByRole("button", { name: "Hand-cut" });
    expect(handCut).toHaveAttribute("aria-pressed", "false");
    await userEvent.click(handCut);
    expect(searchMock).toHaveBeenCalledWith("", "HAND_CUT");
    await waitFor(() => expect(handCut).toHaveAttribute("aria-pressed", "true"));
  });

  it("a legacy card still renders its stored thumbnail when the widest srcset variant 404s (sc-183 fallback)", async () => {
    searchMock.mockResolvedValue([
      restaurant({
        id: "l-1",
        name: "Al-Amir Grill",
        // Backend advertises the full width set for every listing, but a row
        // ingested before multi-width thumbnails stored only thumbnail(400)+full.
        imageThumbnailUrl: "/v1/listings/l-1/image?variant=thumbnail",
        imageSrcset: [
          { width: 400, url: "/v1/listings/l-1/image?variant=thumbnail" },
          { width: 768, url: "/v1/listings/l-1/image?variant=thumbnail_768" },
          { width: 1280, url: "/v1/listings/l-1/image?variant=thumbnail_1280" },
          { width: 1920, url: "/v1/listings/l-1/image?variant=thumbnail_1920" },
        ],
      }),
    ]);
    render(<SearchPage />);

    const img = await screen.findByRole("img", { name: "Al-Amir Grill" });
    // Card sources the widest variant first (sharp srcset for fully-ingested rows).
    expect(img).toHaveAttribute(
      "src",
      "/v1/listings/l-1/image?variant=thumbnail_1920",
    );

    // Legacy row: the widest variant was never stored → 404.
    act(() => fireEvent.error(img));

    // The card steps down to the guaranteed thumbnail — a valid photo always
    // renders (sc-157) instead of dropping to the blank placeholder.
    const fallbackImg = screen.getByRole("img", { name: "Al-Amir Grill" });
    expect(fallbackImg).toHaveAttribute(
      "src",
      "/v1/listings/l-1/image?variant=thumbnail",
    );
  });
});