function readPositiveNumber(value: string | undefined, fallback: number): number {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

export const env = {
  // 后端接口地址和认证模式来自构建环境，业务页面不再读取固定知识空间编号。
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081/api',
  authMode: import.meta.env.VITE_AUTH_MODE ?? 'disabled',
  oidcAuthority: import.meta.env.VITE_OIDC_AUTHORITY ?? '',
  oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? '',
  oidcScope: import.meta.env.VITE_OIDC_SCOPE ?? 'openid profile email',
  // 只用于提交前的用户体验校验；最终安全边界仍由后端配置控制。
  maxUploadSizeMb: readPositiveNumber(import.meta.env.VITE_MAX_UPLOAD_SIZE_MB, 150),
};
