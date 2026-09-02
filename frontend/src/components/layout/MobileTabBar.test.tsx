import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";
import { MobileTabBar } from "./MobileTabBar";

vi.mock("next/navigation", () => ({
  usePathname: () => "/search",
}));
vi.mock("next/link", () => ({
  __esModule: true,
  default: ({ href, children }: { href: string; children: ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

describe("MobileTabBar (docs/design/mobile-bottom-tab.md)", () => {
  it("is hidden on desktop (lg+) so it never overlays desktop layouts (sc-185 Item 4)", () => {
    render(<MobileTabBar />);
    const nav = screen.getByRole("navigation", { name: "Primary" });
    // lg:hidden hides ≥ lg while leaving it visible on mobile; a bare `hidden`
    // would hide it everywhere and is a regression.
    expect(nav).toHaveClass("lg:hidden");
    expect(nav).not.toHaveClass("hidden");
  });

  it("renders the four tabs in fixed order", () => {
    render(<MobileTabBar />);
    expect(screen.getByRole("link", { name: "Search" })).toHaveAttribute(
      "href",
      "/search",
    );
    expect(screen.getByRole("link", { name: "Add" })).toHaveAttribute(
      "href",
      "/add-listing",
    );
    expect(screen.getByRole("link", { name: "Saved" })).toHaveAttribute(
      "href",
      "/saved",
    );
    expect(screen.getByRole("link", { name: "Account" })).toHaveAttribute(
      "href",
      "/account",
    );
  });
});