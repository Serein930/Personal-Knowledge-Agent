import { defineConfig, devices } from '@playwright/test';

/**
 * 全链路测试使用独立端口，并强制启动新的前后端进程。
 * 这样可以避免误连开发者正在运行的服务，也能保证本地与持续集成环境行为一致。
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [
    ['line'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  outputDir: 'test-results',
  use: {
    baseURL: 'http://127.0.0.1:15173',
    locale: 'zh-CN',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: [
    {
      command: 'node scripts/start-e2e-backend.mjs',
      url: 'http://127.0.0.1:18081/actuator/health',
      timeout: 180_000,
      reuseExistingServer: false,
      env: {
        SERVER_PORT: '18081',
        AGENTMIND_ALLOWED_ORIGIN: 'http://127.0.0.1:15173',
        AGENTMIND_SECURITY_MODE: 'disabled',
        AGENTMIND_RATE_LIMIT_MODE: 'disabled',
        AGENTMIND_TRACING_ENABLED: 'false',
      },
    },
    {
      command: 'npm run dev -- --host 127.0.0.1 --port 15173 --strictPort',
      url: 'http://127.0.0.1:15173',
      timeout: 120_000,
      reuseExistingServer: false,
      env: {
        VITE_API_BASE_URL: 'http://127.0.0.1:18081/api',
        VITE_WORKSPACE_ID: '1',
      },
    },
  ],
});
