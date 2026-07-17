import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  // 空前缀只用于 Node.js 配置层读取当前进程环境；只有 VITE_ 前缀变量才会进入浏览器代码。
  const runtimeEnv = loadEnv(mode, '.', '');
  const stagingProxyTarget = runtimeEnv.AGENTMIND_STAGING_E2E_PROXY_TARGET;
  const stagingAccessToken = runtimeEnv.AGENTMIND_STAGING_E2E_ACCESS_TOKEN;

  if (stagingProxyTarget && !stagingAccessToken) {
    throw new Error('真实 staging E2E 代理缺少访问令牌');
  }
  if (stagingProxyTarget && stagingProxyTarget.indexOf('https://') !== 0) {
    throw new Error('真实 staging E2E 代理只允许连接 HTTPS 地址');
  }

  return {
    plugins: [react()],
    server: {
      port: 5173,
      // 该代理只在真实 staging E2E 显式传入目标地址时启用。OIDC 令牌停留在 Node.js
      // 开发服务器进程中，不会被 Vite 注入浏览器代码、localStorage 或构建产物。
      proxy: stagingProxyTarget ? {
        '/api': {
          target: stagingProxyTarget,
          changeOrigin: true,
          secure: true,
          headers: {
            Authorization: `Bearer ${stagingAccessToken}`,
          },
        },
      } : undefined,
    },
  };
});
