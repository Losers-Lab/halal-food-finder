import type { NextConfig } from "next";

// Security headers applied to every response (all routes).
//
// Content-Security-Policy notes:
// - `script-src`/`style-src` keep `'unsafe-inline'` because Next.js injects
//   inline bootstrap scripts (the RSC payload / hydration runtime) and inline
//   styles. Removing it would require a nonce-based policy, which Next only
//   supports by forcing every page into dynamic rendering via the Proxy
//   (middleware) file plus `connection()`. That is tracked as follow-up
//   security hardening, not done here (task: "do not over-engineer").
// - `'unsafe-eval'` is required only by `next dev` (React's dev-mode error
//   stack reconstruction uses eval) and is omitted from production builds.
// - `frame-ancestors 'none'` blocks clickjacking; it is kept alongside the
//   legacy `X-Frame-Options: DENY` header for older browsers.
const contentSecurityPolicy = [
  "default-src 'self'",
  "script-src 'self' 'unsafe-inline'" +
    (process.env.NODE_ENV === "development" ? " 'unsafe-eval'" : ""),
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: blob:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'none'",
  "upgrade-insecure-requests",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: contentSecurityPolicy },
  {
    key: "Strict-Transport-Security",
    value: "max-age=31536000; includeSubDomains",
  },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
];

// Same-origin image proxy (see rewrites below): restaurant photos are served
// from /v1/listings/{id}/image?variant=thumbnail|full through the same-origin
// /v1 proxy. next/image's optimizer runs these through /_next/image and, since
// Next 16, requires an explicit `images.localPatterns` allowlist for local
// URLs (especially ones with a query string) or it returns 400. Path is
// scoped to the /v1 listing photo proxy; `search` is pinned to the two exact
// variants the backend emits (no wildcard = no enumeration of arbitrary query
// strings).
const imageLocalPatterns = [
  { pathname: "/v1/**", search: "?variant=thumbnail" },
  { pathname: "/v1/**", search: "?variant=full" },
];

const nextConfig: NextConfig = {
  images: {
    localPatterns: imageLocalPatterns,
  },
  async headers() {
    return [
      {
        source: "/:path*",
        headers: securityHeaders,
      },
    ];
  },
  async rewrites() {
    // Proxy API calls to the backend so the browser stays same-origin
    // (avoids requiring backend CORS config). Default targets the local dev
    // backend; override with the API_PROXY_TARGET env at build/runtime.
    const target = process.env.API_PROXY_TARGET ?? "http://127.0.0.1:8080";
    return [
      {
        source: "/v1/:path*",
        destination: `${target}/v1/:path*`,
      },
    ];
  },
};

export default nextConfig;
