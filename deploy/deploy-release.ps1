param(
    [Parameter(Mandatory = $true)]
    [string]$Image,
    [string]$StackName = "agentmind"
)

$ErrorActionPreference = "Stop"
$env:AGENTMIND_BACKEND_IMAGE = $Image

# Swarm 逐个启动新任务；新任务通过就绪检查后才会停止旧任务，失败则按 stack 策略自动回滚。
docker stack deploy --with-registry-auth --compose-file "$PSScriptRoot/docker-stack.yml" $StackName
if ($LASTEXITCODE -ne 0) {
    throw "发布命令执行失败"
}

docker service ps "$StackName`_agentmind-backend" --no-trunc
Write-Host "请持续观察服务收敛状态和 /actuator/health/readiness，再执行容量门禁。"
