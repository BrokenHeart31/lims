<#
.SYNOPSIS
  以仓库内配置启动 Ollama 服务（LIMS 本地 AI 助手，feature A / T04）。

.DESCRIPTION
  - OLLAMA_MODELS 指向仓库内 ai/models/（模型不入系统目录，随仓库搬走）；
  - OLLAMA_HOST 固定回环口 127.0.0.1:11434（**只监听本机**，不对外暴露；AI 零外发）；
  - 启动前先检查是否已在运行（幂等：重复执行不会起两个进程）。

.NOTES
  仅 Windows；兼容 Windows PowerShell 5.1。
  用法： powershell -ExecutionPolicy Bypass -File ai/scripts/start-ollama.ps1
#>
[CmdletBinding()]
param(
    [string]$RepoRoot
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot -or $RepoRoot.Trim() -eq '') {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
$RuntimeDir = Join-Path $RepoRoot 'ai\runtime'
$ModelsDir  = Join-Path $RepoRoot 'ai\models'
$OllamaExe  = Join-Path $RuntimeDir 'ollama.exe'
$ConfigFile = Join-Path $RepoRoot 'ai\config\ollama.yml'

# 读取配置（只解析顶层 key: value）
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
    }
}
$listen = "${host_}:${port}"

if (-not (Test-Path $OllamaExe)) {
    Write-Host "[start-ollama] 未找到 $OllamaExe" -ForegroundColor Red
    Write-Host '  请先执行： powershell -ExecutionPolicy Bypass -File ai/scripts/deploy-ollama.ps1'
    exit 2
}

foreach ($d in @($ModelsDir)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}

# 幂等：已在监听则不再启动
$running = Get-Process -Name 'ollama' -ErrorAction SilentlyContinue
if ($running) {
    Write-Host "[start-ollama] 已有 ollama 进程在运行（PID: $($running.Id -join ', ')），跳过启动"
    exit 0
}

$env:OLLAMA_MODELS = $ModelsDir
$env:OLLAMA_HOST   = $listen
Write-Host "[start-ollama] OLLAMA_MODELS = $ModelsDir"
Write-Host "[start-ollama] OLLAMA_HOST   = $listen"

# 后台启动服务（-WindowStyle Hidden 避免弹窗；日志重定向到 ai/runtime/ollama.log）
$logFile = Join-Path $RuntimeDir 'ollama.log'
Start-Process -FilePath $OllamaExe -ArgumentList 'serve' -WorkingDirectory $RuntimeDir `
    -WindowStyle Hidden -RedirectStandardOutput $logFile -RedirectStandardError "$logFile.err"

Start-Sleep -Seconds 3
Write-Host "[start-ollama] 已启动（日志：$logFile）"
Write-Host "[start-ollama] 自检： powershell -ExecutionPolicy Bypass -File ai/scripts/status-ollama.ps1"
exit 0
