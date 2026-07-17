param(
    [Parameter(Mandatory = $true)]
    [string]$Image,
    [string]$CanaryImage = "",
    [string]$SecretVersionFile = "$PSScriptRoot/.secret-versions.env",
    [string]$StackName = "agentmind"
)

$ErrorActionPreference = "Stop"
$env:AGENTMIND_BACKEND_IMAGE = $Image
$env:AGENTMIND_CANARY_IMAGE = if ([string]::IsNullOrWhiteSpace($CanaryImage)) { $Image } else { $CanaryImage }

# 版本文件只保存 Docker Secret 名称，不保存秘密原文；密钥轮换后下一次 stack deploy 必须沿用这些名称。
if (Test-Path $SecretVersionFile) {
    $allowedSecretNameVariables = @(
        "AGENTMIND_DATABASE_PASSWORD_SECRET_NAME",
        "AGENTMIND_REDIS_PASSWORD_SECRET_NAME",
        "AGENTMIND_MINIO_ACCESS_KEY_SECRET_NAME",
        "AGENTMIND_MINIO_SECRET_KEY_SECRET_NAME",
        "AGENTMIND_OPENSEARCH_PASSWORD_SECRET_NAME",
        "AGENTMIND_TLS_CERTIFICATE_SECRET_NAME",
        "AGENTMIND_TLS_PRIVATE_KEY_SECRET_NAME"
    )
    Get-Content -Path $SecretVersionFile -Encoding UTF8 | ForEach-Object {
        if ($_ -match '^([A-Z0-9_]+)=(.+)$') {
            if ($allowedSecretNameVariables -notcontains $Matches[1]) {
                throw "秘密版本文件包含未授权变量：$($Matches[1])"
            }
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], "Process")
        }
    }
}

# Swarm 逐个启动新任务；新任务通过就绪检查后才会停止旧任务，失败则按 stack 策略自动回滚。
docker stack deploy --with-registry-auth --compose-file "$PSScriptRoot/docker-stack.yml" $StackName
if ($LASTEXITCODE -ne 0) {
    throw "发布命令执行失败"
}

docker service ps "$StackName`_agentmind-backend" --no-trunc
docker service ps "$StackName`_agentmind-backend-canary" --no-trunc
docker service ps "$StackName`_agentmind-gateway" --no-trunc
Write-Host "请通过 HTTPS 网关观察就绪状态；后端端口不再直接发布到公网。"
