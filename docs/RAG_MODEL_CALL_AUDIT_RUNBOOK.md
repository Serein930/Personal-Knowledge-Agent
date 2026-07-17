# 模型调用观测审计联调手册

本文档用于验证 Stage 6 的模型调用观测记录写入、知识空间隔离、状态过滤和分页查询能力。

## 默认内存模式

默认配置使用内存仓库：

```yaml
agentmind:
  rag:
    observation-store: memory
```

内存模式适合日常开发和自动化测试，服务重启后观测记录会清空。

## 产生观测记录

调用一次检索增强生成问答接口：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/workspaces/1/rag/chat" `
  -ContentType "application/json" `
  -Body '{"question":"线程池为什么可以复用工作线程？","topK":5}'
```

每次回答生成完成后会保存一条最终记录：

- `SUCCEEDED`：模型或模拟生成器正常完成。
- `FAILED`：真实模型调用失败，并且关闭了失败降级。
- `FALLBACK`：真实模型调用失败，系统返回降级回答。
- `CANCELLED`：流式回答因客户端断开或会话超时而取消。

调用开始状态只写应用日志，不单独落库，保证一次问答只产生一条最终审计记录。

## 查询审计接口

查询知识空间内的模型调用记录：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/rag/model-calls?page=1&pageSize=20"
```

按状态过滤：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/rag/model-calls?page=1&pageSize=20&status=FALLBACK"
```

响应记录包含：

- 提示词版本。
- 回答生成器类型。
- 模型名称。
- 引用数量。
- 是否拒答。
- 调用状态。
- 调用耗时。
- 回答长度。
- 失败原因。
- 创建时间。

接口不会返回完整提示词、用户问题或检索正文，避免审计查询泄露个人知识内容。

## 查询聚合指标

查询当前知识空间的模型调用总体指标和分组指标：

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/workspaces/1/rag/model-calls/metrics"
```

响应包含：

- 当前知识空间的总调用次数、成功次数、降级次数、失败次数和取消次数。
- 总体成功率、降级率和平均耗时。
- 按“模型名称 + 提示词版本”组合生成的指标分组。
- 每个分组各自的调用次数、成功率、降级率和平均耗时。

成功率和降级率使用 `0` 到 `1` 的小数表示。例如 `0.7500` 表示 75%。平均耗时单位为毫秒。
当前接口统计知识空间内的全部历史记录，时间范围筛选和趋势序列留到评估面板阶段扩展。

## 切换数据库模式

数据库适配器复用项目本地 PostgreSQL 数据源。先启动数据库：

```powershell
cd D:\Program\AgentMind
docker compose up -d agentmind-postgres
```

新建数据库和已有数据库升级都由 Flyway 执行：

```text
backend/src/main/resources/db/migration/V5__reconcile_rag_model_call_observations.sql
```

禁止手工执行 `db/schema` 下的旧基线脚本。Flyway 会根据 `flyway_schema_history` 自动判断是否需要升级。

确认表结构存在后，使用本地配置并切换观测仓库：

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run `
  "-Dspring-boot.run.profiles=local" `
  "-Dspring-boot.run.arguments=--agentmind.rag.observation-store=jdbc"
```

数据库模式和内存模式实现同一个仓储端口，控制层与应用服务不需要修改。

## 当前边界

- 当前没有用户认证，接口暂时只校验知识空间编号；后续必须增加知识空间归属校验。
- 当前令牌用量仍未从真实模型响应中提取。
- 数据库脚本暂时由容器初始化或开发者手动执行，后续应引入数据库迁移工具。
- 当前接口服务于开发联调，后续评估面板可以直接消费该分页契约。
- 聚合指标尚未包含令牌消耗、用户反馈和时间趋势，这些字段需要在观测记录扩充后继续补充。
