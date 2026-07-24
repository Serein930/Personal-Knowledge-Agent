param(
    [string]$Profiles = "",
    [switch]$SkipDependencies,
    [string]$JvmArguments = "-Xms256m -Xmx1g -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8"
)

$ErrorActionPreference = "Stop"

$utf8 = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = $utf8
try {
    [Console]::InputEncoding = $utf8
    [Console]::OutputEncoding = $utf8
    & "$env:SystemRoot\System32\chcp.com" 65001 | Out-Null
} catch {
    Write-Warning "无法切换当前终端编码，后端仍会使用 UTF-8 JVM 参数启动。"
}

$backendRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $backendRoot

function Import-UserEnvironmentValue {
    param([string]$Name)

    if (-not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($Name))) {
        return
    }
    $userValue = [Environment]::GetEnvironmentVariable(
        $Name,
        [EnvironmentVariableTarget]::User
    )
    if (-not [string]::IsNullOrWhiteSpace($userValue)) {
        [Environment]::SetEnvironmentVariable(
            $Name,
            $userValue,
            [EnvironmentVariableTarget]::Process
        )
    }
}

function Test-EnvironmentValue {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name)
    return -not [string]::IsNullOrWhiteSpace($value)
}

function Resolve-LocalProfiles {
    if (-not [string]::IsNullOrWhiteSpace($Profiles)) {
        return $Profiles
    }

    $hasOpenAiConfiguration = Test-EnvironmentValue "OPENAI_API_KEY"
    $hasCompatibleModelConfiguration =
        (Test-EnvironmentValue "AGENTMIND_CHAT_API_KEY") -and
        (Test-EnvironmentValue "AGENTMIND_CHAT_BASE_URL") -and
        (Test-EnvironmentValue "AGENTMIND_CHAT_MODEL")

    if ($hasOpenAiConfiguration -or $hasCompatibleModelConfiguration) {
        return "local,local-ai,opensearch"
    }

    Write-Warning "未检测到完整真实模型配置，本次使用 Mock 问答启动。配置持久环境变量后，脚本会自动启用 local-ai。"
    return "local,opensearch"
}

function Start-LocalDependencies {
    param([string]$EffectiveProfiles)

    if ($SkipDependencies) {
        Write-Host "已跳过 Docker 本地依赖启动。"
        return
    }
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw "未检测到 Docker。请先启动 Docker Desktop，或使用 -SkipDependencies 跳过自动编排。"
    }

    $profileNames = $EffectiveProfiles.Split(
        ",",
        [System.StringSplitOptions]::RemoveEmptyEntries
    ) | ForEach-Object { $_.Trim().ToLowerInvariant() }
    $arguments = @("compose")
    $services = @("agentmind-postgres", "agentmind-redis")

    if ($profileNames -contains "opensearch") {
        $arguments += @("--profile", "opensearch")
        $services += "agentmind-opensearch"
    }
    if ($profileNames -contains "local") {
        $arguments += @("--profile", "searxng")
        $services += "agentmind-searxng"
    }

    $arguments += @("up", "-d", "--wait")
    $arguments += $services

    Write-Host "正在启动并等待本地依赖：$($services -join ', ')"
    Push-Location $repositoryRoot
    try {
        & docker @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Docker 本地依赖启动失败，退出码：$LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

$modelEnvironmentNames = @(
    "OPENAI_API_KEY",
    "AGENTMIND_CHAT_API_KEY",
    "AGENTMIND_CHAT_BASE_URL",
    "AGENTMIND_CHAT_COMPLETIONS_PATH",
    "AGENTMIND_CHAT_MODEL",
    "AGENTMIND_RAG_MODEL_NAME",
    "AGENTMIND_EMBEDDING_PROVIDER",
    "AGENTMIND_SPRING_AI_EMBEDDING_MODEL",
    "AGENTMIND_EMBEDDING_MODEL",
    "AGENTMIND_EMBEDDING_DIMENSIONS"
)
$modelEnvironmentNames | ForEach-Object { Import-UserEnvironmentValue $_ }

$effectiveProfiles = Resolve-LocalProfiles
Start-LocalDependencies $effectiveProfiles
Write-Host "正在启动 AgentMind 后端，Profiles=$effectiveProfiles"

Push-Location $backendRoot
try {
    & ".\mvnw.cmd" spring-boot:run "-Dspring-boot.run.profiles=$effectiveProfiles" "-Dspring-boot.run.jvmArguments=$JvmArguments"
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
