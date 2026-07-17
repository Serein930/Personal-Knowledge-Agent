param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [Parameter(Mandatory = $true)]
    [string]$CandidateImage,
    [Parameter(Mandatory = $true)]
    [string]$GitCommit,
    [string]$EnvironmentName = "staging",
    [string]$StackName = "agentmind",
    [int]$MinimumCertificateValidityDays = 14,
    [string]$CertificateIdentityRegexp,
    [string]$CertificateOidcIssuer = "https://token.actions.githubusercontent.com",
    [string]$ReportRoot = ".production-acceptance-evidence"
)

$ErrorActionPreference = "Stop"
$startedAt = [DateTimeOffset]::UtcNow
$checks = [ordered]@{}
$failure = $null

function Assert-LastCommand([string]$Message) {
    if ($LASTEXITCODE -ne 0) {
        throw $Message
    }
}

function Get-TlsCertificate([Uri]$Uri) {
    $port = if ($Uri.IsDefaultPort) { 443 } else { $Uri.Port }
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.Connect($Uri.Host, $port)
        $stream = [System.Net.Security.SslStream]::new($client.GetStream(), $false)
        try {
            # AuthenticateAsClient 会执行系统信任链、主机名和有效期校验，失败时不允许继续验收。
            $stream.AuthenticateAsClient($Uri.Host)
            return [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($stream.RemoteCertificate)
        } finally {
            $stream.Dispose()
        }
    } finally {
        $client.Dispose()
    }
}

try {
    if ($env:AGENTMIND_ENVIRONMENT -ne $EnvironmentName -or $EnvironmentName -ne "staging") {
        throw "生产验收预检只允许在 AGENTMIND_ENVIRONMENT=staging 的受保护环境运行"
    }
    if ($CandidateImage -notmatch '^.+@sha256:[a-f0-9]{64}$') {
        throw "候选镜像必须使用不可变 sha256 摘要，禁止使用 latest 或普通标签"
    }
    if ($GitCommit -notmatch '^[a-f0-9]{40}$') {
        throw "GitCommit 必须是完整的 40 位提交摘要"
    }

    $uri = [Uri]$BaseUrl
    if ($uri.Scheme -ne "https" -or -not [string]::IsNullOrWhiteSpace($uri.Query) -or -not [string]::IsNullOrWhiteSpace($uri.Fragment)) {
        throw "BaseUrl 必须是没有查询参数和片段的 HTTPS 地址"
    }
    $checks.httpsAddress = "通过"

    $swarm = docker info --format '{{json .Swarm}}' | ConvertFrom-Json
    Assert-LastCommand "无法读取 Docker Swarm 状态"
    if ($swarm.LocalNodeState -ne "active" -or -not $swarm.ControlAvailable) {
        throw "当前节点不是可用的 Docker Swarm 管理节点"
    }
    $checks.swarmManager = "通过"

    $requiredServices = [ordered]@{
        "$StackName`_agentmind-backend" = 2
        "$StackName`_agentmind-backend-canary" = 1
        "$StackName`_agentmind-gateway" = 2
    }
    foreach ($entry in $requiredServices.GetEnumerator()) {
        $replicas = docker service inspect $entry.Key --format '{{.Spec.Mode.Replicated.Replicas}}'
        Assert-LastCommand "无法读取服务 $($entry.Key)"
        if ([int]$replicas -lt $entry.Value) {
            throw "服务 $($entry.Key) 的目标副本数低于验收要求 $($entry.Value)"
        }
    }
    $checks.serviceTopology = "通过"

    docker pull $CandidateImage | Out-Null
    Assert-LastCommand "无法拉取候选镜像，请检查镜像地址和 Runner 仓库权限"
    $imageRevision = docker image inspect $CandidateImage --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
    Assert-LastCommand "无法读取候选镜像的 OCI 源码版本标签"
    if ($imageRevision -ne $GitCommit) {
        throw "候选镜像的 OCI 源码版本 $imageRevision 与验收提交 $GitCommit 不一致"
    }
    $checks.imageRevision = "通过：$imageRevision"

    if (-not [string]::IsNullOrWhiteSpace($CertificateIdentityRegexp)) {
        cosign verify `
            --certificate-identity-regexp $CertificateIdentityRegexp `
            --certificate-oidc-issuer $CertificateOidcIssuer `
            $CandidateImage | Out-Null
        Assert-LastCommand "候选镜像 Cosign 签名验证失败"
        $checks.imageSignature = "通过"
    }

    $certificate = Get-TlsCertificate -Uri $uri
    $remainingDays = [Math]::Round(($certificate.NotAfter.ToUniversalTime() - [DateTime]::UtcNow).TotalDays, 2)
    if ($remainingDays -lt $MinimumCertificateValidityDays) {
        throw "TLS 证书剩余有效期仅 $remainingDays 天，低于 $MinimumCertificateValidityDays 天门禁"
    }
    $checks.tls = [ordered]@{
        subject = $certificate.Subject
        thumbprint = $certificate.Thumbprint
        expiresAt = $certificate.NotAfter.ToUniversalTime().ToString("o")
        remainingDays = $remainingDays
    }

    $readinessUri = "$($BaseUrl.TrimEnd('/'))/actuator/health/readiness"
    $response = Invoke-WebRequest -Uri $readinessUri -UseBasicParsing -TimeoutSec 10
    if ($response.StatusCode -ne 200) {
        throw "就绪探针返回非 200 状态码：$($response.StatusCode)"
    }
    $checks.readiness = "通过"
} catch {
    $failure = $_.Exception.Message
}

$report = [ordered]@{
    schemaVersion = "1.0"
    evidenceType = "preflight"
    evidenceId = "preflight-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    environment = $EnvironmentName
    gitCommit = $GitCommit
    candidateImage = $CandidateImage
    startedAt = $startedAt.ToString("o")
    completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
    checks = $checks
    passed = $null -eq $failure
    failure = $failure
}
New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$reportPath = Join-Path $ReportRoot "preflight.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -Path $reportPath -Encoding UTF8
if (-not $report.passed) {
    throw "生产验收预检未通过：$failure。报告=$reportPath"
}
Write-Host "生产验收预检通过：$reportPath"
