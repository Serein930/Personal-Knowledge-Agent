# 生产实测证据闭环手册

## 一、目标与真实性边界

本手册用于在受保护的预发布环境执行最后一轮生产验收。仓库只提供自动化入口和强制门禁，不会使用本地模拟结果代替真实 Vault、异地对象存储、
正式规格资源或公网 TLS 链路。

所有验收结果必须绑定：

- 完整的四十位 Git 提交摘要。
- 带 `sha256` 摘要的不可变候选镜像。
- 受保护的 `staging` GitHub Environment。
- 同一次 GitHub Actions 运行编号与尝试编号。

## 二、Runner 隔离

需要准备两个自托管 Runner：

| 标签 | 职责 | 必需工具 |
| --- | --- | --- |
| `agentmind-staging` | Swarm 预检、秘密轮换、灰度、故障注入和晋级 | Docker、Vault CLI、Cosign、PowerShell 7 |
| `agentmind-staging-dr` | 从异地仓库恢复并覆盖可丢弃灾备环境 | Docker、Vault CLI、PowerShell 7 |

灾备 Runner 不能连接生产数据库，也不能与预发布主集群共享持久卷。它只允许访问专用的可丢弃 Docker Compose 环境和只读备份仓库。

## 三、GitHub Environment 配置

为 `staging` Environment 启用必要审核人、禁止自审并限制允许部署的分支或标签。配置以下变量：

```text
STAGING_BASE_URL
STAGING_WORKSPACE_ID
STAGING_VAULT_AGENT_CONFIG
STAGING_VAULT_RENDERED_DIRECTORY
STAGING_DR_VAULT_AGENT_CONFIG
STAGING_DR_VAULT_RENDERED_DIRECTORY
STAGING_RESTIC_REPOSITORY
STAGING_RESTIC_PASSWORD_FILE
STAGING_BACKUP_REGION
STAGING_DR_ENVIRONMENT
STAGING_DR_COMPOSE_FILE
STAGING_RPO_TARGET_MINUTES
STAGING_RTO_TARGET_MINUTES
```

配置以下受保护秘密：

```text
STAGING_ACCESS_TOKEN
STAGING_BACKUP_ACCESS_KEY_ID
STAGING_BACKUP_SECRET_ACCESS_KEY
STAGING_BACKUP_SESSION_TOKEN
```

优先使用云工作负载身份签发短期备份凭据。如果当前对象存储暂不支持 OIDC，则上述访问密钥必须限制为只读备份桶权限，并设置短期轮换策略。
Vault 渲染目录必须位于内存盘或 Runner 任务结束后自动销毁的受限目录。

## 四、执行顺序

1. 运行 `生产发布门禁`，生成并签名候选镜像。
2. 从 GHCR 复制带 `sha256` 的镜像地址，不能填写标签。
3. 手动运行 `预发布生产证据验收`，填写镜像、并发数、持续时间和 restic 快照。
4. 审核人批准 `staging` Environment 后，工作流执行全部真实验收。
5. 六类证据全部通过后生成发布候选冻结清单，并使用该清单晋级稳定组。
6. 任一任务失败或被取消时，清理任务会把灰度组恢复到稳定镜像。

## 五、证据内容

### 环境预检

`test-production-acceptance-preflight.ps1` 检查 HTTPS 地址、TLS 信任链与剩余有效期、Swarm 管理节点、稳定/灰度/网关副本、就绪探针和
Cosign 签名。预检还会拉取候选镜像，要求受签名 OCI 元数据中的 `org.opencontainers.image.revision` 与验收 Git 提交完全一致。当前节点不是
`staging`、镜像未固定摘要或镜像来自其他提交时，都会在任何变更前失败。

### 容量证据

`production-capacity-test.js` 保存全部 k6 原始指标、门禁表达式和逐项通过状态。默认门禁为错误率小于 1%、检索 P95/P99 小于
800/1500 毫秒、RAG P95/P99 小于 3/6 秒。

### 韧性证据

故障注入报告保存网关失败次数、最大连续失败次数、副本是否恢复和恢复耗时。灾备报告保存备份年龄、恢复耗时、RPO/RTO 目标及
PostgreSQL、MinIO、OpenSearch 恢复检查结果。

### 冻结清单

`complete-production-acceptance.ps1` 要求六类报告全部属于相同提交与镜像，并记录每份报告的 SHA-256。GitHub Actions 制品保留 90 天；
正式发布后还应把冻结清单复制到不可变审计存储。`promote-canary-release.ps1` 默认只接受 24 小时内的冻结清单。

## 六、本地安全测试

本地只能测试门禁逻辑，不得写入假的生产数据：

```powershell
./scripts/tests/production-acceptance-evidence-tests.ps1
```

该测试验证完整证据可以冻结、失败报告会阻断、缺少灾备报告也会阻断。它只在系统临时目录创建数据并在路径边界复核后清理。

预检的负向验证可以在普通开发机执行；由于没有 `AGENTMIND_ENVIRONMENT=staging`，它必须返回失败：

```powershell
./scripts/test-production-acceptance-preflight.ps1 `
  -BaseUrl "https://staging.example.com" `
  -CandidateImage "example/backend@sha256:六十四位摘要" `
  -GitCommit "四十位提交摘要"
```

如果普通开发机能够绕过该限制，视为安全缺陷，不能发布。

## 七、验收完成标准

- GitHub Actions 全部任务成功，清理任务没有运行。
- 冻结清单包含六类证据且所有哈希可以重新计算。
- 稳定服务镜像与冻结清单中的候选镜像完全一致。
- `PERFORMANCE_BASELINE_REPORT.md` 已填入本次实测指标，不保留“待填写”。
- 灾备报告满足约定 RPO/RTO，且演练环境已在报告归档后销毁。
- 监控观察窗口内没有新的错误率、资源耗尽或数据一致性告警。

只有满足这些条件，才能创建正式版本标签并冻结发布候选。
