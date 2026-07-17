import { expect, test } from '@playwright/test';

test('真实 OIDC、文档摄取、生产索引和流式问答形成前后端闭环', async ({ page, request }) => {
  const browserErrors: string[] = [];
  const marker = `AgentMind-Staging-E2E-${Date.now()}`;
  page.on('pageerror', (error) => browserErrors.push(error.message));

  // 请求通过与浏览器相同的本地代理进入 staging，用当前短期令牌验证真实 OIDC 用户映射。
  const currentUserResponse = await request.get('/api/v1/users/me');
  expect(currentUserResponse.ok()).toBeTruthy();
  const currentUser = await currentUserResponse.json() as { data?: { id?: number } };
  expect(currentUser.data?.id).toBeGreaterThan(0);

  await page.goto('/');
  await expect(page.getByText('AgentMind', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: '采集中心' }).click();
  await page.locator('input[type="file"]').setInputFiles({
    name: `${marker}.md`,
    mimeType: 'text/markdown',
    buffer: Buffer.from([
      '# AgentMind 真实预发布验收资料',
      '',
      `${marker} 是本次验收的唯一标记。`,
      '',
      '真实知识处理闭环包括文件对象存储、文本切分、向量与关键词双路索引、检索增强生成和引用返回。',
    ].join('\n'), 'utf8'),
  });
  await page.getByRole('button', { name: '提交文件' }).click();
  await expect(page.getByText('文件摄取任务已创建')).toBeVisible();

  const ingestionTaskPanel = page.locator('section.panel').filter({
    has: page.getByRole('heading', { name: '本次联调任务' }),
  });
  await expect(ingestionTaskPanel.getByText(`${marker}.md`)).toBeVisible();
  await expect.poll(async () => {
    await ingestionTaskPanel.getByRole('button', { name: '刷新' }).click();
    return ingestionTaskPanel.textContent();
  }, {
    message: '等待真实文档完成 MinIO 存储、切分及 pgvector/OpenSearch 索引',
    timeout: 120_000,
    intervals: [1_000, 2_000, 5_000],
  }).toContain('已完成');

  await page.getByRole('button', { name: '知识库' }).click();
  const uploadedDocumentRow = page.getByRole('row').filter({ hasText: `${marker}.md` });
  await expect(uploadedDocumentRow).toBeVisible();
  await expect(uploadedDocumentRow).toContainText('Markdown');
  await expect(uploadedDocumentRow).toContainText('已完成');

  await page.getByRole('button', { name: 'Agent 问答' }).click();
  const question = `请根据知识库说明 ${marker} 对应的真实知识处理闭环。`;
  await page.getByPlaceholder('例如：根据资料生成一张线程池复习卡片').fill(question);
  await page.getByRole('button', { name: '发送' }).click();

  await expect(page.getByText(question, { exact: true })).toBeVisible();
  const assistantAnswer = page.locator('.chat-message--agent p').last();
  await expect(assistantAnswer).toContainText('知识', { timeout: 120_000 });
  const citationPanel = page.locator('section.panel').filter({
    has: page.getByRole('heading', { name: '引用来源' }),
  });
  await expect(citationPanel).toContainText(marker);
  await expect(citationPanel.getByText('暂无引用')).toHaveCount(0);

  expect(browserErrors, `浏览器出现未处理异常：${browserErrors.join('；')}`).toEqual([]);
});
