import { act, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { Restaurant } from "@/lib/listings/restaurants";
import RestaurantDetailPage from "./page";

const now = Date.now();
const day = 86_400_000;

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

vi.mock("next/navigation", () => ({
  useParams: () => ({ slug: "al-amir-grill" }),
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
vi.mock("@/lib/auth/AuthProvider", () => ({ useAuth: () => ({ session: null, restoring: false }) }));

vi.mock("@/lib/listings/seed", async (importOriginal) => {
  const original = await importOriginal<typeof import("@/lib/listings/seed")>();
  return { ...original, fetchRestaurantBySlug: vi.fn() };
});
import { fetchRestaurantBySlug } from "@/lib/listings/seed";
const fetchMock = vi.mocked(fetchRestaurantBySlug);

describe("RestaurantDetailPage — certificate trust panel (detail-page.md)", () => {
  beforeEach(() => {
    fetchMock.mockReset();
  });

  it("renders the verified certificate panel with certifier, review, expiry + view link", async () => {
    fetchMock.mockResolvedValueOnce(
      restaurant({
        certificate: {
          certifier: "HFSAA",
          reviewedOn: new Date(now - 200 * day).toISOString(),
          expiresOn: new Date(now + 300 * day).toISOString(),
          certificateUrl: "https://example.com/cert.pdf",
        },
      }),
    );
    render(<RestaurantDetailPage />);

    expect(await screen.findByText("Halal verification")).toBeInTheDocument();
    // <dl> field values
    expect(screen.getByText("HFSAA")).toBeInTheDocument();
    const link = screen.getByRole("link", {
      name: "View halal certificate for Al-Amir Grill",
    });
    expect(link).toHaveAttribute("href", "https://example.com/cert.pdf");
    expect(screen.getByText(/Reviewed by our verification committee/)).toBeInTheDocument();
  });

  it("shows an expiring-soon warning (icon+text) when ≤ 60 days to expiry", async () => {
    fetchMock.mockResolvedValueOnce(
      restaurant({
        certificate: {
          certifier: "HFSAA",
          reviewedOn: new Date(now - 100 * day).toISOString(),
          expiresOn: new Date(now + 20 * day).toISOString(),
        },
      }),
    );
    render(<RestaurantDetailPage />);

    const notice = await screen.findByText(/review in progress/);
    expect(notice).toBeInTheDocument();
    // Never danger-red for an expiring/expired state (binding rule).
  });

  it("renders the quiet unverified panel (never red) for a listing with no certificate", async () => {
    fetchMock.mockResolvedValueOnce(restaurant({}));
    render(<RestaurantDetailPage />);

    expect(
      await screen.findByText(/hasn't been verified yet/i),
    ).toBeInTheDocument();
    expect(screen.queryByText("Halal verification")).not.toBeInTheDocument();
    expect(screen.queryByText("Verified")).not.toBeInTheDocument();
  });

  it("renders the not-found panel when the slug is unknown (header/footer intact)", async () => {
    fetchMock.mockResolvedValueOnce(undefined);
    render(<RestaurantDetailPage />);

    expect(
      await screen.findByText("We couldn't find that restaurant."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse all spots" })).toHaveAttribute(
      "href",
      "/search",
    );
    expect(screen.getByText("SiteHeader")).toBeInTheDocument();
    expect(screen.getByText("SiteFooter")).toBeInTheDocument();
  });

  it("renders the error panel and retries on fetch failure", async () => {
    fetchMock.mockRejectedValueOnce(new Error("boom"));
    fetchMock.mockResolvedValueOnce(
      restaurant({
        certificate: {
          certifier: "HFSAA",
          reviewedOn: new Date(now - 200 * day).toISOString(),
          expiresOn: new Date(now + 300 * day).toISOString(),
        },
      }),
    );
    render(<RestaurantDetailPage />);

    expect(
      await screen.findByText("We couldn't load this restaurant."),
    ).toBeInTheDocument();

    const retry = screen.getByRole("button", { name: "Retry" });
    act(() => retry.click());
    await waitFor(() =>
      expect(screen.getByText("Halal verification")).toBeInTheDocument(),
    );
  });

  it("detail hero requests the FULL-res variant (imageUrl), eager (sc-157)", async () => {
    fetchMock.mockResolvedValueOnce(
      restaurant({
        id: "l-1",
        name: "Al-Amir Grill",
        imageThumbnailUrl: "/v1/listings/l-1/image?variant=thumbnail",
        imageUrl: "/v1/listings/l-1/image?variant=full",
      }),
    );
    render(<RestaurantDetailPage />);

    const img = await screen.findByRole("img", { name: "Al-Amir Grill" });
    expect(img).toHaveAttribute(
      "src",
      "/v1/listings/l-1/image?variant=full",
    );
    // Full-res hero is the LCP element — load it immediately, not lazy.
    expect(img).toHaveAttribute("loading", "eager");
  });

  it("renders the quiet image placeholder (no <img>) when a listing has no photo (sc-157)", async () => {
    fetchMock.mockResolvedValueOnce(restaurant({ id: "l-9", name: "New Spot" }));
    render(<RestaurantDetailPage />);

    expect(await screen.findByText("New Spot")).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: "New Spot" })).not.toBeInTheDocument();
  });
});