param(
    [Parameter(Mandatory = $true)]
    [string]$RenderedSecretDirectory,
    [string]$StackName = "agentmind",
    [string]$VersionFile = "$PSScriptRoot/.secret-versions.env",
    [string]$EnvironmentName = "staging",
    [string]$GitCommit = $env:GITHUB_SHA,
    [string]$CandidateImage = $env:AGENTMIND_CANDIDATE_IMAGE,
    [string]$ReportRoot = ".production-acceptance-evidence"
)

$ErrorActionPreference = "Stop"
$startedAt = [DateTimeOffset]::UtcNow
$resolvedDirectory = (Resolve-Path -Path $RenderedSecretDirectory).Path
$version = Get-Date -Format "yyyyMMddHHmmss"

# 文件名是应用属性名或网关证书名，值只在 docker secret create 的标准输入中短暂出现。
$definitions = @(
    [pscustomobject]@{ File = "spring.datasource.password"; Base = "agentmind_database_password"; Environment = "AGENTMIND_DATABASE_PASSWORD_SECRET_NAME"; Target = "spring.datasource.password"; Services = @("agentmind-backend-canary", "agentmind-backend") },
    [pscustomobject]@{ File = "spring.data.redis.password"; Base = "agentmind_redis_password"; Environment = "AGENTMIND_REDIS_PASSWORD_SECRET_NAME"; Target = "spring.data.redis.password"; Services = @("agentmind-backend-canary", "agentmind-backend") },
    [pscustomobject]@{ File = "agentmind.storage.minio.access-key"; Base = "agentmind_minio_access_key"; Environment = "AGENTMIND_MINIO_ACCESS_KEY_SECRET_NAME"; Target = "agentmind.storage.minio.access-key"; Services = @("agentmind-backend-canary", "agentmind-backend") },
    [pscustomobject]@{ File = "agentmind.storage.minio.secret-key"; Base = "agentmind_minio_secret_key"; Environment = "AGENTMIND_MINIO_SECRET_KEY_SECRET_NAME"; Target = "agentmind.storage.minio.secret-key"; Services = @("agentmind-backend-canary", "agentmind-backend") },
    [pscustomobject]@{ File = "agentmind.keyword-index.opensearch.password"; Base = "agentmind_opensearch_password"; Environment = "AGENTMIND_OPENSEARCH_PASSWORD_SECRET_NAME"; Target = "agentmind.keyword-index.opensearch.password"; Services = @("agentmind-backend-canary", "agentmind-backend") },
    [pscustomobject]@{ File = "agentmind_tls_certificate.pem"; Base = "agentmind_tls_certificate"; Environment = "AGENTMIND_TLS_CERTIFICATE_SECRET_NAME"; Target = "agentmind_tls_certificate.pem"; Services = @("agentmind-gateway") },
    [pscustomobject]@{ File = "agentmind_tls_private_key.pem"; Base = "agentmind_tls_private_key"; Environment = "AGENTMIND_TLS_PRIVATE_KEY_SECRET_NAME"; Target = "agentmind_tls_private_key.pem"; Services = @("agentmind-gateway") }
)

function Assert-DockerCommand([string]$Message) {
    if ($LASTEXITCODE -ne 0) {
        throw $Message
    }
}

function Get-ServiceSecretName([string]$ServiceName, [string]$Target) {
    $json = docker service inspect $ServiceName --format '{{json .Spec.TaskTemplate.ContainerSpec.Secrets}}'
    Assert-DockerCommand "读取服务 $ServiceName 的秘密配置失败"
    if ([string]::IsNullOrWhiteSpace($json) -or $json -eq "null") {
        return $null
    }
    $references = $json | ConvertFrom-Json
    $reference = $references | Where-Object { $_.File.Name -eq $Target } | Select-Object -First 1
    return $reference.SecretName
}

$created = @{}
foreach ($definition in $definitions) {
    $path = Join-Path $resolvedDirectory $definition.File
    if (-not (Test-Path $path) -or (Get-Item $path).Length -eq 0) {
        throw "轮换输入文件不存在或为空：$($definition.File)"
    }
    $newName = "$($definition.Base)_$version"
    docker secret create $newName $path | Out-Null
    Assert-DockerCommand "创建版本化秘密 $newName 失败"
    $created[$definition.Environment] = $newName
}

$pendingVersionFile = "$VersionFile.pending"
# pending 文件不包含秘密值；若发布中断，运维人员可据此识别已经创建的新版本。
$created.GetEnumerator() | Sort-Object Key | ForEach-Object { "$($_.Key)=$($_.Value)" } |
    Set-Content -Path $pendingVersionFile -Encoding UTF8

foreach ($shortServiceName in @("agentmind-backend-canary", "agentmind-backend", "agentmind-gateway")) {
    $serviceName = "$StackName`_$shortServiceName"
    $serviceDefinitions = $definitions | Where-Object { $_.Services -contains $shortServiceName }
    $arguments = @("service", "update", "--detach=false")
    foreach ($definition in $serviceDefinitions) {
        $oldName = Get-ServiceSecretName $serviceName $definition.Target
        if (-not [string]::IsNullOrWhiteSpace($oldName)) {
            $arguments += @("--secret-rm", $oldName)
        }
        $newName = $created[$definition.Environment]
        $arguments += @("--secret-add", "source=$newName,target=$($definition.Target)")
    }
    $arguments += $serviceName
    & docker @arguments | Out-Host
    Assert-DockerCommand "服务 $serviceName 批量轮换秘密失败；请保留 $pendingVersionFile 并检查服务规格"
}

# 只有三个服务全部收敛后才把 pending 清单晋级为当前版本文件。
Move-Item -Path $pendingVersionFile -Destination $VersionFile -Force
if (-not [string]::IsNullOrWhiteSpace($GitCommit) -and -not [string]::IsNullOrWhiteSpace($CandidateImage)) {
    # 报告只记录 Docker Secret 的版本化名称，绝不记录渲染文件内容或秘密值。
    $report = [ordered]@{
        schemaVersion = "1.0"
        evidenceType = "secret_rotation"
        evidenceId = "secret-rotation-$version"
        environment = $EnvironmentName
        gitCommit = $GitCommit
        candidateImage = $CandidateImage
        startedAt = $startedAt.ToString("o")
        completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
        secretVersions = $created
        services = @(
            "$StackName`_agentmind-backend-canary",
            "$StackName`_agentmind-backend",
            "$StackName`_agentmind-gateway"
        )
        passed = $true
        failure = $null
    }
    New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
    $reportPath = Join-Path $ReportRoot "$($report.evidenceId).json"
    $report | ConvertTo-Json -Depth 8 | Set-Content -Path $reportPath -Encoding UTF8
    Write-Host "秘密轮换证据已写入：$reportPath"
} else {
    Write-Warning "未提供 GitCommit 或 CandidateImage，本次运维轮换不会生成发布验收证据"
}
Write-Host "秘密轮换完成。旧版本仍保留用于回滚，验证稳定后再按变更单人工清理。"
