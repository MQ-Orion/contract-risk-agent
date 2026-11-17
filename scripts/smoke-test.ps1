# 端到端冒烟测试（规范 17.1：上传 → 解析 → 切分全链路）
# 兼容 Windows PowerShell 5.1 与 PowerShell 7；multipart 上传使用系统自带 curl.exe。
# 前置：依赖环境已启动（PostgreSQL 16 + Redis），应用已启动并监听 8080。
# 用法：powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$samples = Join-Path $repoRoot 'eval\samples'
$sampleNames = @('purchase_contract.docx', 'service_contract.docx', 'nda_contract.docx')
$utf8 = New-Object System.Text.UTF8Encoding($false)

# PowerShell 5.1 在响应缺少 charset 时会按 ISO-8859-1 解析 JSON，这里显式按 UTF-8 解码
function ConvertFrom-Response {
    param($Response)
    $bytes = $Response.RawContentStream.ToArray()
    $text = $utf8.GetString($bytes)
    return $text | ConvertFrom-Json
}

function Get-Api {
    param([string]$Path)
    $resp = Invoke-WebRequest -Uri "$BaseUrl$Path" -Method Get -UseBasicParsing -TimeoutSec 30
    return ConvertFrom-Response -Response $resp
}

function Invoke-Upload {
    param([string]$FilePath)
    $temp = [System.IO.Path]::GetTempFileName()
    try {
        & curl.exe -s -o $temp -F "file=@$FilePath" "$BaseUrl/api/v1/contracts"
        if ($LASTEXITCODE -ne 0) { throw "curl 上传失败：$FilePath" }
        $text = $utf8.GetString([System.IO.File]::ReadAllBytes($temp))
        return $text | ConvertFrom-Json
    }
    finally {
        Remove-Item $temp -ErrorAction SilentlyContinue
    }
}

Write-Host "目标服务：$BaseUrl"
$rows = @()
foreach ($name in $sampleNames) {
    $file = Join-Path $samples $name
    if (-not (Test-Path $file)) { throw "样例缺失：$file" }

    $upload = Invoke-Upload -FilePath $file
    if ($upload.code -ne 0) { throw "上传失败：$name code=$($upload.code) msg=$($upload.message)" }
    $contractId = $upload.data.contractId

    $status = 'PENDING'
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        $task = Get-Api -Path "/api/v1/contracts/$contractId/parse-task"
        $status = $task.data.status
        if ($status -eq 'READY' -or $status -eq 'FAILED') { break }
        Start-Sleep -Milliseconds 500
    }

    $clauses = Get-Api -Path "/api/v1/contracts/$contractId/clauses"
    $items = @($clauses.data)
    $lengths = @($items | ForEach-Object { $_.text.Length })
    $maxLength = 0
    if ($lengths.Count -gt 0) { $maxLength = ($lengths | Measure-Object -Maximum).Maximum }
    $firstClauseNo = '-'
    if ($items.Count -gt 0) { $firstClauseNo = $items[0].clauseNo }

    $rows += [pscustomobject]@{
        样例     = $name
        合同标识 = $contractId
        状态     = $status
        切片数   = $items.Count
        首切片   = $firstClauseNo
        最长切片 = $maxLength
    }
}

$again = Invoke-Upload -FilePath (Join-Path $samples 'purchase_contract.docx')

$rows | Format-Table -AutoSize | Out-String | Write-Host
Write-Host ("秒传校验：deduplicated={0}，合同标识={1}" -f $again.data.deduplicated, $again.data.contractId)

$bad = @($rows | Where-Object { $_.状态 -ne 'READY' -or $_.切片数 -le 0 })
if ($bad.Count -gt 0) { throw ("冒烟失败：{0} 份合同未就绪或无切片" -f $bad.Count) }
if (-not $again.data.deduplicated) { throw '秒传校验失败：重复上传未命中秒传' }
if ([long]$again.data.contractId -ne [long]$rows[0].合同标识) { throw '秒传校验失败：合同标识不一致' }
$overlong = @($rows | Where-Object { $_.最长切片 -gt 800 })
if ($overlong.Count -gt 0) { throw '切片长度超过 800 字上限' }

Write-Host '冒烟通过：三份样例均解析就绪并产出切片，重复上传命中秒传，切片长度未超限'
