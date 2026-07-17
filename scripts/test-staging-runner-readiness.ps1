param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("primary", "disaster-recovery")]
    [string]$Role,
    [string]$EnvironmentName = $env:AGENTMIND_ENVIRONMENT,
    [string]$BaseUrl,
    [string]$VaultAgentConfig,
    [string]$VaultRenderedDirectory,
    [string]$StackName = "agentmind",
    [string]$OverlayNetwork = "agentmind-production",
    [string]$DisasterRecoveryComposeFile,
    [string]$ResticRepository,
    [string]$ResticPasswordFile,
    [string]$ReportRoot = ".staging-readiness-reports"
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

function Assert-RequiredValue([string]$Name, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) {
        throw "缺少必需的验收环境配置：$Name"
    }
}

function Assert-ExistingFile([string]$Name, [string]$Path) {
    Assert-RequiredValue -Name $Name -Value $Path
    if (-not [System.IO.Path]::IsPathRooted($Path)) {
        throw "$Name 必须使用 Runner 上的绝对路径"
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "$Name 指向的文件不存在：$Path"
    }
    if ((Get-Item -LiteralPath $Path).Length -eq 0) {
        throw "$Name 指向的文件为空：$Path"
    }
}

function Assert-ExistingDirectory([string]$Name, [string]$Path) {
    Assert-RequiredValue -Name $Name -Value $Path
    if (-not [System.IO.Path]::IsPathRooted($Path)) {
        throw "$Name 必须使用 Runner 上的绝对路径"
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) {
        throw "$Name 指向的目录不存在：$Path"
    }
}

function Get-RequiredTool([string]$Name, [string[]]$VersionArguments) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $command) {
        throw "Runner 缺少必需工具：$Name"
    }

    # 版本输出只截取首行，既能证明工具可执行，也避免把无关环境信息写入报告。
    $versionOutput = (& $command.Source @VersionArguments 2>&1 | Select-Object -First 1 | Out-String).Trim()
    Assert-LastCommand "无法执行 $Name 的版本检查"
    return [ordered]@{
        path = $command.Source
        version = $versionOutput
    }
}

function Test-VaultPaths {
    Assert-ExistingFile -Name "Vault Agent 配置" -Path $VaultAgentConfig
    Assert-ExistingDirectory -Name "Vault 渲染目录" -Path $VaultRenderedDirectory
    $checks.vault = [ordered]@{
        agentConfig = (Resolve-Path -LiteralPath $VaultAgentConfig).Path
        renderedDirectory = (Resolve-Path -LiteralPath $VaultRenderedDirectory).Path
    }
}

function Test-PrimaryRunner {
    Assert-RequiredValue -Name "STAGING_BASE_URL" -Value $BaseUrl
    $baseUri = [Uri]$BaseUrl
    if ($baseUri.Scheme -ne "https" -or -not [string]::IsNullOrWhiteSpace($baseUri.Query) `
            -or -not [string]::IsNullOrWhiteSpace($baseUri.Fragment)) {
        throw "STAGING_BASE_URL 必须是不带查询参数和片段的 HTTPS 地址"
    }
    $checks.baseUrl = $baseUri.GetLeftPart([System.UriPartial]::Authority)

    $checks.tools = [ordered]@{
        docker = Get-RequiredTool -Name "docker" -VersionArguments @("--version")
        vault = Get-RequiredTool -Name "vault" -VersionArguments @("version")
        cosign = Get-RequiredTool -Name "cosign" -VersionArguments @("version")
        powershell = $PSVersionTable.PSVersion.ToString()
    }
    if ($PSVersionTable.PSVersion.Major -lt 7) {
        throw "受保护 Runner 必须使用 PowerShell 7 或更高版本"
    }
    Test-VaultPaths

    $swarm = docker info --format '{{json .Swarm}}' | ConvertFrom-Json
    Assert-LastCommand "无法读取 Docker Swarm 状态"
    if ($swarm.LocalNodeState -ne "active" -or -not $swarm.ControlAvailable) {
        throw "主验收 Runner 必须连接到可用的 Docker Swarm 管理节点"
    }
    $checks.swarm = [ordered]@{
        nodeId = $swarm.NodeID
        state = $swarm.LocalNodeState
        controlAvailable = $swarm.ControlAvailable
    }

    $networkDriver = docker network inspect $OverlayNetwork --format '{{.Driver}}'
    Assert-LastCommand "无法读取覆盖网络 $OverlayNetwork"
    $networkOptions = docker network inspect $OverlayNetwork --format '{{json .Options}}' | ConvertFrom-Json
    Assert-LastCommand "无法读取覆盖网络加密配置"
    if ($networkDriver -ne "overlay" -or $networkOptions.PSObject.Properties.Name -notcontains "encrypted") {
        throw "网络 $OverlayNetwork 必须是启用 encrypted 选项的 overlay 网络"
    }
    $checks.overlayNetwork = [ordered]@{
        name = $OverlayNetwork
        driver = $networkDriver
        encrypted = $true
    }

    $requiredServices = [ordered]@{
        "$StackName`_agentmind-backend" = 2
        "$StackName`_agentmind-backend-canary" = 1
        "$StackName`_agentmind-gateway" = 2
    }
    $serviceChecks = [ordered]@{}
    foreach ($entry in $requiredServices.GetEnumerator()) {
        $desiredReplicas = docker service inspect $entry.Key --format '{{.Spec.Mode.Replicated.Replicas}}'
        Assert-LastCommand "无法读取服务 $($entry.Key)"
        $runningTasks = docker service ps $entry.Key --filter "desired-state=running" --format '{{.CurrentState}}'
        Assert-LastCommand "无法读取服务 $($entry.Key) 的任务状态"
        $runningReplicas = ($runningTasks | Where-Object { $_ -like "Running*" } | Measure-Object).Count
        if ([int]$desiredReplicas -lt $entry.Value -or $runningReplicas -lt $entry.Value) {
            throw "服务 $($entry.Key) 未达到验收拓扑：期望至少 $($entry.Value)，当前运行 $runningReplicas"
        }
        $serviceChecks[$entry.Key] = [ordered]@{
            desired = [int]$desiredReplicas
            running = $runningReplicas
        }
    }
    $checks.services = $serviceChecks
}

