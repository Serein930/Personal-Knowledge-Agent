export const env = {
  // 后端 API 地址只从 Vite 环境变量读取，避免把机器本地配置写死在代码中。
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
};
