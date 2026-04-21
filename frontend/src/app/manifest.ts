import type { MetadataRoute } from "next";

export default function manifest(): MetadataRoute.Manifest {
  return {
    id: "/",
    name: "Force Gym Web",
    short_name: "Force Gym",
    description: "PWA del panel Force Gym con instalacion local, modo offline y sincronizacion diferida.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    display_override: ["window-controls-overlay", "standalone"],
    orientation: "portrait",
    background_color: "#f5efe4",
    theme_color: "#0f4c81",
    icons: [
      {
        src: "/icon?size=192",
        sizes: "192x192",
        type: "image/png"
      },
      {
        src: "/icon?size=192",
        sizes: "192x192",
        type: "image/png",
        purpose: "maskable"
      },
      {
        src: "/icon?size=512",
        sizes: "512x512",
        type: "image/png"
      },
      {
        src: "/icon?size=512",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable"
      },
      {
        src: "/apple-icon",
        sizes: "180x180",
        type: "image/png"
      }
    ]
  };
}