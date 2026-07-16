# 复习卡片调度联调手册

## 本阶段范围

本阶段已完成复习卡片状态、到期查询、评分记录、SM-2 调度、FSRS 状态快照、用户参数、优化任务、会话生命周期、个性化每日任务、学习趋势和前端复习工作台。

默认配置：

```yaml
agentmind:
  study:
    flashcard:
      algorithm: sm2
```

`SpacedRepetitionAlgorithm` 是稳定算法端口，`Sm2SpacedRepetitionAlgorithm` 是当前默认实现。后续增加 FSRS 时应新增实现和配置值，不应把算法判断写入控制层或仓储层。

切换官方 Java-FSRS 适配器：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--agentmind.study.flashcard.algorithm=fsrs"
```

FSRS 适配器会优先读取持久化卡片快照，仅在旧卡没有快照或版本不兼容时按时间正序重放历史评分。当前优化任务校准用户期望保持率，不把该过程描述成 FSRS 权重训练。默认开发、自动测试和常规联调仍使用 SM-2。

## 复习工作流接口

```text
GET  /api/v1/workspaces/{workspaceId}/flashcards/statistics
POST /api/v1/workspaces/{workspaceId}/review-sessions
GET  /api/v1/workspaces/{workspaceId}/review-sessions/{sessionId}
GET  /api/v1/workspaces/{workspaceId}/review-sessions?page=1&pageSize=20
POST /api/v1/workspaces/{workspaceId}/review-sessions/{sessionId}/pause
POST /api/v1/workspaces/{workspaceId}/review-sessions/{sessionId}/resume
POST /api/v1/workspaces/{workspaceId}/review-sessions/{sessionId}/abandon
POST /api/v1/workspaces/{workspaceId}/review-sessions/{sessionId}/cards/{flashcardId}/reviews
POST /api/v1/workspaces/{workspaceId}/study-plans/daily
GET  /api/v1/workspaces/{workspaceId}/study-plans/daily?date=YYYY-MM-DD
GET  /api/v1/workspaces/{workspaceId}/study/analytics/trends?from=YYYY-MM-DD&to=YYYY-MM-DD
GET  /api/v1/workspaces/{workspaceId}/study/fsrs/profile
POST /api/v1/workspaces/{workspaceId}/study/fsrs/optimization-jobs
```

前端启动后进入“学习计划”导航即可使用真实复习工作台：

```powershell
cd D:\Program\AgentMind\ui
npm run dev
```

页面包括到期数量、今日完成、连续学习、遗忘率、每日目标、固定复习队列、答案揭示、0 至 5 分评分、评分分布、成熟度和卡片暂停/恢复。

## 评分语义

| 评分 | 含义 | 当前调度结果 |
| --- | --- | --- |
| 0 | 完全忘记 | 回到学习状态，1 天后复习 |
| 1 | 仅有模糊印象 | 回到学习状态，1 天后复习 |
| 2 | 回忆失败 | 回到学习状态，1 天后复习 |
| 3 | 勉强记住 | 按 SM-2 推进，难度系数下降 |
| 4 | 正常记住 | 按 SM-2 推进，难度系数保持稳定 |
| 5 | 轻松记住 | 按 SM-2 推进，难度系数上升 |

首次成功复习间隔为 1 天，第二次为 6 天，后续基于上次间隔和难度系数计算。难度系数最低为 1.3。

## 内存模式手动联调

启动后端：

```powershell
$env:JAVA_HOME = "D:\Tools\Java21"
$env:Path = "D:\Tools\Java21\bin;$env:Path"
cd D:\Program\AgentMind\backend
mvn spring-boot:run
```

先通过 `flashcard.create` 写工具确认流程创建一张卡片，再查询到期列表：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/flashcards/due?page=1&pageSize=20" `
  -Headers @{"Authorization"="Bearer $env:AGENTMIND_ACCESS_TOKEN"}
```

提交评分时将 `{flashcardId}` 替换为实际卡片编号：

```powershell
$body = @{requestId="manual-review-001"; score=5} | ConvertTo-Json
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/flashcards/{flashcardId}/reviews" `
  -ContentType "application/json" `
  -Headers @{"Authorization"="Bearer $env:AGENTMIND_ACCESS_TOKEN"} `
  -Body $body
```

使用相同请求体再次提交时，响应中的 `reused` 应为 `true`，卡片 `version` 不应再次增加。随后查询复习历史：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/flashcards/{flashcardId}/reviews?page=1&pageSize=20" `
  -Headers @{"Authorization"="Bearer $env:AGENTMIND_ACCESS_TOKEN"}
```

## 自动测试

运行默认测试：

```powershell
cd D:\Program\AgentMind\backend
mvn test
```

默认测试覆盖 SM-2 计算、失败重置、参数校验、到期查询、重复提交、知识空间隔离和内存并发。

## PostgreSQL 手动集成测试

先启动本地 PostgreSQL 并初始化脚本：

```powershell
cd D:\Program\AgentMind
docker compose up -d agentmind-postgres
docker exec agentmind-postgres psql -U agentmind -d agentmind -f /docker-entrypoint-initdb.d/03-agent-write-tools.sql
```

再运行非默认集成测试：

```powershell
cd D:\Program\AgentMind\backend
$env:AGENTMIND_AGENT_JDBC_INTEGRATION_TEST = "true"
mvn -Dtest=JdbcStudyFlashcardReviewIntegrationTests test
```

该测试会清空本地数据库中的复习卡片与复习记录，只能连接本地开发数据库。它验证不同评分请求并发执行时不丢失更新，以及同一请求并发重试时只生成一条记录。

## 数据检查

```sql
select id, status, repetition_count, interval_days, ease_factor,
       lapse_count, due_at, last_reviewed_at, version
from study_flashcards
order by id desc;

select flashcard_id, request_id, score, previous_status, next_status,
       previous_interval_days, next_interval_days, algorithm, reviewed_at
from study_flashcard_reviews
order by id desc;

select id, status, total_cards, reviewed_cards, correct_cards,
       started_at, paused_at, completed_at, abandoned_at
from study_review_sessions
order by id desc;

select plan_date, daily_review_target, due_card_snapshot, updated_at
from daily_study_plans
order by plan_date desc;

select plan_id, task_type, priority, topic, source_document_id,
       target_card_count, reason
from daily_study_tasks
order by plan_id desc, id;

select flashcard_id, algorithm_version, schema_version,
       payload ->> 'stability' as stability,
       payload ->> 'difficulty' as difficulty,
       updated_at
from study_flashcard_fsrs_states
order by updated_at desc;
```

每次成功评分应满足：

1. 卡片版本号只增加 1。
2. 复习记录保存评分前后调度快照。
3. 相同请求编号不会产生第二条记录。
4. 到期时间只由算法结果决定。
5. 其他用户或知识空间无法读取和评分当前卡片。
