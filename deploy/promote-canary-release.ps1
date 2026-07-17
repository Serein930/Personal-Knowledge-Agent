param(
    [string]$StackName = "agentmind"
)

$ErrorActionPreference = "Stop"
$canaryService = "$StackName`_agentmind-backend-canary"
$stableService = "$StackName`_agentmind-backend"
$canaryImage = docker service inspect $canaryService --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($canaryImage)) {
    throw "无法读取灰度镜像"
}

# 稳定组仍按 start-first 逐实例替换；任一实例未通过健康监控都会自动回滚。
docker service update --with-registry-auth --detach=false --image $canaryImage $stableService
if ($LASTEXITCODE -ne 0) {
    throw "灰度晋级失败，稳定服务已按策略回滚"
}
docker service ps $stableService --no-trunc
Write-Host "灰度版本已晋级到稳定组，请继续观察完整监控窗口。"
