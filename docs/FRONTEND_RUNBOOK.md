# 前端运行与验证说明

本文档记录前端本地运行、构建和预览的标准步骤。每次进入前端联调或视觉检查阶段时，优先按本文档执行。

## 环境要求

- Node.js 20 或更高版本
- npm 10 或更高版本
- 当前项目目录：`D:\Program\AgentMind\ui`

## 安装依赖

```powershell
cd D:\Program\AgentMind\ui
npm install
```

安装成功后应生成：

- `node_modules/`
- `package-lock.json`

其中 `node_modules/` 不应提交到 Git，`package-lock.json` 应提交，用于锁定前端依赖版本。

## 类型检查与构建

```powershell
npm run build
```

该命令会先执行 TypeScript 编译，再执行 Vite 构建。构建成功后会生成 `dist/`，该目录不提交到 Git。

如果当前 PowerShell 会自动激活 Conda，并出现中文路径编码导致的 Conda 报错，可以使用非登录 shell 或重新打开一个普通 PowerShell 后再执行 npm 命令。核心判断标准是命令能正常进入 `tsc -b && vite build`。

## 启动开发预览

```powershell
npm run dev
```

默认访问：

```text
http://localhost:5173
```

## 当前阶段检查重点

- 侧边栏导航是否可以切换所有页面。
- 工作台、知识库、采集中心、Agent 问答、学习计划、评估观测、设置页面是否都能正常显示。
- 页面文字是否溢出或重叠。
- 表格在较窄宽度下是否出现横向滚动而不是挤压变形。
- 设置页是否正确读取 `VITE_API_BASE_URL` 并保存当前知识空间偏好。
- 本地 JWT 模式是否可以注册、登录、自动刷新令牌和退出。
- 切换知识空间后，各业务页面是否重新加载对应空间的数据。
- 复习工作台是否能加载统计、保存今日目标、创建到期队列、显示答案并提交评分。
- 卡片暂停后是否离开到期数量，恢复后是否重新显示为活动状态。
- 控制台是否存在 TypeScript 或运行时错误。

## 本阶段说明

工作台、采集中心、知识库、Agent 问答、复习工作台和设置页已经接入真实后端。知识空间列表来自当前登录用户的后端接口，选择结果只在浏览器保存空间编号，不再使用构建期固定编号。后端地址默认是 `http://localhost:8081/api`。

```powershell
$env:VITE_API_BASE_URL = "http://localhost:8080/api"
$env:VITE_AUTH_MODE = "local-jwt"
npm run dev
```

`VITE_AUTH_MODE` 应与后端 `AGENTMIND_SECURITY_MODE` 保持一致，可选 `disabled`、`local-jwt` 或 `oidc`。
本地 JWT 令牌由统一 API Client 和 SSE Client 自动携带；收到 `401` 后会清理会话并返回登录页。
OIDC 模式使用授权码 + PKCE，不在浏览器保存客户端密钥；需要额外配置 `VITE_OIDC_AUTHORITY`、
`VITE_OIDC_CLIENT_ID` 和可选的 `VITE_OIDC_SCOPE`，令牌续期与退出由标准 OIDC 客户端管理。

问答页面通过 `fetch` 解析 POST SSE，支持 `delta`、`citation`、`tool_call`、`tool_confirmation_required` 和 `complete` 事件。确认令牌只保存在当前页面内存中，不写入浏览器持久化存储。

复习工作台通过普通 REST 接口加载每日计划、学习统计和卡片队列。评分请求为每次用户操作生成新的 `requestId`；发生网络重试时应复用同一个编号，后端据此防止重复推进调度。

工作台通过 `GET /api/v1/workspaces/{workspaceId}/dashboard` 一次加载知识资产数量、今日摄取、
到期卡片、模型调用指标、最近文档和今日学习任务。没有文档或学习计划属于正常空状态，不应回退到模拟数据。

设置页通过 `GET/PUT /api/v1/workspaces/{workspaceId}/preferences` 读取和保存非敏感偏好。
页面使用响应中的 `version` 作为下一次保存的 `expectedVersion`；收到 `409` 时会提示冲突并自动加载最新值。
模型密钥和供应商端点不属于前端设置项，仍应通过后端环境变量、Vault 或 KMS 管理。
默认召回数量和引用策略已经接入 RAG 上下文组装；请求没有显式传递 `topK` 时使用当前知识空间偏好。
聊天模型会写入同步与 SSE 请求级 Spring AI 选项，Embedding 模型同时用于摄取索引和查询向量，
从而避免同一知识空间混用不同向量空间。

## 当前环境验证记录

- 前端依赖已经安装。
- TypeScript 编译与 Vite 生产构建已通过。
- 后端默认只允许 `http://localhost:5173` 跨域访问，可通过 `AGENTMIND_ALLOWED_ORIGIN` 调整。
