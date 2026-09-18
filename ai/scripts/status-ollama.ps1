<#
.SYNOPSIS
  自检：Ollama 进程 / 端口 / /api/tags / 模型是否就绪（LIMS 本地 AI 助手，feature A / T04）。

.DESCRIPTION
  fail-loud 自检——四步逐项检查并给出明确结论与修复指引：
    1) 进程：是否存在 ollama 进程；
    2) 端口：127.0.0.1:<port> 是否可连；
    3) /api/tags：HTTP 是否可达；
    4) 模型：GET /api/tags 返回的 models 列表是否含目标模型。
  任一项失败以非 0 退出（便于 CI/联调脚本判断），并提示下一步命令。

.NOTES
  仅 Windows；兼容 Windows PowerShell 5.1。
  用法： powershell -ExecutionPolicy Bypass -File ai/scripts/status-ollama.ps1
#>
[CmdletBinding()]
param(
    [string]$RepoRoot,
    [string]$Model
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot -or $RepoRoot.Trim() -eq '') {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
$ConfigFile = Join-Path $RepoRoot 'ai\config\ollama.yml'

$host_ = '127.0.0.1'
$port  = '11434'
if (Test-Path $ConfigFile) {
    foreach ($line in Get-Content -Path $ConfigFile -Encoding UTF8) {
        $t = $line.Trim()
        if ($t -eq '' -or $t.StartsWith('#')) { continue }
        $idx = $t.IndexOf(':')
        if ($idx -lt 1) { continue }
        $k = $t.Substring(0, $idx).Trim()
        $v = $t.Substring($idx + 1).Trim().Trim('"').Trim("'")
        if ($k -eq 'host' -and $v -ne '') { $host_ = $v }
        if ($k -eq 'port' -and $v -ne '') { $port  = $v }
        if ($k -eq 'model' -and $v -ne '' -and (-not $Model)) { $Model = $v }
    }
}
if (-not $Model -or $Model.Trim() -eq '') { $Model = 'qwen3:4b-instruct' }
$baseUrl = "http://${host_}:${port}"

$ok = $true

# 1) 进程
$procs = Get-Process -Name 'ollama' -ErrorAction SilentlyContinue
if ($procs) {
    Write-Host "[status] 进程   : OK (PID: $($procs.Id -join ', '))"
} else {
    Write-Host '[status] 进程   : FAIL（未发现 ollama 进程）' -ForegroundColor Red
    $ok = $false
}

# 2) 端口（TCP 连通性）
$portOk = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $iar = $client.BeginConnect($host_, [int]$port, $null, $null)
    $portOk = $iar.AsyncWaitHandle.WaitOne(1500, $false) -and $client.Connected
    $client.Close()
} catch {
    $portOk = $false
}
if ($portOk) {
    Write-Host "[status] 端口   : OK ($baseUrl)"
} else {
    Write-Host "[status] 端口   : FAIL（$baseUrl 不可达）" -ForegroundColor Red
    $ok = $false
}

# 3) /api/tags 可达 + 4) 模型就绪
if ($portOk) {
    try {
        $resp = Invoke-RestMethod -Uri "$baseUrl/api/tags" -Method Get -TimeoutSec 5
        Write-Host '[status] HTTP    : OK (/api/tags 可达)'
        $names = @()
        if ($resp -and $resp.models) {
            foreach ($m in $resp.models) { $names += $m.name }
        }
        if ($names -contains $Model) {
            Write-Host "[status] 模型    : OK ($Model 已就绪)"
        } else {
            Write-Host "[status] 模型    : FAIL（未找到 $Model；已就绪：$($names -join ', ')）" -ForegroundColor Red
            Write-Host '         修复： powershell -ExecutionPolicy Bypass -File ai/scripts/deploy-ollama.ps1'
            $ok = $false
        }
    } catch {
        Write-Host "[status] HTTP    : FAIL（$($_.Exception.Message)）" -ForegroundColor Red
        $ok = $false
    }
}

if ($ok) {
    Write-Host ''
    Write-Host '[status] 结论    : 本地 AI 助手就绪（可直接登录系统使用）' -ForegroundColor Green
    exit 0
}

Write-Host ''
Write-Host '[status] 结论    : 本地 AI 助手未就绪；业务功能不受影响。' -ForegroundColor Yellow
Write-Host '         依次执行： deploy-ollama.ps1  →  start-ollama.ps1  →  status-ollama.ps1'
exit 1
