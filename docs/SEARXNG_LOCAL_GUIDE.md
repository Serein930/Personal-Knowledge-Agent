# SearXNG 本地联网搜索指南

## 目标

复习卡片可以通过自建 SearXNG 补充公开互联网资料，不依赖 Brave Search API Key。
知识库原文始终是卡片的主要依据；联网结果只显示在“联网补充”区域，搜索失败不会阻断制卡。

## 日常启动

后端本地启动脚本会自动启动并等待 PostgreSQL、Redis、OpenSearch 和 SearXNG，
无需单独执行 Docker 命令或设置临时搜索变量：

```powershell
cd D:\Program\AgentMind\backend
.\scripts\start-local.ps1
```

默认访问地址为 `http://localhost:8888`。端口冲突时可在本地 `.env` 中设置：

```text
AGENTMIND_SEARXNG_PORT=8889
```

共享环境或公网环境还必须在本地 `.env` 中设置随机的 `AGENTMIND_SEARXNG_SECRET`，
并通过反向代理限制访问；真实密钥不得提交到 Git。

## 验证 JSON 搜索接口

```powershell
Invoke-RestMethod "http://localhost:8888/search?q=Java%20virtual%20thread&format=json"
```

若返回 `403`，说明 SearXNG 没有启用 JSON 输出。确认
`searxng/settings.yml` 的 `search.formats` 同时包含 `html` 和 `json`，然后重建容器：

```powershell
docker compose --profile searxng up -d --force-recreate agentmind-searxng
```

SearXNG 模式不需要设置 `AGENTMIND_WEB_SEARCH_API_KEY`。脚本参数
`-SkipDependencies` 只用于已经手动管理容器的排障场景，日常启动不需要使用。

## 切回 Brave Search

```powershell
$env:AGENTMIND_WEB_SEARCH_PROVIDER = "brave"
$env:AGENTMIND_WEB_SEARCH_BASE_URL = "https://api.search.brave.com"
$env:AGENTMIND_WEB_SEARCH_API_KEY = "本地真实密钥"
```

提供方、基础地址和密钥必须成组切换，避免把 Brave 请求发送到 SearXNG，或反向发送。

## 常见问题

- 返回“当前未配置联网搜索”：检查 `AGENTMIND_FLASHCARD_WEB_SEARCH_ENABLED`。
- 返回“搜索提供方配置不完整”：检查基础地址；Brave 模式还必须检查 API Key。
- 返回“联网搜索暂时不可用”：查看后端警告日志和 `docker compose logs agentmind-searxng`。
- 搜索结果为空：先直接调用 JSON 接口，确认实例的上游搜索引擎在当前网络中可用。
- 频繁被上游限流：降低每次制卡数量，调整 SearXNG 引擎列表，或为实例配置代理出口。
