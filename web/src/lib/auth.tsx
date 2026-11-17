import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { ApiError, auth, SIGNED_OUT_EVENT } from "./api";
import type { AuthConfig, Me } from "./types";

interface AuthState {
  /** undefined while checking, null when signed out. */
  user: Me | null | undefined;
  /** The server couldn't be reached (laptop asleep, no network). */
  unreachable: boolean;
  retry(): void;
  config: AuthConfig;
  login(email: string, password: string): Promise<void>;
  signup(name: string, email: string, password: string): Promise<void>;
  demo(): Promise<void>;
  logout(): Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null | undefined>(undefined);
  const [config, setConfig] = useState<AuthConfig>({ demo: false });
  const [unreachable, setUnreachable] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    setUnreachable(false);
    // /config also hands out the CSRF cookie before the first POST.
    auth.config().then(setConfig, () => undefined).finally(() => {
      auth.me().then(setUser, (e) => {
        if (e instanceof ApiError && e.status === 401) setUser(null);
        else setUnreachable(true);
      });
    });
  }, [attempt]);

  useEffect(() => {
    const onSignedOut = () => setUser(null);
    window.addEventListener(SIGNED_OUT_EVENT, onSignedOut);
    return () => window.removeEventListener(SIGNED_OUT_EVENT, onSignedOut);
  }, []);

  const value: AuthState = {
    user,
    unreachable,
    retry: useCallback(() => setAttempt((n) => n + 1), []),
    config,
    login: useCallback(async (email, password) => setUser(await auth.login(email, password)), []),
    signup: useCallback(async (name, email, password) => setUser(await auth.signup(name, email, password)), []),
    demo: useCallback(async () => setUser(await auth.demo()), []),
    logout: useCallback(async () => {
      await auth.logout().catch(() => undefined);
      setUser(null);
    }, []),
  };
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth outside AuthProvider");
  return ctx;
}

/** The signed-in user. Only use under the signed-in part of the app. */
export function useUser(): Me {
  const { user } = useAuth();
  if (!user) throw new Error("useUser while signed out");
  return user;
}
