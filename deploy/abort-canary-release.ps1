param(
    [string]$StackName = "agentmind"
)

$ErrorActionPreference = "Stop"
$canaryService = "$StackName`_agentmind-backend-canary"
$stableService = "$StackName`_agentmind-backend"
$stableImage = docker service inspect $stableService --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}'
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($stableImage)) {
    throw "无法读取稳定组镜像"
}
docker service update --detach=false --image $stableImage $canaryService
if ($LASTEXITCODE -ne 0) {
    throw "终止灰度失败，请人工检查服务状态"
}
Write-Host "灰度组已恢复为稳定组镜像。"
