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

export function buildApiUrl(path: string) {
  const normalizedBase = env.apiBaseUrl.replace(/\/$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return `${normalizedBase}${normalizedPath}`;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const response = await fetch(buildApiUrl(path), {
    ...options,
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      ...options.headers,
    },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });

  if (!response.ok) {
    const payload = await response.json().catch(() => null) as ApiResponse<unknown> | null;
    throw new ApiClientError(payload?.message ?? `请求失败：${response.status}`, response.status);
  }

  const payload = (await response.json()) as ApiResponse<T>;
  return payload.data;
}

export async function upload<T>(path: string, formData: FormData): Promise<T> {
  const response = await fetch(buildApiUrl(path), {
    method: 'POST',
    headers: { Accept: 'application/json' },
    body: formData,
  });
  if (!response.ok) {
    const payload = await response.json().catch(() => null) as ApiResponse<unknown> | null;
    throw new ApiClientError(payload?.message ?? `请求失败：${response.status}`, response.status);
  }
  const payload = (await response.json()) as ApiResponse<T>;
  return payload.data;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) => request<T>(path, { method: 'POST', body }),
  upload: <T>(path: string, formData: FormData) => upload<T>(path, formData),
};
