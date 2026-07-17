# 生产验收最终阶段运行手册

## 一、交付边界

本阶段把生产发布补齐为可审计流程：TLS 网关、可信代理校验、Vault 渲染和版本化秘密轮换、
客户端加密异地备份、RPO/RTO 演练、镜像 SBOM、漏洞门禁、OIDC 无密钥签名、灰度发布、k6 容量门禁和故障注入。

仓库提供的是可执行配置和自动化入口。真实证书、云 KMS、Vault、异地对象存储、GitHub staging 环境及正式规格机器
都属于部署环境资源，不得用示例值伪造验收结果。

## 二、TLS 网关与可信代理

生产栈不再发布后端 `8081`，只有 Nginx 网关发布 `80/443`。HTTP 只执行永久跳转，HTTPS 限制为 TLS 1.2/1.3，
并统一覆盖外部传入的转发头。网关将约 90% 流量发送到两个稳定实例，约 10% 发送到一个灰度实例。

首次部署应创建加密覆盖网络：

```powershell
docker network create --driver overlay --opt encrypted agentmind-production
```

若同名网络已经承载服务，不要在线删除重建，应在维护窗口创建新网络并滚动迁移。根据覆盖网络实际 CIDR 配置：

```powershell
$env:AGENTMIND_TRUSTED_PROXY_CIDRS="10.0.0.0/8"
```

应用只有在请求携带转发头时才校验来源 CIDR；容器内部不携带转发头的健康探针仍然可用。生产配置强制要求
`X-Forwarded-Proto=https`，并拒绝绕过网关伪造 `X-Forwarded-For` 的请求。

首次创建秘密时传入真实证书文件：

```powershell
./deploy/create-swarm-secrets.ps1 `
  -TlsCertificatePath "D:/secure/tls/fullchain.pem" `
  -TlsPrivateKeyPath "D:/secure/tls/private.key"
```

## 三、Vault、KMS 与秘密轮换

`deploy/vault/agent.hcl.example` 使用 AppRole 最小权限认证，将运行秘密和 TLS 材料渲染到受限目录。
`deploy/vault/policy.hcl` 只授予两个 AgentMind 路径的读取权限。Vault 服务端可参考
`server-kms-auto-unseal.hcl.example` 使用云 KMS 自动解封；云凭据必须来自实例角色，不能写入 HCL 或仓库。

推荐轮换顺序：

1. 在 Vault 中写入新版本，保留旧版本用于短期回滚。
2. 在受控发布节点运行 Vault Agent，把秘密渲染到内存盘。
3. 调用版本化轮换脚本，先更新灰度组，再更新稳定组和网关。
4. 验证数据库、Redis、MinIO、OpenSearch、OIDC 和 TLS 后再清理旧 Docker Secret。

```powershell
vault agent -config=deploy/vault/agent.hcl
./deploy/rotate-swarm-secrets.ps1 `
  -RenderedSecretDirectory "R:/vault/rendered" `
  -StackName agentmind
```

Swarm Secret 不可原地修改，因此脚本会创建带时间戳的新名称，并把当前名称写入被 Git 忽略的
`deploy/.secret-versions.env`。后续 `deploy-release.ps1` 会读取该文件，防止再次部署时退回旧秘密。

## 四、异地加密备份与灾备

先使用 `backup-production.ps1` 生成 PostgreSQL、MinIO、OpenSearch 一致性备份和 SHA-256 清单，再用 restic
在客户端加密后同步到 S3 兼容异地存储。restic 密码必须放在权限受限的外部文件中。

```powershell
$env:AWS_ACCESS_KEY_ID="由临时凭据注入"
$env:AWS_SECRET_ACCESS_KEY="由临时凭据注入"
$env:AWS_SESSION_TOKEN="可选的短期会话令牌"
$env:AWS_DEFAULT_REGION="ap-southeast-1"

./scripts/sync-offsite-backup.ps1 `
  -BackupDirectory ".agentmind-backups/20260717-010000" `
  -Repository "s3:https://s3.example.com/agentmind-disaster-recovery" `
  -PasswordFile "D:/secure/restic/password"
```

异地恢复先落到空目录并验证全部哈希：

```powershell
./scripts/restore-offsite-backup.ps1 `
  -DestinationDirectory "D:/drill/offsite-restore" `
  -Repository "s3:https://s3.example.com/agentmind-disaster-recovery" `
  -PasswordFile "D:/secure/restic/password"
```

完整演练只能在可丢弃环境执行。确认文本必须包含目标环境名称：

```powershell
$env:AGENTMIND_MINIO_ACCESS_KEY="演练环境访问密钥"
$env:AGENTMIND_MINIO_SECRET_KEY="演练环境秘密密钥"
./scripts/run-disaster-recovery-drill.ps1 `
  -BackupDirectory "D:/drill/offsite-restore/backup" `
  -EnvironmentName "staging-drill-01" `
  -ConfirmationText "DISPOSABLE:staging-drill-01" `
  -RpoTargetMinutes 1440 `
  -RtoTargetMinutes 60
