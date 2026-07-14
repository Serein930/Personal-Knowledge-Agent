# 后端核心契约设计

本文档定义 Personal Knowledge Agent 后端早期阶段需要稳定下来的核心模型和接口契约。它用于指导后端实现，也用于前端联调前确认字段和 API 语义。

## 通用 API 响应格式

所有 REST 接口默认使用统一响应结构：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {},
  "traceId": "optional-trace-id",
  "timestamp": "2026-07-04T22:30:00"
}
```

字段说明：

- `code`：业务状态码，例如 `SUCCESS`、`BAD_REQUEST`、`RESOURCE_NOT_FOUND`。
- `message`：面向开发者和前端的简短说明。
- `data`：业务数据。
- `traceId`：链路追踪 ID，第一阶段可为空，后续接入日志追踪。
- `timestamp`：服务端响应时间。

分页响应结构：

```json
{
  "records": [],
  "page": 1,
  "pageSize": 20,
  "total": 0
}
```

## 用户模型

用户代表个人知识资产的拥有者。

建议字段：

- `id`：用户 ID。
- `username`：登录用户名。
- `displayName`：展示名称。
- `email`：邮箱。
- `passwordHash`：密码哈希。
- `role`：角色，例如 `USER`、`ADMIN`。
- `status`：状态，例如 `ACTIVE`、`DISABLED`。
- `createdAt`：创建时间。
- `updatedAt`：更新时间。

第一阶段不实现用户登录，但后续所有知识资产都应能关联到用户。

## 知识空间模型

知识空间用于隔离不同主题的知识资产。

建议字段：

- `id`：知识空间 ID。
- `ownerUserId`：拥有者用户 ID。
- `name`：知识空间名称。
- `description`：描述。
- `visibility`：可见性，例如 `PRIVATE`。
- `defaultModel`：默认聊天模型。
- `defaultEmbeddingModel`：默认 Embedding 模型。
- `createdAt`：创建时间。
- `updatedAt`：更新时间。

规则：

- 文档、chunk、会话、学习计划和复习卡片必须归属于某个知识空间。
- 默认检索只在当前知识空间内进行。

## 文档元数据模型

文档元数据描述用户上传或采集的知识资产。

建议字段：

- `id`：文档 ID。
- `ownerUserId`：拥有者用户 ID。
- `workspaceId`：知识空间 ID。
- `title`：标题。
- `sourceType`：来源类型，例如 `PDF`、`MARKDOWN`、`WEB_PAGE`、`WORD`、`TEXT`、`CODE`。
- `sourceUri`：来源地址，文件对象路径或网页 URL。
- `originalFilename`：原始文件名。
- `contentHash`：正文或文件 hash，用于去重。
- `tags`：标签。
- `ingestionStatus`：摄取状态。
- `chunkCount`：chunk 数量。
- `createdAt`：创建时间。
- `updatedAt`：更新时间。
- `deletedAt`：软删除时间。

规则：

- 删除文档时，后续必须同步删除或标记失效对应 chunk 和向量。
- 网页重复提交时优先根据 URL 和正文 hash 判断是否需要创建新版本。

## 摄取任务模型

摄取任务用于跟踪文件上传、网页采集、解析、分块和向量化状态。

建议字段：

- `id`：任务 ID。
- `ownerUserId`：拥有者用户 ID。
- `workspaceId`：知识空间 ID。
- `documentId`：关联文档 ID，可为空，任务创建初期可能尚未生成文档。
- `taskType`：任务类型，例如 `FILE_UPLOAD`、`WEB_PAGE_CAPTURE`、`REINDEX`。
- `status`：状态，例如 `PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELED`。
- `progress`：进度，0 到 100。
- `source`：来源文件名或 URL。
- `errorMessage`：失败原因。
- `createdAt`：创建时间。
- `updatedAt`：更新时间。
- `startedAt`：开始时间。
- `finishedAt`：结束时间。

规则：

- 摄取任务必须可查询。
- 失败任务必须保留失败原因。
- 重试时应保证幂等。

## Agent 工具调用审计模型

工具调用审计用于记录 Agent 执行了什么工具、输入是什么、结果如何。

建议字段：

- `id`：审计 ID。
- `ownerUserId`：拥有者用户 ID。
- `workspaceId`：知识空间 ID。
- `conversationId`：会话 ID。
- `messageId`：触发工具调用的消息 ID。
- `toolName`：工具名称。
- `toolType`：工具类型，例如 `READ`、`WRITE`、`ANALYSIS`。
- `requestPayload`：工具入参，敏感字段需要脱敏。
- `responseSummary`：结果摘要。
- `status`：状态，例如 `PENDING`、`SUCCEEDED`、`FAILED`、`SKIPPED`。
- `errorMessage`：失败原因。
- `latencyMs`：耗时。
- `createdAt`：创建时间。

规则：

- 写操作工具必须权限校验。
- 审计日志不得保存敏感明文。
- 前端评估观测页后续可展示工具调用链路。

## 文件上传接口

接口：

```text
POST /api/v1/workspaces/{workspaceId}/documents/files
Content-Type: multipart/form-data
```

请求字段：

- `file`：上传文件。
- `title`：可选标题。
- `tags`：可选标签数组或逗号分隔字符串。

响应：

```json
{
  "documentId": "doc-id",
  "taskId": "task-id",
  "status": "PENDING"
}
```

规则：

- 校验文件大小。
- 校验扩展名和内容类型。
- 文件先存储，解析和向量化走异步任务。

## URL 采集接口

接口：

```text
POST /api/v1/workspaces/{workspaceId}/documents/web-pages
Content-Type: application/json
```

请求：

```json
{
  "url": "https://example.com/article",
  "title": "可选标题",
  "tags": ["Java", "Spring AI"]
}
```

响应：

```json
{
  "documentId": "doc-id",
  "taskId": "task-id",
  "status": "PENDING"
}
```

规则：

- 只允许 `http` 和 `https`。
- 阻止 localhost、内网 IP、回环地址和链路本地地址。
- 设置超时、最大响应体大小和重定向限制。
- 采集后进入异步解析流程。

## 文档列表接口

接口：

```text
GET /api/v1/workspaces/{workspaceId}/documents
```

查询参数：

- `page`：页码，默认 1。
- `pageSize`：每页数量，默认 20。
- `keyword`：关键词。
- `sourceType`：来源类型。
- `status`：摄取状态。
- `tag`：标签。

响应：

```json
{
  "records": [
    {
      "id": "doc-id",
      "title": "Java 并发编程笔记",
      "sourceType": "MARKDOWN",
      "workspaceId": "workspace-id",
      "workspaceName": "Java 后端学习",
      "tags": ["Java", "并发"],
      "ingestionStatus": "SUCCEEDED",
      "chunkCount": 48,
      "updatedAt": "2026-07-04T22:30:00"
    }
  ],
  "page": 1,
  "pageSize": 20,
  "total": 1
}
```

## RAG 问答接口

非流式接口：

```text
POST /api/v1/workspaces/{workspaceId}/rag/chat
Content-Type: application/json
```

请求：

```json
{
  "conversationId": "optional-conversation-id",
  "question": "线程池核心参数怎么理解？",
  "topK": 5,
  "filters": {
    "tags": ["Java", "并发"],
    "sourceTypes": ["MARKDOWN", "PDF"]
  }
}
```

响应：

```json
{
  "conversationId": "conversation-id",
  "messageId": "message-id",
  "answer": "回答正文",
  "citations": [
    {
      "documentId": "doc-id",
      "title": "Java 并发编程笔记",
      "chunkId": "chunk-id",
      "excerpt": "引用片段",
      "score": 0.91
    }
  ],
  "toolCalls": [],
  "usage": {
    "promptTokens": 1000,
    "completionTokens": 300,
    "totalTokens": 1300
  }
}
```

当真实 Spring AI 模型选择了只读工具时，`toolCalls` 返回本次回答消息对应的脱敏调用摘要；默认模拟回答或模型未选择工具时返回空数组。

后续流式接口：

```text
POST /api/v1/workspaces/{workspaceId}/rag/chat/stream
```

流式响应建议使用 SSE，具体契约在 RAG 阶段再细化。

当前流式协议已经增加可选工具事件：

```text
event: tool_call
data: {"sequence":1,"toolCall":{"toolName":"knowledge.search","status":"SUCCEEDED"}}
```

正常完成事件的 `toolCalls` 字段会再次返回本次回答的全部工具摘要，便于前端在丢失单个中间事件时用最终事件校准状态。

## 智能体工具调用接口

当前 Stage 7 首批只读工具提供显式调用接口，用于前端联调、工具审计验证和后续模型编排适配：

```text
POST /api/v1/workspaces/{workspaceId}/agent/tool-calls
```

当前演示环境可选请求头：

```text
X-Demo-User-Id: 1
```

请求示例：

```json
{
  "conversationId": 101,
  "toolName": "knowledge.search",
  "requestId": "search-java-thread-pool-001",
  "arguments": {
    "query": "线程池核心参数",
    "topK": 5
  }
}
```

当前白名单工具：

- `knowledge.search`：参数为 `query` 和可选 `topK`，在当前知识空间执行语义检索。
- `document.read_chunk`：参数为 `documentId` 与 `chunkId`，读取当前知识空间中的指定文档片段。

成功响应中的 `audit` 包含审计编号、调用状态、参数摘要、结果摘要与耗时；`result` 是当前调用方可见的业务结果。相同用户、知识空间和 `requestId` 的成功调用会复用已有审计记录。

审计查询接口：

```text
GET /api/v1/workspaces/{workspaceId}/agent/tool-calls?page=1&pageSize=20
```

规则：

- 工具名称必须存在于服务端白名单中。
- 工具参数必须经过服务端校验，不能信任模型或客户端传入的数据类型。
- 调用上下文必须通过用户、知识空间和可选会话归属校验。
- 审计记录只保存参数摘要和结果摘要，不重复保存完整私有知识内容。

## 写工具确认接口

写工具不能通过普通工具调用接口直接执行。当前首个写工具为 `note.create`，参数为 `title` 和 `content`。

创建确认单：

```text
POST /api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations
```

请求必须携带稳定的 `requestId`。成功响应返回 `PENDING_CONFIRMATION` 确认单和只出现一次的 `confirmationToken`。

确认与拒绝：

```text
POST /api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{confirmationId}/confirm
POST /api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{confirmationId}/reject
```

请求体：

```json
{
  "confirmationToken": "创建确认单时返回的一次性令牌"
}
```

查询确认单与查询笔记：

```text
GET /api/v1/workspaces/{workspaceId}/agent/write-tool-confirmations/{confirmationId}
GET /api/v1/workspaces/{workspaceId}/notes?page=1&pageSize=20
```

安全规则：

- 服务端只保存确认令牌摘要，不保存或记录令牌明文。
- 确认时重新校验用户、知识空间、会话和可选消息归属。
- 确认单状态从 `PENDING_CONFIRMATION` 原子迁移，避免并发重复执行。
- 相同工具、用户、知识空间和 `requestId` 的相同参数复用既有成功写入。
- 相同 `requestId` 对应不同参数时返回 `RESOURCE_CONFLICT`，不得错误复用旧结果。
- 写工具成功执行后仍生成标准工具审计记录；确认单响应不暴露完整参数和令牌摘要。

## 后续实现顺序

1. Stage 1 先实现统一响应、异常处理和健康检查。
2. Stage 2 实现核心 DTO、枚举和领域模型骨架。
3. Stage 3 开始落地文件上传、URL 采集和文档列表接口。
4. RAG 问答接口必须等文档摄取和向量检索具备基本能力后再实现。
