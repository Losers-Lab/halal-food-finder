import type { NextConfig } from "next";

const nextConfig: NextConfig = {
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
