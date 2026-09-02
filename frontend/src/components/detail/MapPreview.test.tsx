import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { MapPreview } from "./MapPreview";

describe("MapPreview — sc-187 embedded map preview with location pin", () => {
  it("renders a Google Maps embed iframe pinned at the listing coordinates (no key)", () => {
    render(
      <MapPreview lat={40.6916} lng={-73.9788} restaurantName="Al-Amir Grill" />,
    );
    const frame = screen.getByTitle(
      "Map showing the location of Al-Amir Grill",
    );
    expect(frame).toHaveAttribute(
      "src",
      "https://www.google.com/maps?q=40.6916,-73.9788&z=16&output=embed",
    );
    // Below the fold — don't load until scrolled into view.
    expect(frame).toHaveAttribute("loading", "lazy");
  });

  it("renders an accessible title so screen readers know the frame's purpose", () => {
    render(<MapPreview lat={1} lng={2} restaurantName="Karachi Kitchen" />);
    expect(
      screen.getByTitle("Map showing the location of Karachi Kitchen"),
    ).toBeInTheDocument();
  });

  it("renders nothing when a coordinate is not finite (broken read data, never a dead frame)", () => {
    const { container } = render(
      <MapPreview lat={Number.NaN} lng={-73.9788} restaurantName="X" />,
    );
    expect(container.querySelector("iframe")).toBeNull();
    expect(container.firstChild).toBeNull();
  });
});