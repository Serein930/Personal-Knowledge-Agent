# 长期记忆与个性化学习联调手册

## 本阶段边界

本阶段把复习行为沉淀为 FSRS 卡片快照与参数版本、主题学习画像、长期会话摘要、可追踪的每日任务及维护运行状态。默认算法仍为 SM-2；只有显式配置 `agentmind.study.flashcard.algorithm=fsrs` 时才使用 FSRS 快照。

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

少于 50 条历史记录时应返回 `SKIPPED`。样本足够时，优化器按时间切分训练集和验证集，以二元交叉熵拟合完整 FSRS 权重。只有训练、验证损失都改善且 `applyResult=true` 时才增加用户参数版本。

查询版本并回滚：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8081/api/v1/workspaces/1/study/fsrs/profile/versions?page=1&pageSize=20" `
  -Headers @{"X-Demo-User-Id"="1"}

$body = @{targetVersion=0; expectedCurrentVersion=1} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/workspaces/1/study/fsrs/profile/rollback" `
  -Headers @{"X-Demo-User-Id"="1"} `
  -ContentType "application/json" `
  -Body $body
```

回滚后应产生新的 `ROLLBACK` 版本，目标版本和中间版本仍能查询。

## 会话生命周期

创建会话后依次调用 `pause`、`resume` 和 `abandon`。暂停或放弃后提交未完成卡片评分必须返回 `RESOURCE_CONFLICT`。分页历史接口应保留 `pausedAt`、`abandonedAt` 和最后更新时间。

## 个性化计划

卡片可以带 `topic` 和 `sourceDocumentId`。创建每日计划时提交偏好主题和文档编号，响应中的任务应解释来源：

- `DUE_REVIEW`：计划日期前已经到期。
- `WEAK_POINT_REVIEW`：卡片存在历史回忆失败。
- `TOPIC_REVIEW`：匹配用户指定主题。
- `DOCUMENT_REVIEW`：匹配用户指定来源文档。
- `MASTERY_REINFORCEMENT`：主题画像被判定为薄弱或有风险。
- `CONVERSATION_REVIEW`：近期完整会话摘要识别到用户明确表达的薄弱主题。

Agent 写工具名为 `study_plan.create`。创建确认单后先查询计划，应返回不存在；确认令牌成功后再次查询，计划和任务才应出现。

## 每日任务状态验证

从计划响应取一个任务编号，依次调用完成和反馈接口。每一步都必须提交最近读取到的 `expectedVersion`：

```powershell
$complete = @{expectedVersion=0; comment="已完成复习"} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/workspaces/1/study-tasks/1/complete" `
  -ContentType "application/json" -Body $complete

$feedback = @{expectedVersion=1; score=5; comment="内容有效"} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8081/api/v1/workspaces/1/study-tasks/1/feedback" `
  -ContentType "application/json" -Body $feedback
```

事件查询应返回 `COMPLETED` 与 `FEEDBACK_RECORDED` 两条不可变记录。跳过与重新安排使用相同乐观锁语义。

## 画像、摘要与维护任务

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/v1/workspaces/1/study/learning-profile/refresh"
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/v1/workspaces/1/study/conversation-summaries/refresh"
Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/v1/workspaces/1/study/maintenance/run"
Invoke-RestMethod -Method Get  -Uri "http://localhost:8081/api/v1/workspaces/1/study/maintenance/status"
```

后台调度默认关闭。需要自动执行时设置 `agentmind.study.maintenance.enabled=true`；默认每 6 小时运行，FSRS 优化至少间隔 7 天。维护任务会补偿已发生真实复习但仍为待完成的任务，并把逾期未完成任务重新安排到当天。

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
$env:AGENTMIND_POSTGRES_JDBC_URL = "jdbc:postgresql://localhost:55432/agentmind"
mvn -Dtest=JdbcStudyFlashcardReviewIntegrationTests,JdbcAgentWriteToolIntegrationTests,JdbcStudyPersonalizationIntegrationTests test
```

若本机 `5432` 已被占用，可在仓库根目录这样启动容器：

```powershell
$env:AGENTMIND_POSTGRES_PORT = "55432"
docker compose up -d agentmind-postgres
```

测试会初始化并清空本地学习系统相关表，只能连接本地开发数据库。覆盖 FSRS 快照与参数版本、并发评分、画像快照、会话摘要、任务事件、计划事务和确认式写工具。
