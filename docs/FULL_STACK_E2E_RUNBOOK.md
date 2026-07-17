# 前后端全链路验收手册

本文档说明如何用可重复的本地环境验证 AgentMind 的第一条前后端完整业务链路。该测试使用内存存储、确定性向量和模拟回答生成器，不依赖数据库、外部模型密钥或真实用户数据。

## 验收范围

自动化用例会依次验证：

1. 使用 Maven Wrapper 启动 Spring Boot 后端。
2. 使用 Vite 启动 React 前端。
3. 从采集中心上传固定 Markdown 资料。
4. 验证摄取任务成功，并在知识库列表中看到文档、类型和状态。
5. 从 Agent 问答页面发起 SSE 流式问题。
6. 验证回答来自后端检索增强链路，并返回上传文档的引用来源。
7. 验证浏览器没有未处理异常。

这条用例证明前端和后端已经形成真实可运行闭环，但不替代真实 OIDC、PostgreSQL、pgvector、Redis、MinIO、OpenSearch 和真实大模型的预发布验收。

## 环境要求

- JDK 21
- Node.js 22 或兼容版本
- npm

项目已经固定 Maven 3.9.11。开发机无需再单独安装同版本 Maven，构建工具会由 `backend/mvnw.cmd` 或 `backend/mvnw` 下载并校验。

## 首次安装

```powershell
cd D:\Program\AgentMind\ui
npm ci
npm run e2e:install
```

Linux 持续集成环境需要同时安装 Chromium 的系统依赖：

```bash
cd ui
npx playwright install --with-deps chromium
```

## 执行验收

```powershell
cd D:\Program\AgentMind\ui
npm run e2e
```

Playwright 会自动管理两个隔离服务：

- 后端：`http://127.0.0.1:18081`
- 前端：`http://127.0.0.1:15173`

测试结束后服务会自动停止。运行前应确保这两个端口没有被其他程序占用。

需要观察浏览器交互时可执行：

```powershell
npm run e2e:headed
```

## 测试产物

失败时会保留截图、视频和 Playwright 跟踪文件：

```text
ui/test-results
ui/playwright-report
```

以上目录已加入 Git 忽略规则。持续集成工作流会将它们作为临时诊断产物保存十四天。

## Maven Wrapper 验证

Windows：

```powershell
cd D:\Program\AgentMind\backend
.\mvnw.cmd -version
.\mvnw.cmd test
```

Linux：

```bash
cd backend
sh ./mvnw -version
sh ./mvnw test
```

## 与生产验收的边界

本地全链路测试只验证功能正确性和接口兼容性，不生成生产性能结论。RPO、RTO、P95、P99、最大吞吐和故障恢复时间必须由受保护的真实验收环境运行 `staging-acceptance.yml` 后固化，任何本地模拟值都不得写入发布候选证据。

只有生产证据工作流和本全链路测试同时通过，才允许冻结发布候选版本并进入最终发布审批。
