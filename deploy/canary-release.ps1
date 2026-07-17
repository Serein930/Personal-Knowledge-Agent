param(
    [Parameter(Mandatory = $true)]
    [string]$Image,
    [string]$StackName = "agentmind",
    [string]$OverlayNetwork = "agentmind-production"
)

$ErrorActionPreference = "Stop"
$serviceName = "$StackName`_agentmind-backend-canary"

# 先只替换 10% 流量对应的灰度服务，稳定组镜像保持不变。
docker service update --with-registry-auth --detach=false --image $Image $serviceName
if ($LASTEXITCODE -ne 0) {
    throw "灰度服务更新失败，Swarm 将按服务策略回滚"
}

# 从内部覆盖网络直连灰度服务，避免公网加权分流使检查结果不确定。
docker run --rm --network $OverlayNetwork --entrypoint wget $Image `
    -qO- -T 3 -t 12 "http://$serviceName`:8081/actuator/health/readiness" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "灰度实例就绪检查失败，请执行 abort-canary-release.ps1"
}
docker service ps $serviceName --no-trunc
Write-Host "灰度实例已承接约 10% 流量。完成 k6、错误率和链路指标观察后才能晋级。"
