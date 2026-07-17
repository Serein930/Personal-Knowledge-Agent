$ErrorActionPreference = "Stop"

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$releaseGatePath = Join-Path $repositoryRoot ".github/workflows/production-release-gate.yml"
$stagingAcceptancePath = Join-Path $repositoryRoot ".github/workflows/staging-acceptance.yml"
$orchestratorPath = Join-Path $repositoryRoot ".github/workflows/release-candidate-acceptance.yml"

function Assert-Contains {
    param(
        [string]$Content,
        [string]$Expected,
        [string]$FailureMessage
    )

    if (-not $Content.Contains($Expected)) {
        throw $FailureMessage
    }
}

$releaseGate = Get-Content -LiteralPath $releaseGatePath -Raw -Encoding UTF8
$stagingAcceptance = Get-Content -LiteralPath $stagingAcceptancePath -Raw -Encoding UTF8
$orchestrator = Get-Content -LiteralPath $orchestratorPath -Raw -Encoding UTF8

# 供应链工作流必须同时支持独立手动运行和被统一验收入口复用。
Assert-Contains $releaseGate "workflow_call:" "生产供应链门禁没有声明可复用调用入口"
Assert-Contains $releaseGate "candidate_image:" "生产供应链门禁没有暴露候选镜像输出"
Assert-Contains $releaseGate 'candidate_image="${IMAGE}@${DIGEST}"' "候选镜像没有绑定仓库地址与 sha256 摘要"
Assert-Contains $releaseGate 'manifestType: "signed_release_candidate"' "候选镜像清单缺少稳定类型"
Assert-Contains $releaseGate "provenanceAttested: true" "候选镜像清单没有声明来源证明状态"
Assert-Contains $releaseGate "sbomAttested: true" "候选镜像清单没有声明软件物料清单证明状态"

# staging 工作流必须接受上游输出，保留单独重跑真实验收的能力。
Assert-Contains $stagingAcceptance "workflow_call:" "预发布验收没有声明可复用调用入口"
Assert-Contains $stagingAcceptance "candidate_image:" "预发布验收没有定义候选镜像输入"
Assert-Contains $stagingAcceptance '${{ inputs.candidate_image }}' "预发布验收没有使用候选镜像输入"

# 统一入口必须先完成供应链门禁，再把原始输出传给 staging，禁止改用普通镜像标签。
Assert-Contains $orchestrator "needs: supply_chain_gate" "预发布验收没有依赖供应链门禁"
Assert-Contains $orchestrator "uses: ./.github/workflows/production-release-gate.yml" "统一入口没有调用生产供应链门禁"
Assert-Contains $orchestrator "uses: ./.github/workflows/staging-acceptance.yml" "统一入口没有调用预发布验收"
Assert-Contains $orchestrator '${{ needs.supply_chain_gate.outputs.candidate_image }}' "统一入口没有原样传递摘要候选镜像"

Write-Host "发布候选工作流契约测试全部通过"
