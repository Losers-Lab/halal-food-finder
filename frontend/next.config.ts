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

const nextConfig: NextConfig = {
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
