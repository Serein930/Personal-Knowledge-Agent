param(
    [Parameter(Mandatory = $true)]
    [string]$TlsCertificatePath,
    [Parameter(Mandatory = $true)]
    [string]$TlsPrivateKeyPath
)

$ErrorActionPreference = "Stop"

# 秘密只从当前进程环境读取并通过标准输入写入 Swarm，不在磁盘生成中间文件。
$requiredSecrets = [ordered]@{
    "agentmind_database_password" = "AGENTMIND_DATABASE_PASSWORD"
    "agentmind_redis_password" = "AGENTMIND_REDIS_PASSWORD"
    "agentmind_minio_access_key" = "AGENTMIND_MINIO_ACCESS_KEY"
    "agentmind_minio_secret_key" = "AGENTMIND_MINIO_SECRET_KEY"
    "agentmind_opensearch_password" = "AGENTMIND_OPENSEARCH_PASSWORD"
}

foreach ($entry in $requiredSecrets.GetEnumerator()) {
    $value = [Environment]::GetEnvironmentVariable($entry.Value)
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "缺少环境变量 $($entry.Value)，未创建任何同名秘密"
    }
    $existing = docker secret ls --filter "name=$($entry.Key)" --format "{{.Name}}"
    if ($existing -eq $entry.Key) {
        Write-Host "秘密 $($entry.Key) 已存在，保持原值；轮换时请使用版本化名称。"
        continue
    }
    $value | docker secret create $entry.Key - | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "创建秘密 $($entry.Key) 失败"
    }
    Write-Host "已创建秘密 $($entry.Key)"
}

$tlsSecrets = [ordered]@{
    "agentmind_tls_certificate" = $TlsCertificatePath
    "agentmind_tls_private_key" = $TlsPrivateKeyPath
}
foreach ($entry in $tlsSecrets.GetEnumerator()) {
    $resolvedPath = (Resolve-Path -Path $entry.Value).Path
    $existing = docker secret ls --filter "name=$($entry.Key)" --format "{{.Name}}"
    if ($existing -eq $entry.Key) {
        Write-Host "TLS 秘密 $($entry.Key) 已存在，保持原值；证书轮换请使用版本化轮换脚本。"
        continue
    }
    docker secret create $entry.Key $resolvedPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "创建 TLS 秘密 $($entry.Key) 失败"
    }
    Write-Host "已创建 TLS 秘密 $($entry.Key)"
}
