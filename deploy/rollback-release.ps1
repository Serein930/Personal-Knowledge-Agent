param(
    [string]$StackName = "agentmind"
)

$ErrorActionPreference = "Stop"
$serviceName = "$StackName`_agentmind-backend"

# 回滚使用 Swarm 保存的上一版服务规范，不需要手工猜测旧镜像标签。
docker service rollback $serviceName
if ($LASTEXITCODE -ne 0) {
    throw "服务回滚命令执行失败"
}
docker service ps $serviceName --no-trunc
