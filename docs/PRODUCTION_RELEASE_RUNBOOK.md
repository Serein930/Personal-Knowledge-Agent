# 生产发布收尾手册

## 本阶段目标

本阶段将知识索引链路从“同步双写”升级为“PostgreSQL 权威数据 + 事务 Outbox + OpenSearch 最终一致性”，并补齐数据库迁移、批量重建、可观测、全链路 CI、并发租约和故障恢复验证。

## 一致性模型

1. 文档片段完成 Embedding 后，在一个 Spring 数据库事务中替换 pgvector 记录并写入 Outbox。
2. 事务任一动作失败时，向量和消息同时回滚，不会产生只有一侧成功的半成品。
3. 后台消费者使用 `FOR UPDATE SKIP LOCKED` 领取消息，多实例不会重复领取同一租约。
4. OpenSearch 写入失败时按指数退避重试，超过上限进入 `DEAD`，不会静默丢失。
5. OpenSearch 写入具备幂等文档编号，重复投递只会覆盖相同片段。
6. pgvector 始终是可重建的权威片段来源，OpenSearch 可以删除后全量恢复。

## 本地启动

启动 PostgreSQL、Redis 和 OpenSearch：

```powershell
docker compose --profile opensearch up -d
```

以生产索引模式启动后端：

```powershell
$env:AGENTMIND_KEYWORD_INDEX_TYPE="opensearch"
$env:AGENTMIND_KNOWLEDGE_INDEX_OUTBOX_ENABLED="true"
$env:AGENTMIND_INSTANCE_ID="local-1"
mvn -Dspring-boot.run.profiles=local spring-boot:run
```

Flyway 会在应用启动时自动执行迁移。旧数据库首次接入时使用 `baseline-on-migrate` 建立基线，后续禁止再手工修改表结构，应新增版本化迁移。

## 运维接口

以下接口仅在 Outbox 开启时注册，当前阶段使用 `X-Demo-User-Id: 1`，正式认证接入后改由 Spring Security 身份提供。

```text
GET  /api/v1/workspaces/{workspaceId}/knowledge/index-operations/outbox
POST /api/v1/workspaces/{workspaceId}/knowledge/index-operations/outbox/process-once
POST /api/v1/workspaces/{workspaceId}/knowledge/index-operations/rebuild
```

重建接口按文档编号游标分页读取 pgvector，只负责投递消息，不在 HTTP 请求中逐条等待 OpenSearch 完成。每次重建会生成新的事务消息，OpenSearch 通过稳定的片段文档编号保证重复消费幂等。

## 可观测环境

```powershell
docker compose --profile observability up -d
```

- Grafana：`http://localhost:3000`，本地默认账号 `admin`，密码 `agentmind_dev_password`。
- Prometheus：`http://localhost:9090`。
- Tempo：`http://localhost:3200`。
- 后端指标：`http://localhost:8081/actuator/prometheus`。

启用链路上报：

```powershell
$env:AGENTMIND_TRACING_ENABLED="true"
$env:AGENTMIND_OTLP_TRACING_ENDPOINT="http://localhost:4318/v1/traces"
```

面板已预置后端可用性、索引成功与失败速率、HTTP P95。告警覆盖后端不可用、死信、重试积压和评估任务租约异常。

## 测试与故障注入

普通测试：

```powershell
cd backend
mvn test
```

容器全链路测试：

```powershell
$env:AGENTMIND_RUN_PRODUCTION_INTEGRATION="true"
mvn -Dtest=ProductionKnowledgeIndexPipelineIntegrationTests test
```

全链路测试验证 Flyway、pgvector、事务回滚、OpenSearch 批量索引和多实例互斥领取。`KnowledgeIndexOutboxWorkerTests` 使用故障适配器验证达到重试上限后进入死信。

多实例压测时，先启动至少两个不同 `AGENTMIND_INSTANCE_ID` 的后端并放到同一负载均衡入口，然后执行：

```powershell
k6 run -e BASE_URL=http://localhost:8080 -e WORKSPACE_ID=1 -e VUS=40 -e DURATION=2m scripts/knowledge-index-load-test.js
```

OpenSearch 故障演练：先上传测试文档，再暂停 `agentmind-opensearch` 容器，观察重试指标增长；恢复容器后确认消息转为完成。若超过最大次数进入死信，修复根因后执行重建接口生成新快照事件。

## 发布检查

1. 备份 PostgreSQL，并在预发布环境验证 Flyway。
2. 为每个实例配置唯一 `AGENTMIND_INSTANCE_ID`。
3. 确认 pgvector 与 OpenSearch 配置同时启用后再打开 Outbox。
4. 执行全链路 CI、固定 RAG 质量门禁和多实例压测。
5. 检查死信为零、HTTP P95 达标、重试无持续增长。
6. 验证 OpenSearch 重建和 PostgreSQL 恢复流程。
7. 生产环境替换 Grafana 默认密码，并配置真实告警通知渠道。
