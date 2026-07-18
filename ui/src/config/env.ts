export const env = {
  // 后端接口地址和认证模式来自构建环境，业务页面不再读取固定知识空间编号。
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081/api',
  authMode: import.meta.env.VITE_AUTH_MODE ?? 'disabled',
  oidcAuthority: import.meta.env.VITE_OIDC_AUTHORITY ?? '',
  oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? '',
  oidcScope: import.meta.env.VITE_OIDC_SCOPE ?? 'openid profile email',
};
