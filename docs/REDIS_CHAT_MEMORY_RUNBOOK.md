# Redis 短期会话记忆联调手册

本文档用于验证 Stage 6 的 Redis 会话记忆适配器、知识空间隔离、空闲过期、序列化版本和助手消息原子终态迁移。

## 实现边界

应用服务仍然只依赖 `ChatMemoryRepository`。默认内存实现和 Redis 实现通过配置切换，同步问答、流式问答、滑动窗口和查询接口不需要修改。

Redis 数据结构如下：

- 字符串：保存版本化会话 JSON。
- 哈希：按会话保存消息 JSON，字段为消息编号。
- 有序集合：保存知识空间会话顺序和会话消息顺序。
- 自增键：生成会话编号和消息编号。

同一知识空间的业务键包含相同哈希标签，例如：

```text
agentmind:chat-memory:v1:workspace:{1}:conversation:100
agentmind:chat-memory:v1:workspace:{1}:conversation:100:messages
agentmind:chat-memory:v1:workspace:{1}:conversation:100:message-order
```

该设计使同一知识空间的数据天然隔离，并允许终态迁移脚本在 Redis 集群的同一哈希槽内执行。

## 启动 Redis

在仓库根目录执行：

```powershell
cd D:\Program\AgentMind
docker compose up -d agentmind-redis
docker compose ps agentmind-redis
```

健康状态应为 `healthy`。本地容器默认监听 `6379`，使用数据卷保存数据，不配置生产密码。

## 使用 Redis 模式启动后端

`redis` profile 会把记忆仓储切换为 Redis：

```powershell
cd D:\Program\AgentMind\backend
mvn spring-boot:run "-Dspring-boot.run.profiles=redis"
```

可以通过环境变量覆盖连接配置：

```powershell
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
$env:REDIS_DATABASE = "0"
$env:REDIS_PASSWORD = ""
```

核心配置：

```yaml
agentmind:
  chat:
    memory:
      store: redis
      key-prefix: agentmind:chat-memory
      serialization-version: 1
      ttl: 7d
```

- `key-prefix` 用于区分开发、测试和生产环境。
- `serialization-version` 同时写入 JSON 并进入键空间；结构升级时应提升版本并制定迁移方案。
- `ttl` 是会话空闲过期时间。读取会话、查询消息、创建消息和迁移助手状态都会刷新相关键。

## 验证持久化与隔离

按照 `docs/CHAT_MEMORY_RUNBOOK.md` 创建两轮会话，然后重启后端。再次查询相同会话编号，消息应继续存在。

检查 Redis 键：

```powershell
docker exec agentmind-redis redis-cli --scan --pattern "agentmind:chat-memory:v1:*"
```

检查某个会话键的剩余有效期：

```powershell
docker exec agentmind-redis redis-cli TTL "agentmind:chat-memory:v1:workspace:{1}:conversation:100"
```

使用其他知识空间编号读取该会话时仍应返回资源不存在。键隔离和序列化文档中的归属字段会执行双重校验。

## 助手消息原子终态

助手消息创建时为 `PENDING`。完成、失败和取消可能由模型线程、超时线程和客户端断连回调并发触发。

Redis 适配器使用 Lua 脚本完成以下原子操作：

1. 读取当前消息。
2. 仅允许 `PENDING` 进入首个最终状态。
3. 写入完整回答或失败原因。
4. 更新会话活跃时间。
5. 刷新会话、消息和索引键的有效期。

后到达的终态信号只能读到已经确定的结果，不能覆盖完整回答。

## 运行真实 Redis 集成测试

集成测试默认跳过，避免日常构建依赖 Docker。启动容器后显式开启：

```powershell
cd D:\Program\AgentMind\backend
$env:AGENTMIND_REDIS_INTEGRATION_TEST = "true"
mvn "-Dtest=RedisChatMemoryRepositoryIntegrationTests" test
Remove-Item Env:AGENTMIND_REDIS_INTEGRATION_TEST
```

测试使用 Redis 第 15 号数据库和随机键前缀，覆盖：

- 进程内重新创建适配器后仍能读取会话。
- 不同知识空间不能读取同一会话。
- 会话及消息键具有正数有效期。
- 两个适配器并发迁移时只能产生一个最终状态。

## 停止服务

停止容器但保留数据卷：

```powershell
docker compose stop agentmind-redis
```

删除数据卷会永久清除本地 Redis 数据，执行前应确认不再需要联调会话。

## 当前限制

- 当前 Redis 仅用于短期会话记忆，用户、知识空间和文档元数据仍未统一持久化。
- 当前没有会话重命名、归档和删除接口。
- 当前滑动窗口使用字符数近似预算，尚未按照实际模型分词器计算令牌数。
- 当前本地 Redis 没有密码和传输加密，生产部署必须使用密钥管理、网络隔离和加密连接。
