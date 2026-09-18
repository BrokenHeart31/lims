<#
.SYNOPSIS
  一键启动 LIMS 本地开发环境：Ollama（本地模型）→ 后端 → 前端，并轮询三项就绪自检。
  （增量 ai_flow_assistant，T04 / C-11，设计 §2.8）

.DESCRIPTION
  按序拉起三个进程，只有前一个「就绪」才拉起下一个；最后对 OLLAMA 轮询
  GET /api/ai/status 的三项就绪（online / modelPresent / kbReady）。
  任一不就绪 → 打印**具体下一步命令**并以非 0 退出（fail-loud，绝不静默假装成功）。

  三项就绪（C-11）：
    ① online        本地模型服务在线（127.0.0.1:11434 可达）
    ② modelPresent  期望模型已拉取（qwen3:4b-instruct）
    ③ kbReady       标准索引已入库 ≥1 篇（kbDocCount > 0）

.USAGE
  powershell -ExecutionPolicy Bypass -File ai/scripts/start-all.ps1
  powershell -ExecutionPolicy Bypass -File ai/scripts/start-all.ps1 -SkipFrontend
  powershell -ExecutionPolicy Bypass -File ai/scripts/start-all.ps1 -BackendPort 8080 -FrontendPort 5173

.NOTES
  仅 Windows；兼容 Windows PowerShell 5.1。
  本脚本**只负责拉起**：日志分别写 _logs/ollama.log / _logs/backend.log / _logs/frontend.log。
#>
[CmdletBinding()]
param(
    [string]$RepoRoot,
    [int]$BackendPort = 8080,
    [int]$FrontendPort = 5173,
    # Ollama 回环口
    [int]$OllamaPort = 11434,
    # 三段分别的等待上限（秒）
    [int]$OllamaWaitSec = 60,
    [int]$BackendWaitSec = 180,
    [int]$FrontendWaitSec = 120,
    [switch]$SkipFrontend,
    [switch]$SkipOllama
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot -or $RepoRoot.Trim() -eq '') {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
$LogDir = Join-Path $RepoRoot '_logs'
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }

function Test-Port([int]$port) {
    try {
        $c = New-Object System.Net.Sockets.TcpClient
        $c.Connect('127.0.0.1', $port); $c.Close(); return $true
    } catch { return $false }
}

function Wait-Port([int]$port, [int]$timeoutSec, [string]$what) {
    $sw = [Diagnostics.Stopwatch]::StartNew()
    while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
        if (Test-Port $port) { return $true }
        Start-Sleep -Milliseconds 800
    }
    Write-Host "[start-all] ✗ $what 在 $timeoutSec 秒内未就绪（端口 $port 未监听）" -ForegroundColor Red
    return $false
}

Write-Host '[start-all] === LIMS 本地开发环境启动 ===' -ForegroundColor Cyan

# ---- 1) Ollama --------------------------------------------------------------
if (-not $SkipOllama) {
    if (Test-Port $OllamaPort) {
        Write-Host "[start-all] Ollama 已在运行（:$OllamaPort）"
    } else {
        $ollamaExe = Join-Path $RepoRoot 'ai\runtime\ollama.exe'
        if (-not (Test-Path $ollamaExe)) {
            Write-Host "[start-all] ✗ 未找到 $ollamaExe。请先运行 ai/scripts/deploy-ollama.ps1 部署本地运行时与模型。" -ForegroundColor Red
            exit 2
        }
        Write-Host '[start-all] 启动 Ollama…'
        Start-Process -FilePath $ollamaExe -ArgumentList 'serve' -WorkingDirectory (Join-Path $RepoRoot 'ai\runtime') `
            -RedirectStandardOutput (Join-Path $LogDir 'ollama.log') `
            -RedirectStandardError  (Join-Path $LogDir 'ollama.log.err') -WindowStyle Hidden | Out-Null
        if (-not (Wait-Port $OllamaPort $OllamaWaitSec 'Ollama')) {
            Write-Host '  下一步：查看 _logs/ollama.log.err；或手动运行 ai/runtime/ollama.exe serve' -ForegroundColor Yellow
            exit 3
        }
    }
} else {
    Write-Host '[start-all] 跳过 Ollama（-SkipOllama）'
}

