import type { Metadata, Viewport } from "next";
import { DM_Sans, Space_Grotesk } from "next/font/google";

import "@/app/globals.css";

const bodyFont = DM_Sans({
  subsets: ["latin"],
  variable: "--font-body"
});

const headingFont = Space_Grotesk({
  subsets: ["latin"],
  variable: "--font-heading"
});

export const metadata: Metadata = {
  title: "Force Gym Web",
  description: "PWA instalable con modo offline y sincronizacion contra el backend Force Gym.",
  manifest: "/manifest.webmanifest",
  applicationName: "Force Gym Web",
  appleWebApp: {
    capable: true,
    statusBarStyle: "default",
    title: "Force Gym"
  },
  icons: {
    icon: [
      {
        url: "/icon?size=192",
        sizes: "192x192",
        type: "image/png"
      },
      {
        url: "/icon?size=512",
        sizes: "512x512",
        type: "image/png"
      }
    ],
    apple: [
      {
        url: "/apple-icon",
        sizes: "180x180",
        type: "image/png"
      }
    ],
    shortcut: [
      {
        url: "/icon?size=192",
        type: "image/png"
      }
    ]
  }
};

export const viewport: Viewport = {
  themeColor: "#0f4c81",
  width: "device-width",
  initialScale: 1
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="es">
      <body className={`${bodyFont.variable} ${headingFont.variable}`}>{children}</body>
    </html>
  );
}