export const env = {
  // 后端 接口 地址只从 前端构建工具 环境变量读取，避免把机器本地配置写死在代码中。
  apiBaseUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081/api',
  workspaceId: Number(import.meta.env.VITE_WORKSPACE_ID ?? '1'),
};
