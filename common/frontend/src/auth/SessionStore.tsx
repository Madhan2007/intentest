/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Provides kernel session state and auth actions to the React tree.
 */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { isAbortError } from "@kernel/api/abortError";
import { httpOpzhub } from "@kernel/api/http-client";
import { ApiError } from "@kernel/api/errors";
import type { AccessMatrix, SessionState, SessionUser } from "@kernel/auth/session";

const IDENTITY_SESSION_PATH = "/identity/session";
const IDENTITY_LOGIN_PATH = "/identity/login";
const IDENTITY_LOGOUT_PATH = "/identity/logout";
const SESSION_REQUIRED_MESSAGE = "useSession must be used inside <SessionProvider>";

interface SessionContextValue extends SessionState {
  loading: boolean;
  login: (username: string, password: string, requestInit?: RequestInit) => Promise<void>;
  logout: () => Promise<void>;
}

interface SessionActions {
  login: SessionContextValue["login"];
  logout: SessionContextValue["logout"];
}

const SessionStateContext = createContext<(SessionState & { loading: boolean }) | null>(null);
const SessionActionsContext = createContext<SessionActions | null>(null);

interface SessionResponse {
  user: SessionUser;
  a: AccessMatrix;
}

interface SessionProviderProps {
  children: ReactNode;
}

/**
 * Holds session user, access matrix, and login/logout actions.
 *
 * @param props Provider children rendered with access to session context
 * @returns The session context provider wrapper
 */
export function SessionProvider({ children }: SessionProviderProps) {
  const [user, setUser] = useState<SessionUser | null>(null);
  const [matrix, setMatrix] = useState<AccessMatrix>({});
  const [loading, setLoading] = useState(true);
  const isProviderMountedRef = useRef(true);
  const logoutInFlightRef = useRef(false);

  useEffect(() => {
    isProviderMountedRef.current = true;
    const abortController = new AbortController();

    httpOpzhub
      .get<SessionResponse>(IDENTITY_SESSION_PATH, { signal: abortController.signal })
      .then((data) => {
        if (!isProviderMountedRef.current) {
          return;
        }
        setUser(data.user);
        setMatrix(data.a ?? {});
      })
      .catch((error: unknown) => {
        if (!isProviderMountedRef.current || isAbortError(error)) {
          return;
        }
        setUser(null);
      })
      .finally(() => {
        if (isProviderMountedRef.current) {
          setLoading(false);
        }
      });

    return () => {
      isProviderMountedRef.current = false;
      abortController.abort();
    };
  }, []);

  const login = useCallback(
    async (username: string, password: string, requestInit?: RequestInit) => {
      const data = await httpOpzhub.post<SessionResponse>(
        IDENTITY_LOGIN_PATH,
        { username, password },
        requestInit
      );
      if (!isProviderMountedRef.current) {
        return;
      }
      setUser(data.user);
      setMatrix(data.a ?? {});
    },
    []
  );

  const logout = useCallback(async () => {
    if (logoutInFlightRef.current) {
      return;
    }
    logoutInFlightRef.current = true;
    try {
      await httpOpzhub.post(IDENTITY_LOGOUT_PATH);
    } finally {
      logoutInFlightRef.current = false;
      if (isProviderMountedRef.current) {
        setUser(null);
        setMatrix({});
      }
    }
  }, []);

  const sessionState = useMemo(
    () => ({ user, matrix, loading }),
    [user, matrix, loading]
  );
  const sessionActions = useMemo(() => ({ login, logout }), [login, logout]);

  return (
    <SessionActionsContext.Provider value={sessionActions}>
      <SessionStateContext.Provider value={sessionState}>{children}</SessionStateContext.Provider>
    </SessionActionsContext.Provider>
  );
}

function requireSessionState(): SessionState & { loading: boolean } {
  const sessionState = useContext(SessionStateContext);
  if (!sessionState) {
    throw new Error(SESSION_REQUIRED_MESSAGE);
  }
  return sessionState;
}

function requireSessionActions(): SessionActions {
  const sessionActions = useContext(SessionActionsContext);
  if (!sessionActions) {
    throw new Error(SESSION_REQUIRED_MESSAGE);
  }
  return sessionActions;
}

/**
 * Reads the current session context.
 *
 * @returns The active session state and auth actions
 */
export function useSession(): SessionContextValue {
  const sessionState = requireSessionState();
  const sessionActions = requireSessionActions();
  return useMemo(
    () => ({ ...sessionState, ...sessionActions }),
    [sessionState, sessionActions]
  );
}

/**
 * Reads only the signed-in user so chrome can skip matrix-driven rerenders.
 *
 * @returns The current session user, or null when signed out
 */
export function useSessionUser(): SessionUser | null {
  return requireSessionState().user;
}

/**
 * Reads stable login/logout actions.
 *
 * @returns Session mutation helpers
 */
export function useSessionActions(): SessionActions {
  return requireSessionActions();
}

export { ApiError };