```

报告写入 `.disaster-recovery-reports`，包含备份年龄、恢复耗时、RPO/RTO 是否达标以及 PostgreSQL、MinIO、
OpenSearch 检查结果。异地对象存储还应开启版本控制、对象锁和跨账号保留策略。

## 五、SBOM、漏洞扫描和镜像签名

`.github/workflows/production-release-gate.yml` 是生产镜像唯一推荐入口，按以下顺序执行：

1. 常规测试和真实 PostgreSQL、pgvector、Redis、MinIO、OpenSearch、OIDC 测试。
2. 构建本地门禁镜像并生成 CycloneDX SBOM。
3. 使用 Trivy 扫描 HIGH 和 CRITICAL 漏洞，未修复漏洞也会进入 SARIF 报告。
4. 扫描通过后才推送 GHCR。
5. 使用 GitHub OIDC 进行 Cosign 无密钥签名，并生成 provenance 与 SBOM attestation。

普通手动运行默认不发布；只有版本标签或明确选择 `publish=true` 才会写入镜像仓库。仓库还配置了 Dependabot，
每周检查 GitHub Actions 和 Maven 依赖更新。

发布后可验证签名：

```powershell
cosign verify `
  --certificate-identity-regexp "https://github.com/Serein930/Personal-Knowledge-Agent/.github/workflows/production-release-gate.yml@.*" `
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" `
  "ghcr.io/serein930/personal-knowledge-agent/backend@sha256:替换为真实摘要"
```

## 六、灰度发布、容量门禁和故障注入

首次部署时灰度组和稳定组使用相同镜像：

```powershell
./deploy/deploy-release.ps1 -Image "ghcr.io/example/agentmind/backend@sha256:稳定摘要"
```

只更新 10% 灰度组：

```powershell
./deploy/canary-release.ps1 -Image "ghcr.io/example/agentmind/backend@sha256:候选摘要"
```

观察完整监控窗口并通过全部验收门禁后，先冻结六类证据再晋级；异常则终止灰度：

```powershell
./scripts/complete-production-acceptance.ps1 `
  -EvidenceDirectory ".production-acceptance-evidence" `
  -CandidateImage "ghcr.io/example/agentmind/backend@sha256:候选摘要" `
  -GitCommit "完整的四十位提交摘要"

./deploy/promote-canary-release.ps1 `
  -AcceptanceManifest ".production-acceptance-reports/rc-清单.json" `
  -EvidenceDirectory ".production-acceptance-evidence"
./deploy/abort-canary-release.ps1
```

GitHub Actions 的 `预发布生产证据验收` 工作流使用受保护的 `staging` Environment。基础配置包括：

```text
变量 STAGING_BASE_URL
变量 STAGING_WORKSPACE_ID
秘密 STAGING_ACCESS_TOKEN
```

容量任务在 GitHub 托管节点运行，原始 `performance-summary.json` 保存为制品。故障注入只会在带有
`agentmind-staging` 标签的自托管节点运行，且同时要求 `AGENTMIND_ENVIRONMENT=staging` 和显式确认；脚本只终止
一个稳定组任务，验证网关连续性和 Swarm 自动补副本能力。

## 七、正式验收清单

- 后端端口不能从公网直接访问，HTTP 自动跳转 HTTPS。
- 伪造转发头请求返回 `UNTRUSTED_PROXY`。
- Vault 审计日志能定位每次生产秘密读取，轮换后旧凭据失效。
- 异地备份可恢复且 RPO/RTO 报告达标。
- 发布镜像具有 CycloneDX SBOM、Trivy 报告、Cosign 签名和 GitHub attestation。
- k6 错误率和各阶段 P95/P99 满足 `PERFORMANCE_BASELINE_REPORT.md` 门限。
- 单实例故障期间网关连续失败不超过一次，Swarm 能恢复目标副本数。
- 灰度异常能够恢复为稳定镜像，稳定组升级失败能够自动回滚。

只有上述证据均来自真实预发布环境，才能把版本标记为生产验收通过。

## 八、生产实测证据闭环

完整自动化入口是 `.github/workflows/staging-acceptance.yml`。工作流按固定顺序执行环境预检、Cosign 签名验证、
Vault Agent 渲染、Docker Secret 版本化轮换、10% 灰度部署、外部 k6、单实例故障注入、隔离灾备恢复、证据冻结和稳定组晋级。

每份证据使用统一 `1.0` 结构，必须绑定同一个 Git 提交、同一个不可变镜像摘要和 `staging` 环境。冻结脚本要求以下六类证据全部存在且
`passed=true`：

- `preflight`
- `secret_rotation`
- `canary_release`
- `capacity`
- `fault_injection`
- `disaster_recovery`

冻结清单会保存每份原始 JSON 的 SHA-256。晋级脚本会再次计算哈希、检查清单时效、当前提交和灰度服务镜像；任何报告被修改、缺失、过期或来自
其他版本时都会拒绝更新稳定组。完整 Runner、Vault、restic 和 GitHub Environment 配置见
`PRODUCTION_ACCEPTANCE_EVIDENCE_RUNBOOK.md`。
