# 正式版本发布手册

## 一、发布前提

正式版本发布只负责把已经完成最终人工审批的提交固化为 GitHub Release。它不会重新选择镜像，也不会接受本地审批文件。

必须先具备：

- “发布候选全链路验收”成功。
- “真实预发布前后端全链路验收”成功。
- “最终发布审批”成功。
- 最终审批工作流运行编号。
- 与审批产物完全相同的 Git 提交。

## 二、配置正式发布环境

创建 `production-release` GitHub Environment，并配置必要审核人、禁止自审和默认分支保护。增加环境秘密：

```text
PRODUCTION_RELEASE_GUARD
```

该随机守卫只证明环境由维护者显式初始化，不参与应用认证。缺少守卫时工作流会失败，避免 GitHub 自动创建无保护 Environment 后直接发布。

## 三、执行发布

在 GitHub Actions 中选择“正式版本发布”，填写：

- `version`：例如 `v1.0.0` 或 `v1.0.0-rc.1`。
- `final_approval_run_id`：成功的“最终发布审批”运行编号。
- `prerelease`：候选版本选择 `true`，正式稳定版本选择 `false`。

验证作业会下载指定运行的审批产物，核对审批包 SHA-256、提交、候选镜像摘要和审批环境，然后生成正式发布清单。验证成功后，工作流进入 `production-release` Environment 等待人工批准。

## 四、幂等与冲突规则

- 同一版本不存在时，工作流创建指向当前已审批提交的 GitHub Release。
- 同一版本已存在且指向相同提交时，重试会复用现有 Release。
- 同一版本已存在但指向其他提交时，工作流立即失败。
- 禁止使用 `latest`、分支名或不带 `v` 的版本。
- GitHub Release 说明记录不可变候选镜像、发布候选编号和最终审批运行编号。

## 五、本地边界测试

```powershell
./scripts/tests/production-release-tests.ps1
```

该测试验证语义化版本、审批包哈希和工作流保护契约，不会创建 Git 标签、GitHub Release 或生产部署。

## 六、部署边界

GitHub Release 表示代码和镜像版本已经正式固化，不等于生产流量已经完成切换。若后续接入独立生产集群，应继续使用摘要镜像、受保护部署环境、滚动发布、自动回滚和发布后冒烟，不得从版本标签重新构建镜像。

独立生产集群的 Runner、Environment、灰度发布、发布后冒烟和自动回滚步骤见 `INDEPENDENT_PRODUCTION_DEPLOYMENT_RUNBOOK.md`。
