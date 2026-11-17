# 切分策略对比（规范 12.2 切分策略对比）
# 用法：pwsh -File scripts/split-compare.ps1
# 产物：
#   docs/eval/split-compare-<日期>.md   归档报告
#   eval/reports/split-compare-<日期>.csv 机器可读明细（不入仓）
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

Push-Location $repoRoot
try {
    $env:JAVA_HOME = 'C:\Users\14330\.jdks\ms-17.0.20'
    mvn -o -B -pl doc-module -am test -Dtest=SplitComparisonTest -Dsurefire.failIfNoSpecifiedTests=false
    if ($LASTEXITCODE -ne 0) { throw '切分对比失败' }
}
finally {
    Pop-Location
}

Write-Host '报告已写入 docs/eval/ 与 eval/reports/'
