"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useSyncExternalStore,
} from "react";
import type { AuthResponse } from "@/lib/api/client";

const SESSION_KEY = "hff.session.v1";

export type Session = {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresAt: number; // epoch ms — derived from expiresIn at issue time
  accountId: string;
  role: string;
  email: string;
};

type Listener = () => void;

/**
 * Minimal external store for the auth session backed by localStorage.
 * Kept outside React state so hydration can't cascade renders and so any
 * component can read the session synchronously (useSyncExternalStore).
 */
const listeners = new Set<Listener>();

function readSession(): Session | null {
  if (typeof window === "undefined") return null;
  try {
    const raw = window.localStorage.getItem(SESSION_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Session;
    if (!parsed.accessToken || !parsed.refreshToken) return null;
    return parsed;
  } catch {
    return null;
  }
}

/** Parsed session cache — getSnapshot must return a stable reference. */
let cached: Session | null | undefined;
let cacheValid = false;

function getSnapshot(): Session | null {
  if (typeof window === "undefined") return null;
  if (cacheValid) return cached ?? null;
  cached = readSession();
  cacheValid = true;
  return cached ?? null;
}

function subscribe(listener: Listener) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function writeSession(next: Session | null) {
  if (typeof window === "undefined") return;
  try {
    if (next) window.localStorage.setItem(SESSION_KEY, JSON.stringify(next));
    else window.localStorage.removeItem(SESSION_KEY);
  } finally {
    cached = next;
    cacheValid = true;
    for (const l of listeners) l();
  }
}

type AuthContextValue = {
  session: Session | null;
  /** Store a token pair + account identity, marking the user logged in. */
  signIn: (auth: AuthResponse, email: string) => void;
  signOut: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const session = useSyncExternalStore(subscribe, getSnapshot, () => null);

  const signIn = useCallback((auth: AuthResponse, email: string) => {
    const next: Session = {
      accessToken: auth.accessToken,
      refreshToken: auth.refreshToken,
      tokenType: auth.tokenType,
      expiresAt: Date.now() + auth.expiresIn * 1000,
      accountId: auth.accountId,
      role: auth.role,
      email,
    };
    writeSession(next);
  }, []);

  const signOut = useCallback(() => {
    writeSession(null);
  }, []);

  const value = useMemo(
    () => ({ session, signIn, signOut }),
    [session, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within <AuthProvider>");
  return ctx;
}