# 长期记忆与个性化学习联调手册

## 本阶段边界

本阶段把复习行为沉淀为三类长期数据：FSRS 卡片状态快照、用户级参数档案、可解释的每日学习任务。默认算法仍为 SM-2；只有显式配置 `agentmind.study.flashcard.algorithm=fsrs` 时才使用 FSRS 快照。

## FSRS 快照验证

使用 FSRS 启动后端并完成同一张卡片的两次评分：

```powershell
cd D:\Program\AgentMind\backend
$env:JAVA_HOME = "D:\Tools\Java21"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn spring-boot:run "-Dspring-boot.run.arguments=--agentmind.study.flashcard.algorithm=fsrs"
```

JDBC 模式下，第一次评分后 `study_flashcard_fsrs_states` 应出现一条记录；第二次评分更新同一条记录，`created_at` 不变、`updated_at` 变化。快照同时记录用户参数版本，参数改变后自动进行一次历史重建。评分事务失败时，卡片、评分记录和快照必须一起回滚。

## 用户参数与优化任务

读取默认参数：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8081/api/v1/workspaces/1/study/fsrs/profile" `
  -Headers @{"X-Demo-User-Id"="1"}
```

启动只生成建议、不自动应用的优化任务：

```powershell
$body = @{applyResult=$false} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/workspaces/1/study/fsrs/optimization-jobs" `
  -Headers @{"X-Demo-User-Id"="1"} `
  -ContentType "application/json" `
  -Body $body
```

少于 20 条历史记录时应返回 `SKIPPED`。样本足够时返回 `SUCCEEDED` 和推荐保持率；只有 `applyResult=true` 才增加用户参数版本。

## 会话生命周期

创建会话后依次调用 `pause`、`resume` 和 `abandon`。暂停或放弃后提交未完成卡片评分必须返回 `RESOURCE_CONFLICT`。分页历史接口应保留 `pausedAt`、`abandonedAt` 和最后更新时间。

## 个性化计划

卡片可以带 `topic` 和 `sourceDocumentId`。创建每日计划时提交偏好主题和文档编号，响应中的任务应解释来源：

- `DUE_REVIEW`：计划日期前已经到期。
- `WEAK_POINT_REVIEW`：卡片存在历史回忆失败。
- `TOPIC_REVIEW`：匹配用户指定主题。
- `DOCUMENT_REVIEW`：匹配用户指定来源文档。

Agent 写工具名为 `study_plan.create`。创建确认单后先查询计划，应返回不存在；确认令牌成功后再次查询，计划和任务才应出现。

## 趋势验证

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8081/api/v1/workspaces/1/study/analytics/trends?from=2026-07-01&to=2026-07-31" `
  -Headers @{"X-Demo-User-Id"="1"}
```

检查每日复习总数等于当日评分记录数，不同卡片数不会因重复评分增加，周聚合从周一开始，空白日期仍返回零值日数据。

## PostgreSQL 非默认测试

```powershell
cd D:\Program\AgentMind\backend
$env:AGENTMIND_AGENT_JDBC_INTEGRATION_TEST = "true"
mvn -Dtest=JdbcStudyFlashcardReviewIntegrationTests,JdbcAgentWriteToolIntegrationTests test
```

测试会初始化并清空本地学习系统相关表，只能连接本地开发数据库。覆盖 FSRS 快照持久化、并发评分、计划任务事务和确认式写工具。
