import withPWA from "@ducanh2912/next-pwa";
import type { NextConfig } from "next";

const backendBase = (process.env.FORCE_GYM_API_BASE ?? "http://localhost:8080/api").replace(/\/$/, "");

const nextConfig: NextConfig = {
  output: "standalone",
  reactStrictMode: true,
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendBase}/:path*`
      }
    ];
  }
};

export default withPWA({
  dest: "public",
  disable: process.env.NODE_ENV === "development",
  register: true,
  fallbacks: {
    document: "/offline"
  }
})(nextConfig);