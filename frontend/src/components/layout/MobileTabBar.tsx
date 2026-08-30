"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

type Tab = {
  label: string;
  href: string;
  icon: React.ReactNode;
};

function SearchIcon() {
  return (
    <svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.8-3.8" />
    </svg>
  );
}
function AddIcon() {
  return (
    <svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <rect x="4" y="4" width="16" height="16" rx="3" />
      <path d="M12 9v6M9 12h6" />
    </svg>
  );
}
function SavedIcon() {
  return (
    <svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round">
      <path d="M6 4h12v16l-6-4-6 4Z" />
    </svg>
  );
}
function AccountIcon() {
  return (
    <svg aria-hidden="true" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20a8 8 0 0 1 16 0" />
    </svg>
  );
}

const TABS: Tab[] = [
  { label: "Search", href: "/search", icon: <SearchIcon /> },
  { label: "Add", href: "/add-listing", icon: <AddIcon /> },
  { label: "Saved", href: "/saved", icon: <SavedIcon /> },
  { label: "Account", href: "/account", icon: <AccountIcon /> },
];

/**
 * Mobile bottom tab bar (≤768px) — docs/design/mobile-bottom-tab.md.
 * Fixed to viewport bottom, four equal tabs, always-visible labels. Tab is a
 * real <a aria-current="page" when active>. Rendered only ≤ md by the layout.
 * NOTE: /saved and /account routes are follow-up cards; they don't exist yet.
 */
export function MobileTabBar() {
  const pathname = usePathname();
  const active = (href: string) =>
    href === "/search"
      ? pathname === "/search" || pathname === "/"
      : pathname === href;

  return (
    <nav
      aria-label="Primary"
      className="fixed inset-x-0 bottom-0 z-20 border-t border-kraft-300 bg-ink-0 pb-[env(safe-area-inset-bottom)]"
    >
      <div className="grid h-14 grid-cols-4">
        {TABS.map((tab) => {
          const isActive = active(tab.href);
          return (
            <Link
              key={tab.href}
              href={tab.href}
              aria-current={isActive ? "page" : undefined}
              className={`flex min-h-11 flex-col items-center justify-center gap-0.5 focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-brand-500 ${
                isActive ? "text-brand-500" : "text-ink-500"
              }`}
            >
              {tab.icon}
              <span className="text-small">{tab.label}</span>
              <span
                aria-hidden="true"
                className={`h-[3px] w-8 rounded-full ${isActive ? "bg-brand-500" : "bg-transparent"}`}
              />
            </Link>
          );
        })}
      </div>
    </nav>
  );
}