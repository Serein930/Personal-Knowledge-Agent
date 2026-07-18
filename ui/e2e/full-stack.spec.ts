import { expect, test } from '@playwright/test';
import path from 'node:path';

test('文件摄取、知识库展示和流式问答形成真实闭环', async ({ page, request }) => {
  const browserErrors: string[] = [];
  page.on('pageerror', (error) => browserErrors.push(error.message));

  // 先验证后端确实可用，避免页面错误被误判为前端交互问题。
  const healthResponse = await request.get('http://127.0.0.1:18081/actuator/health');
  expect(healthResponse.ok()).toBeTruthy();

  await page.goto('/');
  await page.getByRole('tab', { name: '注册' }).click();
  const registrationPanel = page.locator('.ant-tabs-tabpane-active');
  const registrationInputs = registrationPanel.locator('input');
  const uniqueUsername = `e2e_${Date.now()}`;
  await registrationInputs.nth(0).fill(uniqueUsername);
  await registrationInputs.nth(1).fill('全链路测试用户');
  await registrationInputs.nth(2).fill(`${uniqueUsername}@example.com`);
  await registrationInputs.nth(3).fill('AgentMind-E2E-Password-2026');
  await registrationPanel.getByRole('button', { name: '创建账号' }).click();
  await expect(page.getByText('AgentMind', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: '采集中心' }).click();
  const fixturePath = path.resolve(process.cwd(), 'e2e/fixtures/agentmind-e2e.md');
  await page.locator('input[type="file"]').setInputFiles(fixturePath);
  await page.getByRole('button', { name: '提交文件' }).click();

  await expect(page.getByText('文件摄取任务已创建')).toBeVisible();
  const ingestionTaskPanel = page.locator('section.panel').filter({
    has: page.getByRole('heading', { name: '本次联调任务' }),
  });
  await expect(ingestionTaskPanel.getByText('agentmind-e2e.md')).toBeVisible();
  await expect(ingestionTaskPanel.getByText('已完成', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: '知识库' }).click();
  const uploadedDocumentRow = page.getByRole('row').filter({ hasText: 'agentmind-e2e.md' });
  await expect(uploadedDocumentRow).toBeVisible();
  await expect(uploadedDocumentRow).toContainText('Markdown');
  await expect(uploadedDocumentRow).toContainText('已完成');

  await page.getByRole('button', { name: 'Agent 问答' }).click();
  const question = 'AgentMind-E2E-2026 的知识处理闭环包含哪些步骤？';
  await page.getByPlaceholder('例如：根据资料生成一张线程池复习卡片').fill(question);
  await page.getByRole('button', { name: '发送' }).click();

  await expect(page.getByText(question, { exact: true })).toBeVisible();
  const assistantAnswer = page.locator('.chat-message--agent p').last();
  await expect(assistantAnswer).toContainText('根据当前知识库检索结果', { timeout: 30_000 });
  const citationPanel = page.locator('section.panel').filter({
    has: page.getByRole('heading', { name: '引用来源' }),
  });
  await expect(citationPanel).toContainText('AgentMind-E2E-2026');
  await expect(citationPanel.getByText('暂无引用')).toHaveCount(0);

  expect(browserErrors, `浏览器出现未处理异常：${browserErrors.join('；')}`).toEqual([]);
});
