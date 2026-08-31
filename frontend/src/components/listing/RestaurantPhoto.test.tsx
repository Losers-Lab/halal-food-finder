import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { RestaurantPhoto } from "./RestaurantPhoto";

/**
 * sc-157 — RestaurantPhoto is the variant-selecting image renderer. Cards pass
 * the SMALL thumbnail URL; the detail hero passes the FULL URL eager. The main
 * behaviours to lock in:
 *  - the URL is used verbatim as the `<img>` src (frontend never rewrites it);
 *  - `sizes`/`alt` are forwarded for correct srcset + accessibility;
 *  - cards lazy-load, the detail hero loads eager;
 *  - a listing without an ingested photo renders the quiet placeholder (no img);
 *  - a FAILED load (onError) renders the SAME placeholder, never a broken-image
 *    icon (sc-171 founder directive / detail-page.md §1.1).
 */
describe("RestaurantPhoto", () => {
  it("renders the provided variant URL as the img src with the restaurant name as alt", () => {
    render(
      <RestaurantPhoto
        src="/v1/listings/l-1/image?variant=thumbnail"
        alt="Al-Amir Grill"
        sizes="25vw"
      />,
    );

    const img = screen.getByRole("img", { name: "Al-Amir Grill" });
    expect(img).toHaveAttribute(
      "src",
      "/v1/listings/l-1/image?variant=thumbnail",
    );
    // sizes forwarded so next/image emits a responsive srcset (not just 1x/2x).
    expect(img).toHaveAttribute("sizes", "25vw");
  });

  it("lazy-loads by default (cards) and can be forced eager (detail hero)", () => {
    const { rerender } = render(
      <RestaurantPhoto src="/v1/listings/l-1/image?variant=thumbnail" alt="a" sizes="100vw" />,
    );
    expect(screen.getByRole("img", { name: "a" })).toHaveAttribute("loading", "lazy");

    rerender(
      <RestaurantPhoto src="/v1/listings/l-1/image?variant=full" alt="a" sizes="100vw" eager />,
    );
    expect(screen.getByRole("img", { name: "a" })).toHaveAttribute("loading", "eager");
  });

  it("renders the quiet placeholder (no image) when no photo is ingested yet", () => {
    const { container } = render(<RestaurantPhoto alt="No photo yet" sizes="100vw" />);

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(container.querySelector("svg")).toBeInTheDocument();
  });

  it("swaps to the same stamp placeholder when the image fails to load (no broken-image icon)", () => {
    const { container } = render(
      <RestaurantPhoto
        src="/v1/listings/l-1/image?variant=thumbnail"
        alt="Al-Amir Grill"
        sizes="25vw"
      />,
    );

    const img = screen.getByRole("img", { name: "Al-Amir Grill" });
    act(() => fireEvent.error(img));

    // The failed <img> is gone; the reusable kraft-placeholder is shown instead.
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(container.querySelector("svg")).toBeInTheDocument();
  });
});