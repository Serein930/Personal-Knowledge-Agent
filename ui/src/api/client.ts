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

/** 将浏览器原始网络异常转换成包含后端地址的中文提示，方便区分业务错误与连接/CORS 问题。 */
async function fetchApi(path: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(buildApiUrl(path), init);
  } catch (error) {
    throw new ApiClientError(
      `无法连接后端服务（${env.apiBaseUrl}），请检查后端端口、前端 API 地址和跨域配置`,
      0,
    );
  }
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const accessToken = accessTokenProvider();
  const response = await fetchApi(path, {
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
  const response = await fetchApi(path, {
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
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
  upload: <T>(path: string, formData: FormData) => upload<T>(path, formData),
};
