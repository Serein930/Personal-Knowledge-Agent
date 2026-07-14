# 写工具确认流程联调手册

## 本阶段目标

Stage 7 当前已经建立写工具安全闭环，并实现 `note.create` 与 `flashcard.create`。核心原则是模型或前端可以提出写入建议，但只有用户明确确认后才允许修改知识空间数据。

## 调用流程

```text
创建确认单
  -> 校验工具白名单、WRITE 类型、参数和权限
  -> 返回 PENDING_CONFIRMATION 与一次性令牌
  -> 前端展示工具名称和参数摘要
  -> 用户确认或拒绝
  -> 服务端校验令牌并再次复核权限
  -> 原子迁移为 EXECUTING
  -> 在写工具事务边界内创建笔记并记录工具审计
  -> 状态变为 SUCCEEDED 或 FAILED
```

普通 `/agent/tool-calls` 接口和 Spring AI 自动工具白名单都不能直接执行写工具。

流式问答识别到“保存笔记”或“生成复习卡片”意图时，会发送 `tool_confirmation_required` 事件。事件只创建确认单，不会自动写入。默认使用可重复的规则判断，也可以显式切换到 Spring AI 结构化输出。

## 状态说明

- `PENDING_CONFIRMATION`：等待用户确认。
- `EXECUTING`：已经取得执行权，正在写入。
- `SUCCEEDED`：写入完成，可查看执行审计和结果。
- `REJECTED`：用户明确拒绝，不再允许执行。
- `EXPIRED`：超过配置有效期，默认五分钟。
- `FAILED`：确认后执行失败，需要重新创建确认单。

后台维护任务每分钟扫描一次状态：

- 超过 `expiresAt` 的待确认单原子迁移为 `EXPIRED`。
- 超过执行时限仍为 `EXECUTING` 的确认单原子迁移为 `FAILED` 并记录恢复原因。
- 对结果不确定的执行中任务绝不自动重放，避免重复创建知识资产。

## 启动方式

确保 Maven 使用 Java 21：

```powershell
$env:JAVA_HOME = "D:\Tools\Java21"
$env:Path = "D:\Tools\Java21\bin;$env:Path"
cd D:\Program\AgentMind\backend
mvn spring-boot:run
```

## 手动联调

以下示例使用知识空间 `1`。

1. 创建确认单：

```powershell
$body = @{
  toolName = "note.create"
  requestId = "manual-note-001"
  arguments = @{
    title = "Java 线程池复习笔记"
    content = "线程池通过复用工作线程降低频繁创建线程的开销。"
  }
} | ConvertTo-Json -Depth 5

$created = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/agent/write-tool-confirmations" `
  -ContentType "application/json" `
  -Body $body

$confirmationId = $created.data.confirmation.id
$confirmationToken = $created.data.confirmationToken
$created.data
```

2. 用户确认后执行：

```powershell
$confirmBody = @{
  confirmationToken = $confirmationToken
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/agent/write-tool-confirmations/$confirmationId/confirm" `
  -ContentType "application/json" `
  -Body $confirmBody
```

3. 查询笔记：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/notes?page=1&pageSize=20"
```

复习卡片查询接口：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/flashcards?page=1&pageSize=20"
```

4. 查询确认状态：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/agent/write-tool-confirmations/$confirmationId"
```

## 建议验证项

- 直接向 `/agent/tool-calls` 提交 `note.create`，应返回 `RESOURCE_CONFLICT`。
- 使用错误确认令牌，应返回 `FORBIDDEN`，且笔记不会创建。
- 对同一确认单重复确认，只会返回既有结果，不会重复创建笔记。
- 使用相同 `requestId` 和相同参数创建新的确认单并确认，应复用成功结果。
- 使用相同 `requestId` 但修改参数，应返回 `RESOURCE_CONFLICT`。
- 拒绝确认单后再次确认，应返回状态冲突。
- 换用其他知识空间或演示用户查询确认单，应无法访问。

## 当前存储边界

默认模式仍使用内存实现，服务重启后数据会清空。使用 `local` 配置启动时会切换为 PostgreSQL 仓储，确认单、工具审计、笔记和复习卡片进入真实数据库事务。具体步骤参见 `AGENT_JDBC_RUNBOOK.md`。

数据库模式下，成功审计与业务写入共享主事务；失败审计使用独立事务。即使业务工具中途抛出异常，业务数据会回滚，但审计列表仍能查询到一条最终 `FAILED` 记录。
