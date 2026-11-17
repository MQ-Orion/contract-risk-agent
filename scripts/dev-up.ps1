# 一条命令拉起依赖环境并自检（规范 15.1、17.1）
# 用法：pwsh -File scripts/dev-up.ps1
param(
    [switch]$SkipVerify
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$dockerDir = Join-Path $repoRoot 'docker'
$envFile = Join-Path $dockerDir '.env'

if (-not (Test-Path $envFile)) {
    Copy-Item (Join-Path $dockerDir '.env.example') $envFile
    Write-Host "已根据 .env.example 创建 $envFile"
}

Push-Location $dockerDir
try {
    docker compose up -d
    if ($LASTEXITCODE -ne 0) { throw 'docker compose up 失败' }

    Write-Host '等待 PostgreSQL 健康检查通过...'
    $deadline = (Get-Date).AddMinutes(3)
    while ((Get-Date) -lt $deadline) {
        $status = (docker inspect --format '{{.State.Health.Status}}' crag-postgres 2>$null)
        if ($status -eq 'healthy') { break }
        Start-Sleep -Seconds 3
    }
    if ($status -ne 'healthy') { throw "PostgreSQL 未在 3 分钟内就绪，当前状态：$status" }

    docker compose ps
    if ($LASTEXITCODE -ne 0) { throw 'docker compose ps 失败' }
}
finally {
    Pop-Location
}

if ($SkipVerify) {
    Write-Host '已跳过数据库自检'
    exit 0
}

$verifySql = Join-Path $repoRoot 'db\verify\verify_week1_2.sql'
Write-Host "执行数据库自检：$verifySql"
Get-Content $verifySql -Raw | docker exec -i crag-postgres psql -U crag -d contract_risk
if ($LASTEXITCODE -ne 0) { throw '数据库自检失败' }
Write-Host '环境就绪（规范 17.1 第 1–2 周验收项之一）'
