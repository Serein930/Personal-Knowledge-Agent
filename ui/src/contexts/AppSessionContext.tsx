import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { UserManager, WebStorageStateStore } from 'oidc-client-ts';
import { apiClient, configureApiAuthentication } from '../api/client';
import type { AuthTokenDto, CurrentUserDto, KnowledgeWorkspaceDto } from '../api/contracts';
import { env } from '../config/env';

const TOKEN_KEY = 'agentmind.access-token';
const TOKEN_EXPIRY_KEY = 'agentmind.access-token-expiry';
const WORKSPACE_KEY = 'agentmind.workspace-id';

interface LoginInput {
  username: string;
  password: string;
  captchaChallengeId: string;
  captchaCode: string;
}
interface RegisterInput extends LoginInput { displayName: string; email: string }

interface AppSessionValue {
  loading: boolean;
  authenticated: boolean;
  user?: CurrentUserDto;
  workspaces: KnowledgeWorkspaceDto[];
  workspaceId?: number;
  login: (input: LoginInput) => Promise<void>;
  loginWithOidc: () => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => void;
  selectWorkspace: (workspaceId: number) => void;
}

const AppSessionContext = createContext<AppSessionValue | null>(null);

function createOidcManager() {
  if (env.authMode !== 'oidc' || !env.oidcAuthority || !env.oidcClientId) return undefined;
  return new UserManager({
    authority: env.oidcAuthority,
    client_id: env.oidcClientId,
    redirect_uri: `${window.location.origin}${window.location.pathname}`,
    post_logout_redirect_uri: `${window.location.origin}${window.location.pathname}`,
    response_type: 'code',
    scope: env.oidcScope,
    automaticSilentRenew: true,
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
  });
}

const oidcManager = createOidcManager();

export function AppSessionProvider({ children }: { children: ReactNode }) {
  const [accessToken, setAccessToken] = useState(() => (
    env.authMode === 'local-jwt' ? localStorage.getItem(TOKEN_KEY) ?? undefined : undefined
  ));
  const tokenRef = useRef(accessToken);
  const [user, setUser] = useState<CurrentUserDto>();
  const [workspaces, setWorkspaces] = useState<KnowledgeWorkspaceDto[]>([]);
  const [workspaceId, setWorkspaceId] = useState<number>();
  const [loading, setLoading] = useState(true);

  const logout = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(TOKEN_EXPIRY_KEY);
    localStorage.removeItem(WORKSPACE_KEY);
    tokenRef.current = undefined;
    setAccessToken(undefined);
    setUser(undefined);
    setWorkspaces([]);
    setWorkspaceId(undefined);
    if (env.authMode === 'oidc' && oidcManager) void oidcManager.signoutRedirect();
  }, []);

  useEffect(() => { tokenRef.current = accessToken; }, [accessToken]);
  useEffect(() => {
    configureApiAuthentication(() => tokenRef.current, logout);
  }, [logout]);

  const loadSession = useCallback(async (defaultWorkspaceId?: number) => {
    const [currentUser, availableWorkspaces] = await Promise.all([
      apiClient.get<CurrentUserDto>('/v1/users/me'),
      apiClient.get<KnowledgeWorkspaceDto[]>('/v1/workspaces'),
    ]);
    const savedWorkspaceId = Number(localStorage.getItem(WORKSPACE_KEY));
    const selected = availableWorkspaces.find((workspace) => workspace.id === savedWorkspaceId)
      ?? availableWorkspaces.find((workspace) => workspace.id === defaultWorkspaceId)
      ?? availableWorkspaces[0];
    setUser(currentUser);
    setWorkspaces(availableWorkspaces);
    setWorkspaceId(selected?.id);
    if (selected) localStorage.setItem(WORKSPACE_KEY, String(selected.id));
  }, []);

  const acceptToken = useCallback(async (token: AuthTokenDto) => {
    localStorage.setItem(TOKEN_KEY, token.accessToken);
    localStorage.setItem(TOKEN_EXPIRY_KEY, token.expiresAt);
    tokenRef.current = token.accessToken;
    setAccessToken(token.accessToken);
    await loadSession(token.defaultWorkspaceId);
  }, [loadSession]);

  const login = useCallback(async (input: LoginInput) => {
    await acceptToken(await apiClient.post<AuthTokenDto>('/v1/auth/login', input));
  }, [acceptToken]);

  const loginWithOidc = useCallback(async () => {
    if (!oidcManager) throw new Error('OIDC 地址或客户端编号尚未配置');
    await oidcManager.signinRedirect();
  }, []);

  const register = useCallback(async (input: RegisterInput) => {
    await acceptToken(await apiClient.post<AuthTokenDto>('/v1/auth/register', input));
  }, [acceptToken]);

  const selectWorkspace = useCallback((nextWorkspaceId: number) => {
    if (!workspaces.some((workspace) => workspace.id === nextWorkspaceId)) return;
    localStorage.setItem(WORKSPACE_KEY, String(nextWorkspaceId));
    setWorkspaceId(nextWorkspaceId);
  }, [workspaces]);

  useEffect(() => {
    let cancelled = false;
    const initialize = async () => {
      try {
        if (env.authMode === 'oidc') {
          if (!oidcManager) return;
          if (new URLSearchParams(window.location.search).has('code')) {
            await oidcManager.signinRedirectCallback();
            window.history.replaceState({}, document.title, window.location.pathname);
          }
          const oidcUser = await oidcManager.getUser();
          if (!oidcUser || oidcUser.expired) return;
          tokenRef.current = oidcUser.access_token;
          setAccessToken(oidcUser.access_token);
          await loadSession();
          return;
        }
        if (env.authMode !== 'disabled' && !tokenRef.current) return;
        await loadSession();
      } catch {
        if (env.authMode !== 'disabled') logout();
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    void initialize();
    return () => { cancelled = true; };
  }, [loadSession, logout]);

  useEffect(() => {
    if (env.authMode !== 'local-jwt' || !accessToken) return undefined;
    const expiry = Date.parse(localStorage.getItem(TOKEN_EXPIRY_KEY) ?? '');
    if (!Number.isFinite(expiry)) return undefined;
    const timer = window.setTimeout(() => {
      void apiClient.post<AuthTokenDto>('/v1/auth/refresh', {})
        .then(acceptToken)
        .catch(logout);
    }, Math.max(1000, expiry - Date.now() - 60_000));
    return () => window.clearTimeout(timer);
  }, [acceptToken, accessToken, logout]);

  const value = useMemo<AppSessionValue>(() => ({
    loading,
    authenticated: env.authMode === 'disabled' || Boolean(user),
    user,
    workspaces,
    workspaceId,
    login,
    loginWithOidc,
    register,
    logout,
    selectWorkspace,
  }), [loading, login, loginWithOidc, logout, register, selectWorkspace, user, workspaceId, workspaces]);

  return <AppSessionContext.Provider value={value}>{children}</AppSessionContext.Provider>;
}

export function useAppSession() {
  const value = useContext(AppSessionContext);
  if (!value) throw new Error('useAppSession 必须在 AppSessionProvider 内使用');
  return value;
}