function Test-DisasterRecoveryRunner {
    $checks.tools = [ordered]@{
        docker = Get-RequiredTool -Name "docker" -VersionArguments @("--version")
        vault = Get-RequiredTool -Name "vault" -VersionArguments @("version")
        restic = Get-RequiredTool -Name "restic" -VersionArguments @("version")
        powershell = $PSVersionTable.PSVersion.ToString()
    }
    if ($PSVersionTable.PSVersion.Major -lt 7) {
        throw "灾备 Runner 必须使用 PowerShell 7 或更高版本"
    }
    Test-VaultPaths

    # 灾备节点不得同时充当生产 Swarm 管理节点，防止恢复脚本误触主集群。
    $swarm = docker info --format '{{json .Swarm}}' | ConvertFrom-Json
    Assert-LastCommand "无法读取灾备 Runner 的 Docker 状态"
    if ($swarm.LocalNodeState -eq "active" -and $swarm.ControlAvailable) {
        throw "灾备 Runner 不能连接生产 Docker Swarm 管理面"
    }
    $checks.swarmIsolation = "通过"

    Assert-ExistingFile -Name "灾备 Docker Compose 文件" -Path $DisasterRecoveryComposeFile
    if ([System.IO.Path]::GetFileName($DisasterRecoveryComposeFile) -eq "docker-stack.yml") {
        throw "灾备恢复禁止使用生产 Docker Swarm 栈文件"
    }
    docker compose -f $DisasterRecoveryComposeFile config --quiet
    Assert-LastCommand "灾备 Docker Compose 配置校验失败"
    $checks.composeFile = (Resolve-Path -LiteralPath $DisasterRecoveryComposeFile).Path

    Assert-RequiredValue -Name "STAGING_RESTIC_REPOSITORY" -Value $ResticRepository
    if ($ResticRepository -notmatch '^(s3:|azure:|gs:|b2:|rest:|rclone:)') {
        throw "灾备仓库必须是受支持的异地对象存储或远端仓库，禁止使用本地目录"
    }
    Assert-ExistingFile -Name "restic 密码文件" -Path $ResticPasswordFile
    if ($ResticRepository.StartsWith("s3:") -and (
            [string]::IsNullOrWhiteSpace($env:AWS_ACCESS_KEY_ID) -or
            [string]::IsNullOrWhiteSpace($env:AWS_SECRET_ACCESS_KEY))) {
        throw "S3 异地仓库缺少受保护的临时访问凭据"
    }

    # snapshots 是只读操作，用于在任何恢复覆盖动作前证明仓库、密码和网络均真实可用。
    restic -r $ResticRepository --password-file $ResticPasswordFile snapshots --json --latest 1 | Out-Null
    Assert-LastCommand "无法只读访问异地 restic 仓库"
    $checks.offsiteBackup = [ordered]@{
        repositoryType = $ResticRepository.Split(':')[0]
        passwordFile = (Resolve-Path -LiteralPath $ResticPasswordFile).Path
        readOnlyProbe = "通过"
    }
}

try {
    if ($EnvironmentName -ne "staging" -or $env:AGENTMIND_ENVIRONMENT -ne "staging") {
        throw "Runner 就绪检查只允许在 AGENTMIND_ENVIRONMENT=staging 的受保护环境运行"
    }
    if ($Role -eq "primary") {
        Test-PrimaryRunner
    } else {
        Test-DisasterRecoveryRunner
    }
} catch {
    $failure = $_.Exception.Message
}

# 就绪报告不是发布验收证据，不使用 evidenceType，避免被候选冻结脚本误收录。
$report = [ordered]@{
    schemaVersion = "1.0"
    reportType = "staging_runner_readiness"
    role = $Role
    environment = $EnvironmentName
    runnerName = $env:RUNNER_NAME
    startedAt = $startedAt.ToString("o")
    completedAt = ([DateTimeOffset]::UtcNow).ToString("o")
    checks = $checks
    passed = $null -eq $failure
    failure = $failure
}
New-Item -ItemType Directory -Path $ReportRoot -Force | Out-Null
$reportPath = Join-Path $ReportRoot "$Role-runner-readiness.json"
$report | ConvertTo-Json -Depth 10 | Set-Content -Path $reportPath -Encoding UTF8
if (-not $report.passed) {
    throw "预发布 Runner 就绪检查未通过：$failure。报告=$reportPath"
}
Write-Host "预发布 Runner 就绪检查通过：角色=$Role，报告=$reportPath"
