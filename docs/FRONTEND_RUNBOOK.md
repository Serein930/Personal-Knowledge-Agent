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
- 设置页是否正确读取 `VITE_API_BASE_URL`。
- 复习工作台是否能加载统计、保存今日目标、创建到期队列、显示答案并提交评分。
- 卡片暂停后是否离开到期数量，恢复后是否重新显示为活动状态。
- 控制台是否存在 TypeScript 或运行时错误。

## 本阶段说明

工作台、采集中心、知识库、Agent 问答和复习工作台已经接入真实后端。默认知识空间编号为 `1`，可通过 `VITE_WORKSPACE_ID` 修改；后端地址默认是 `http://localhost:8080/api`。

```powershell
$env:VITE_API_BASE_URL = "http://localhost:8080/api"
$env:VITE_WORKSPACE_ID = "1"
npm run dev
```

问答页面通过 `fetch` 解析 POST SSE，支持 `delta`、`citation`、`tool_call`、`tool_confirmation_required` 和 `complete` 事件。确认令牌只保存在当前页面内存中，不写入浏览器持久化存储。

复习工作台通过普通 REST 接口加载每日计划、学习统计和卡片队列。评分请求为每次用户操作生成新的 `requestId`；发生网络重试时应复用同一个编号，后端据此防止重复推进调度。

工作台通过 `GET /api/v1/workspaces/{workspaceId}/dashboard` 一次加载知识资产数量、今日摄取、
到期卡片、模型调用指标、最近文档和今日学习任务。没有文档或学习计划属于正常空状态，不应回退到模拟数据。

## 当前环境验证记录

- 前端依赖已经安装。
- TypeScript 编译与 Vite 生产构建已通过。
- 后端默认只允许 `http://localhost:5173` 跨域访问，可通过 `AGENTMIND_ALLOWED_ORIGIN` 调整。
