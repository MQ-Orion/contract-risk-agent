# 一键评估脚本（规范 12.4：一条命令跑完检索与生成指标，报告按日期归档）
#
# 前置：PostgreSQL/Redis 已启动；已注入 DASHSCOPE_API_KEY。
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\eval-all.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\eval-all.ps1 -SkipExtraction

param(
    [switch]$SkipExtraction
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

if ([string]::IsNullOrWhiteSpace($env:DASHSCOPE_API_KEY)) {
    Write-Error '缺少 DASHSCOPE_API_KEY：真实 embedding 与抽取跑批都需要该环境变量（规范 11.5）'
}

$date = Get-Date -Format 'yyyyMMdd'
$started = Get-Date

function Invoke-Eval([string]$name, [string[]]$mavenArgs) {
    Write-Host "=== 评估：$name ===" -ForegroundColor Cyan
    & mvn -o -B -pl agent-module -am test @mavenArgs
    if ($LASTEXITCODE -ne 0) {
        throw "评估失败：$name（退出码 $LASTEXITCODE）"
    }
}

$common = @('-Dsurefire.failIfNoSpecifiedTests=false')

Invoke-Eval '检索基础指标' ($common + @('-Dcrag.it=true', '-Dtest=RetrievalEvalIT'))
Invoke-Eval '上下文召回率与 hard negatives' ($common + @('-Dcrag.it=true', '-Dtest=Week1011EvalIT'))
Invoke-Eval '引用准确率' ($common + @('-Dcrag.it=true', '-Dtest=CitationAccuracyIT'))
if (-not $SkipExtraction) {
    Invoke-Eval '抽取回验（真实模型）' ($common + @('-Dcrag.eval=true', '-Dtest=ExtractionEvalIT'))
}

function Get-Latest([string]$pattern) {
    $dir = Join-Path $repo 'docs/eval'
    $files = Get-ChildItem $dir -Filter $pattern -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending
    if ($files) { return (Join-Path $dir $files[0].Name) }
    return $null
}

function Get-Name([string]$path) {
    if (-not $path) { return '(未生成)' }
    return (Split-Path $path -Leaf)
}

# 从报告中按关键词取一行，再用正则抽指标值；抽不到时返回“见报告”
function Get-Metric([string]$path, [string]$keyword, [string]$pattern) {
    if (-not $path) { return '(未生成)' }
    $line = Select-String -Path $path -Pattern $keyword -Encoding UTF8 -SimpleMatch | Select-Object -First 1
    if (-not $line) { return '(见报告)' }
    $matched = [regex]::Match($line.Line, $pattern)
    if ($matched.Success) { return $matched.Groups[1].Value }
    return '(见报告)'
}

$retrieval = Get-Latest 'retrieval-*.md'
$context = Get-Latest 'context-recall-*.md'
$citation = Get-Latest 'citation-*.md'
$extraction = Get-Latest 'extraction-*.md'
$commit = (git rev-parse --short HEAD)
$elapsed = [int]((Get-Date) - $started).TotalSeconds

$devHit = Get-Metric $retrieval '双路融合（开发集）' '([0-9.]+%)'
$valHit = Get-Metric $retrieval '双路融合（验证集）' '([0-9.]+%)'
$vecHit = Get-Metric $retrieval '纯向量基线（开发集）' '([0-9.]+%)'
$gain = Get-Metric $retrieval '融合增益（开发集）' '([+\-][0-9.]+ 个百分点)'
$recall = Get-Metric $context '全部多条款题' '([0-9.]+%)'
$fp = Get-Metric $context '实测误报率' '\*\*(.+?)\*\*'
$citeMechanism = Get-Metric $citation '通过：' '(\d+ / \d+（[0-9.]+%）)'
$citeStored = Get-Metric $citation '样本量 N=' '(N=\d+；与入库标记一致：\d+（[0-9.]+%）)'
$extractRate = Get-Metric $extraction '抽取回验通过率' '(\d+\.\d+%（\d+/\d+）)'

$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# 第 10–11 周评估汇总（$date）")
$lines.Add("")
$lines.Add("- 代码版本：$commit")
$lines.Add("- 运行时间：$($started.ToString('yyyy-MM-dd HH:mm:ss'))，耗时 ${elapsed}s")
$lines.Add("- 环境：PostgreSQL 16 + pgvector / Redis 8 / 百炼 embedding + qwen3.8-flash（真实调用）")
$lines.Add("- 复现：powershell -ExecutionPolicy Bypass -File scripts\eval-all.ps1")
$lines.Add("")
$lines.Add("## 1. 检索层（规范 12.2）")
$lines.Add("")
$lines.Add("| 指标 | 实测 |")
$lines.Add("| --- | --- |")
$lines.Add("| 条款级命中率（开发集） | $devHit |")
$lines.Add("| 条款级命中率（验证集） | $valHit |")
$lines.Add("| 纯向量基线（开发集） | $vecHit |")
$lines.Add("| 融合增益（开发集） | $gain |")
$lines.Add("| 上下文召回率 | $recall |")
$lines.Add("| hard negatives 误报率 | $fp |")
$lines.Add("")
$lines.Add("报告：[$((Get-Name $retrieval))]($((Get-Name $retrieval))) ・ [$((Get-Name $context))]($((Get-Name $context)))")
$lines.Add("")
$lines.Add("## 2. 生成层（规范 12.3）")
$lines.Add("")
$lines.Add("| 指标 | 实测 |")
$lines.Add("| --- | --- |")
$lines.Add("| 引用准确率（机制校验） | $citeMechanism |")
$lines.Add("| 引用准确率（存量复核） | $citeStored |")
$lines.Add("| 抽取回验通过率 | $extractRate |")
$lines.Add("")
$lines.Add("报告：[$((Get-Name $citation))]($((Get-Name $citation))) ・ [$((Get-Name $extraction))]($((Get-Name $extraction)))")
$lines.Add("")
$lines.Add("## 3. 未覆盖项（不计入指标）")
$lines.Add("")
$lines.Add("- 忠实度人工抽检（规范 12.3 要求抽检 >= 10%）：需人工阅读，本轮未做。")
$lines.Add("- 幻觉率对比：需要强制溯源 / 无溯源两组 Prompt 的 A/B 实验，未执行，不宣称数字。")
$lines.Add("")
$lines.Add("## 4. 变更门槛（规范 6.7）")
$lines.Add("")
$lines.Add("任何检索 SQL、权重、k 值、召回数量、分词、嵌入模型、阈值、Prompt、规则变更，必须重跑本脚本，")
$lines.Add("并把前后对比附在 PR；命中率不得低于基线 2 个百分点。")

$indexPath = Join-Path (Join-Path $repo 'docs/eval') "week10-11-index-$date.md"
[IO.File]::WriteAllText($indexPath, ($lines -join "`r`n") + "`r`n", (New-Object System.Text.UTF8Encoding($true)))
Write-Host "汇总报告：$indexPath" -ForegroundColor Green
exit 0
