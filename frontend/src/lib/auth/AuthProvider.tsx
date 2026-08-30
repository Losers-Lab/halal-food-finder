"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useSyncExternalStore,
} from "react";
import type { AuthResponse } from "@/lib/api/client";
import {
  getSnapshot,
  restore,
  signIn as storeSignIn,
  signOut as storeSignOut,
  subscribe,
  type Session,
  type SessionView,
} from "@/lib/auth/session";

type AuthContextValue = {
  session: Session | null;
  /** True while a persisted session is being re-materialized into an access token. */
  restoring: boolean;
  /** Store a fresh access token + identity, marking the user logged in. */
  signIn: (auth: AuthResponse, email: string) => void;
  signOut: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  // Fallback for server render: no session and not restoring until hydration.
  const { session, restoring } = useSyncExternalStore(
    subscribe,
    getSnapshot,
    () => ({ session: null, restoring: false }) satisfies SessionView,
  );

  // On boot, re-materialize a persisted session via the refresh cookie.
  useEffect(() => {
    restore();
  }, []);

  const signIn = useCallback((auth: AuthResponse, email: string) => {
    storeSignIn(auth, email);
  }, []);

  const signOut = useCallback(() => {
    storeSignOut();
  }, []);

  const value = useMemo(
    () => ({ session, restoring, signIn, signOut }),
    [session, restoring, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within <AuthProvider>");
  return ctx;
}