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
- 控制台是否存在 TypeScript 或运行时错误。

## 本阶段说明

当前阶段仍不接入真实后端 API，不调用真实模型，不进行真实文件上传。前端只完成联调前的页面结构、配置展示和运行验证准备。

## 当前环境验证记录

本阶段已确认：

- GitHub 远程 `main` 已包含上一阶段提交 `e332c75`。
- 当前环境 Node.js 版本为 `v24.14.1`。
- 当前环境 npm 版本为 `11.11.0`。
- `npm install` 在受限环境中超时，且联网安装授权未返回结果。
- 因依赖未安装，`npm run build` 停在 `tsc` 命令不存在，尚未进入真实 TypeScript 编译。

因此，下一次具备联网安装权限时，应先执行：

```powershell
cd D:\Program\AgentMind\ui
npm install
npm run build
npm run dev
```
