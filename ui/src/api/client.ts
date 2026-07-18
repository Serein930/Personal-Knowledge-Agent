import { env } from '../config/env';

export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
}

export class ApiClientError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
  }
}

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
}

let accessTokenProvider: () => string | undefined = () => undefined;
let unauthorizedHandler: () => void = () => undefined;

/** 由会话上下文注入认证能力，API Client 本身不依赖 React。 */
export function configureApiAuthentication(
  getAccessToken: () => string | undefined,
  onUnauthorized: () => void,
) {
  accessTokenProvider = getAccessToken;
  unauthorizedHandler = onUnauthorized;
}

/** 供 POST SSE 等无法复用 JSON 请求函数的调用安全复用认证头。 */
export function getApiAuthenticationHeaders(): Record<string, string> {
  const accessToken = accessTokenProvider();
  return accessToken ? { Authorization: `Bearer ${accessToken}` } : {};
}

export function handleApiUnauthorized(status: number) {
  if (status === 401) unauthorizedHandler();
}

export function buildApiUrl(path: string) {
  const normalizedBase = env.apiBaseUrl.replace(/\/$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const accessToken = accessTokenProvider();
  const response = await fetch(buildApiUrl(path), {
    ...options,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...options.headers,
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    if (response.status === 401 && !path.includes('/auth/')) unauthorizedHandler();
    const payload = await response.json().catch(() => null) as ApiResponse<unknown> | null;
    throw new ApiClientError(payload?.message ?? `请求失败：${response.status}`, response.status);
  }

  const payload = (await response.json()) as ApiResponse<T>;
  return payload.data;
}

export async function upload<T>(path: string, formData: FormData): Promise<T> {
  const accessToken = accessTokenProvider();
  const response = await fetch(buildApiUrl(path), {
    method: 'POST',
    headers: {
      Accept: 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    body: formData,
  });
  if (!response.ok) {
    if (response.status === 401) unauthorizedHandler();
    const payload = await response.json().catch(() => null) as ApiResponse<unknown> | null;
    throw new ApiClientError(payload?.message ?? `请求失败：${response.status}`, response.status);
  }
  const payload = (await response.json()) as ApiResponse<T>;
  return payload.data;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) => request<T>(path, { method: 'POST', body }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body }),
  upload: <T>(path: string, formData: FormData) => upload<T>(path, formData),
};
