import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Halal Food Finder",
  description:
    "Find halal food with granular hand-cut vs machine-cut filtering and formally verified certifications.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="antialiased">{children}</body>
    </html>
  );
}
