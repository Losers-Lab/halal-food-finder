import type { Metadata } from "next";
import { Archivo, Archivo_Black, Space_Grotesk } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/lib/auth/AuthProvider";

/**
 * Stamps & Search typography (docs/design/tokens.md §2): Archivo for headings /
 * labels / wordmark, Space Grotesk for body & UI numerals. Self-hosted at build
 * time via next/font (no browser requests to Google); `variable` exposes them as
 * --font-display / --font-sans, which the @theme tokens in globals.css consume.
 */
const archivo = Archivo({
  subsets: ["latin"],
  weight: ["600", "700", "800"],
  display: "swap",
  variable: "--font-display",
});

const spaceGrotesk = Space_Grotesk({
  subsets: ["latin"],
  weight: ["400", "500", "700"],
  display: "swap",
  variable: "--font-sans",
});

const archivoBlack = Archivo_Black({
  subsets: ["latin"],
  weight: "400",
  display: "swap",
  variable: "--font-wordmark",
});

export const metadata: Metadata = {
  title: {
    default: "Tahir's List",
    template: "%s · Tahir's List",
  },
  description:
    "find halal food — search restaurant listings with granular hand-cut vs machine-cut filters and formally verified certifications.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${archivo.variable} ${spaceGrotesk.variable} ${archivoBlack.variable}`}>
      <body className="antialiased">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}