import "@testing-library/jest-dom/vitest";
import { createElement } from "react";
import { vi } from "vitest";

/**
 * jsdom has no real image pipeline, so `next/image` is mocked to render a plain
 * `<img>` element. We keep the props tests actually assert on (src, alt, sizes,
 * loading) and strip the layout-only props (`fill`, `priority`) so they don't
 * leak onto the DOM as invalid attributes. Tests for RestaurantPhoto assert
 * against the real output of this mock.
 */
vi.mock("next/image", () => ({
  __esModule: true,
  default: (props: Record<string, unknown>) => {
    const { fill, priority, ...imgProps } = props;
    void fill;
    void priority;
    return createElement("img", imgProps);
  },
}));