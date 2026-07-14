# 短期会话记忆联调手册

本文档用于验证 Stage 6 的短期会话记忆、滑动窗口、同步与流式问答复用、消息状态流转和知识空间隔离。

## 默认配置

```yaml
agentmind:
  chat:
    memory:
      store: memory
      max-history-turns: 12
      history-scan-message-limit: 200
      model-context-window-tokens: 8192
      reserved-context-tokens: 4096
```

- `store`：默认使用 `memory`，服务重启后会话会清空；可通过 `redis` profile 切换到 Redis。
- `max-history-turns`：最多放入提示词的完整成功问答轮次。
- `history-scan-message-limit`：从仓储读取的最近完成消息数量，用于跳过失败和取消轮次。
- `model-context-window-tokens`：当前模型的上下文 Token 上限。
- `reserved-context-tokens`：为系统提示词、当前问题、检索片段和模型输出预留的 Token。

短期历史预算等于模型上下文上限减去预留 Token。系统先将成功的用户消息和助手回答配成完整轮次，再从最新轮次向前装入预算；任何一轮无法完整放入时立即停止，不会截断消息或产生孤立问题。

## 创建会话并继续追问

第一次问答不传 `conversationId`，后端会创建会话：

```powershell
$first = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/rag/chat" `
  -ContentType "application/json" `
  -Body '{"question":"线程池为什么可以复用工作线程？","topK":5}'

$conversationId = $first.data.conversationId
```

继续同一个会话：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/rag/chat" `
  -ContentType "application/json" `
  -Body (ConvertTo-Json @{
    conversationId = $conversationId
    question = "继续解释上一轮提到的复用机制"
    topK = 5
  })
```

第二轮响应中的 `retrievalContext.promptContext` 会包含第一轮已经完成的用户和助手消息。历史只用于理解省略、指代和追问关系，知识事实仍必须来自本轮检索引用。

## 流式问答复用

SSE 接口使用相同的 `conversationId`：

```powershell
$streamBody = ConvertTo-Json @{
  conversationId = $conversationId
  question = "请流式总结上一轮回答"
  topK = 5
}

curl.exe -N `
  -X POST "http://localhost:8080/api/v1/workspaces/1/rag/chat/stream" `
  -H "Content-Type: application/json" `
  -H "Accept: text/event-stream" `
  --data-raw $streamBody
```

同步接口和 SSE 接口使用同一个记忆仓储和滑动窗口。流式回答会先组装完整正文，只有生成正常完成后才写入助手消息。

## 查询会话

分页查询知识空间中的会话：

```text
GET /api/v1/workspaces/1/chat/conversations?page=1&pageSize=20
```

分页查询指定会话的消息：

```text
GET /api/v1/workspaces/1/chat/conversations/{conversationId}/messages?page=1&pageSize=50
```

消息按照创建时间正序返回。助手消息状态包括：

- `PENDING`：回答正在生成。
- `COMPLETED`：回答完整生成，正文可以进入后续滑动窗口。
- `FAILED`：回答异常结束，正文为空，只保留失败原因。
- `CANCELLED`：客户端断开或超时，正文为空，只保留取消原因。

用户消息创建后直接为 `COMPLETED`。

## 管理会话

重命名会话：

```powershell
Invoke-RestMethod `
  -Method Patch `
  -Uri "http://localhost:8080/api/v1/workspaces/1/chat/conversations/$conversationId" `
  -ContentType "application/json" `
  -Body '{"title":"Java Agent 学习会话"}'
```

归档会话：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/chat/conversations/$conversationId/archive"
```

归档操作是幂等的。归档后仍可查询历史消息，但继续同步或流式问答会返回 `409 RESOURCE_CONFLICT`。已经开始生成的助手消息仍可进入最终状态。

删除会话：

```powershell
Invoke-RestMethod `
  -Method Delete `
  -Uri "http://localhost:8080/api/v1/workspaces/1/chat/conversations/$conversationId"
```

删除会物理清理当前短期记忆中的会话、消息和索引，当前阶段不提供恢复功能。

## 知识空间隔离

即使会话编号真实存在，也不能从其他知识空间读取：

```text
GET /api/v1/workspaces/2/chat/conversations/{workspace1ConversationId}/messages
```

接口返回：

```json
{
  "code": "RESOURCE_NOT_FOUND",
  "message": "会话不存在或无权访问"
}
```

查询不会区分“编号不存在”和“属于其他知识空间”，避免通过响应差异探测其他用户资源。

## 当前边界

- 当前尚未接入认证，知识空间归属规则已经落到仓储和服务契约，后续还需要绑定当前登录用户。
- 当前默认使用 Spring AI JTokkit 估算 Token；接入非兼容编码模型时应提供对应的 `ChatTokenCounter` 实现。
- 当前删除是短期记忆物理删除，后续若会话进入正式知识资产，应增加软删除和恢复策略。
- 内存适配器只保证单进程内编号和状态更新，服务重启后数据会清空；需要跨进程保存时使用 Redis 模式。
- Redis 模式的启动、键结构和手动测试见 `docs/REDIS_CHAT_MEMORY_RUNBOOK.md`。
