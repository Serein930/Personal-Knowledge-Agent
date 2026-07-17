import { defineConfig, devices } from '@playwright/test';

const stagingBaseUrl = process.env.STAGING_BASE_URL;
const stagingAccessToken = process.env.STAGING_ACCESS_TOKEN;
const stagingWorkspaceId = process.env.STAGING_WORKSPACE_ID;

if (!stagingBaseUrl?.startsWith('https://')) {
  throw new Error('STAGING_BASE_URL 必须是有效的 HTTPS 地址');
}
if (!stagingAccessToken) {
  throw new Error('缺少 STAGING_ACCESS_TOKEN');
}
if (!stagingWorkspaceId || !/^\d+$/.test(stagingWorkspaceId) || Number(stagingWorkspaceId) <= 0) {
  throw new Error('STAGING_WORKSPACE_ID 必须是正整数');
}

/**
 * 真实预发布验收不会在测试机启动模拟后端，而是通过 Vite 的同源代理访问 staging。
 * 这样既能运行真实浏览器交互，也不会为了跨域测试而放宽后端生产 CORS 策略。
 */
export default defineConfig({
  testDir: './staging-e2e',
  timeout: 180_000,
  expect: { timeout: 60_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: true,
  retries: 0,
  reporter: [
    ['line'],
    ['html', { outputFolder: 'staging-playwright-report', open: 'never' }],
  ],
  outputDir: 'staging-test-results',
  use: {
    baseURL: 'http://127.0.0.1:15174',
    locale: 'zh-CN',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1 --port 15174 --strictPort',
    url: 'http://127.0.0.1:15174',
    timeout: 120_000,
    reuseExistingServer: false,
    env: {
      VITE_API_BASE_URL: '/api',
      VITE_WORKSPACE_ID: stagingWorkspaceId,
      AGENTMIND_STAGING_E2E_PROXY_TARGET: stagingBaseUrl,
      AGENTMIND_STAGING_E2E_ACCESS_TOKEN: stagingAccessToken,
    },
  },
});
