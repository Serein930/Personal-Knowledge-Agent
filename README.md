# Personal Knowledge Agent

## 本地启动

Docker Desktop 启动后，日常开发只需要两个终端。

后端：

```powershell
cd D:\Program\AgentMind\backend
.\scripts\start-local.ps1
```

该脚本会自动启动并等待 PostgreSQL、Redis、OpenSearch 和 SearXNG，然后启动
`8081` 端口的 Spring Boot 服务。检测到完整模型环境变量时会自动启用真实模型；
否则使用 Mock 问答启动，不阻塞其他功能。

前端：

```powershell
cd D:\Program\AgentMind\ui
npm run dev
```

前端默认连接 `http://localhost:8081/api` 并使用 `local-jwt`，无需创建临时环境文件。
真实模型密钥仍必须一次性存放在操作系统用户环境变量、Vault 或其他密钥管理设施中，
不得写入仓库；启动脚本会自动读取用户环境变量，无需每次使用 `$env:` 临时配置。