# ---- 2) 后端 ----------------------------------------------------------------
if (Test-Port $BackendPort) {
    Write-Host "[start-all] 后端已在运行（:$BackendPort）"
} else {
    Write-Host '[start-all] 启动后端（mvn spring-boot:run）…'
    $mvn = 'C:\Users\Chen\Desktop\apache-maven-3.9.11\bin\mvn.cmd'
    if (-not (Test-Path $mvn)) { $mvn = 'mvn' }
    Start-Process -FilePath $mvn -ArgumentList '-o', '-f', (Join-Path $RepoRoot 'backend\pom.xml'), 'spring-boot:run' `
        -WorkingDirectory $RepoRoot `
        -RedirectStandardOutput (Join-Path $LogDir 'backend.log') `
        -RedirectStandardError  (Join-Path $LogDir 'backend.log.err') -WindowStyle Hidden | Out-Null
    if (-not (Wait-Port $BackendPort $BackendWaitSec '后端')) {
        Write-Host '  下一步：查看 _logs/backend.log / backend.log.err' -ForegroundColor Yellow
        exit 4
    }
}

# ---- 3) 前端 ----------------------------------------------------------------
if (-not $SkipFrontend) {
    if (Test-Port $FrontendPort) {
        Write-Host "[start-all] 前端已在运行（:$FrontendPort）"
    } else {
        Write-Host '[start-all] 启动前端（npm run dev）…'
        $npm = 'npm.cmd'
        Start-Process -FilePath $npm -ArgumentList 'run', 'dev' -WorkingDirectory (Join-Path $RepoRoot 'frontend') `
            -RedirectStandardOutput (Join-Path $LogDir 'frontend.log') `
            -RedirectStandardError  (Join-Path $LogDir 'frontend.log.err') -WindowStyle Hidden | Out-Null
        if (-not (Wait-Port $FrontendPort $FrontendWaitSec '前端')) {
            Write-Host '  下一步：cd frontend; npm install; npm run dev（查看 _logs/frontend.log）' -ForegroundColor Yellow
            exit 5
        }
    }
} else {
    Write-Host '[start-all] 跳过前端（-SkipFrontend）'
}

# ---- 4) 三项就绪自检 --------------------------------------------------------
Write-Host '[start-all] 轮询 /api/ai/status 三项就绪…'
$statusUrl = "http://127.0.0.1:$BackendPort/api/ai/status"
$ready = $false
for ($i = 0; $i -lt 20; $i++) {
    try {
        $resp = Invoke-RestMethod -Uri $statusUrl -Method Get -TimeoutSec 10
        $d = $resp.data
        if ($d -and $d.online -and $d.modelPresent -and $d.kbReady) { $ready = $true }
        Write-Host ("[start-all] online={0} modelPresent={1} kbReady={2} kbDocCount={3} hint={4}" -f `
            $d.online, $d.modelPresent, $d.kbReady, $d.kbDocCount, $d.hint)
        if ($ready) { break }
    } catch {
        Write-Host "[start-all] /api/ai/status 暂不可达，重试…（$($_.Exception.Message)）"
    }
    Start-Sleep -Seconds 2
}

Write-Host ''
Write-Host '[start-all] === 访问地址 ===' -ForegroundColor Green
Write-Host ("  前端: http://127.0.0.1:{0}   （账号 nj001/nj001 管理员；nj002/nj002 登记员；nj003/nj003 任务管理员；njsa000/njsa000 检验员）" -f $FrontendPort)
Write-Host ("  后端: http://127.0.0.1:{0}/api" -f $BackendPort)

if (-not $ready) {
    Write-Host ''
    Write-Host '[start-all] ⚠️ 三项就绪未全部满足（fail-loud）：' -ForegroundColor Yellow
    Write-Host '  · online=false       → 运行 ai/runtime/ollama.exe serve（或 ai/scripts/start-ollama.ps1）' -ForegroundColor Yellow
    Write-Host '  · modelPresent=false → 运行 ai/scripts/deploy-ollama.ps1 拉取 qwen3:4b-instruct' -ForegroundColor Yellow
    Write-Host '  · kbReady=false      → 在「AI 助手 → 标准库导入」导入标准，或运行 ai/scripts/import-standards.ps1' -ForegroundColor Yellow
    Write-Host '  （注意：kbReady=false 不影响主流程，只是检索类问答不可用。）'
    exit 6
}

Write-Host '[start-all] ✓ 三项就绪（online / modelPresent / kbReady）均满足。' -ForegroundColor Green
exit 0
