# AgentMind Backend

Personal Knowledge Agent 后端服务。

## 当前阶段

当前处于后端 Stage 4：文件与网页摄取流程实现。

已包含：

- Java 21 + Spring Boot 3.x Maven 工程。
- 统一 API 响应结构、分页响应结构和全局异常处理。
- 健康检查接口。
- 文档、摄取任务相关 DTO 和枚举契约。
- 文件上传接口骨架，并接入真实文件大小、文件名、扩展名校验。
- 本地对象存储适配层，默认写入运行目录下的 `.agentmind-storage`。
- URL 采集接口骨架，并接入基础 SSRF 防护和 HTML 抓取骨架。
- 内存版文档与摄取任务状态流转，用于前后端联调。
- 针对当前摄取流程的单元测试。

暂未包含：

- 数据库持久化。
- 用户登录与权限。
- MinIO 真实部署适配。
- 文档正文解析、chunk 切分和向量化。
- Spring AI 与 RAG 问答。

## 运行方式

要求：

- JDK 21
- Maven 3.9+

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run
```

健康检查：

```text
GET http://localhost:8080/api/v1/health
```

## 测试

```powershell
cd D:\Program\AgentMind\backend
mvn test
```

本项目在 `backend/.mvn/settings.xml` 中指定 `${user.home}/.m2/repository` 作为 Maven 本地仓库，
用于避免受开发机器全局 Maven 配置影响。

## Stage 4 摄取接口

文档列表：

```text
GET http://localhost:8080/api/v1/workspaces/1/documents?page=1&pageSize=20
```

URL 采集任务：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/documents/web-pages" `
  -ContentType "application/json" `
  -Body '{"url":"https://example.com/article","title":"示例文章","tags":["Web","测试"]}'
```

摄取任务查询：

```text
GET http://localhost:8080/api/v1/workspaces/1/ingestion-tasks/{taskId}
```

文件上传任务：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/documents/files" `
  -Form @{ file = Get-Item ".\README.md"; title = "README 文档"; tags = "文档" }
```

说明：

- 文件上传成功后，原始文件会保存到 `.agentmind-storage`，该目录已加入 `.gitignore`。
- URL 采集会校验 `http` / `https` 协议，并拒绝 localhost、回环地址和常见内网地址。
- URL 采集当前只保存原始 HTML 快照，正文提取、去噪、重复检测和版本管理会在后续阶段实现。
- 当前文档与任务数据仍保存在内存中，服务重启后会恢复为 mock 初始数据。
