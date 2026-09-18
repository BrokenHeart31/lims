<#
.SYNOPSIS
  一键部署本地 Ollama 便携运行时并拉取模型（LIMS 本地 AI 助手，feature A / T04）。

.DESCRIPTION
  做两件事：
    1) 把 Ollama 便携版解压到仓库内 ai/runtime/（按候选镜像顺序尝试，全部失败则给出手动兜底）；
    2) 用仓库内 ai/models/ 作为 OLLAMA_MODELS，拉取 ai/config/ollama.yml 指定的模型。

  设计取舍（与 docs/design/2026-09-17-arch-ai-assistant-and-rollback.md §2.5/§7 一致）：
    - 运行时与权重**全量落在仓库内**（ai/runtime、ai/models），不写系统目录，保证「仓库整体搬走即可用」；
    - 二进制与权重均 gitignore，绝不入库（体积）；
    - 本机为离线/受限网络环境，故**多镜像候选 + 逐个回退**，且**不假设一定能联网**：
      任何一步失败都不静默吞掉，而是打印「手动兜底」指引并以非 0 退出（fail-loud）。

.NOTES
  仅 Windows。兼容 Windows PowerShell 5.1（不使用 && / 三元 / ?? 等 PS7 语法）。
  用法： powershell -ExecutionPolicy Bypass -File ai/scripts/deploy-ollama.ps1
#>
[CmdletBinding()]
param(
    # 模型名；缺省取 ai/config/ollama.yml 的 model
    [string]$Model,
    # 仓库根（脚本自动推断：本文件位于 <repo>/ai/scripts/）
    [string]$RepoRoot,
    # Ollama 便携包下载候选（按顺序尝试；镜像失效时自动回退下一个）
    [string[]]$Mirrors = @(
        'https://github.com/ollama/ollama/releases/latest/download/ollama-windows-amd64.zip',
        'https://ollama.com/download/ollama-windows-amd64.zip',
        'https://mirror.ghproxy.com/https://github.com/ollama/ollama/releases/latest/download/ollama-windows-amd64.zip'
    )
)

$ErrorActionPreference = 'Stop'

# ---- 定位仓库根（本脚本在 <repo>/ai/scripts/） -------------------------------
if (-not $RepoRoot -or $RepoRoot.Trim() -eq '') {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
$RuntimeDir = Join-Path $RepoRoot 'ai\runtime'
$ModelsDir  = Join-Path $RepoRoot 'ai\models'
$ConfigFile = Join-Path $RepoRoot 'ai\config\ollama.yml'

# ---- 读取 ai/config/ollama.yml（只解析顶层 key: value；# 起注释） ------------
# 说明：刻意只支持「顶层简单键值」而不引第三方 YAML 模块——本机离线，无法装模块；
#       配置里需要表达的结构（host/port/model/think）都是标量，够用。
function Get-OllamaConfig {
    param([string]$Path)
    $cfg = @{}
    if (Test-Path $Path) {
        foreach ($line in Get-Content -Path $Path -Encoding UTF8) {
            $t = $line.Trim()
            if ($t -eq '' -or $t.StartsWith('#')) { continue }
            $idx = $t.IndexOf(':')
            if ($idx -lt 1) { continue }
            $k = $t.Substring(0, $idx).Trim()
            $v = $t.Substring($idx + 1).Trim().Trim('"').Trim("'")
            if ($v -ne '') { $cfg[$k] = $v }
        }
    }
    return $cfg
}

$cfg = Get-OllamaConfig -Path $ConfigFile
if (-not $Model -or $Model.Trim() -eq '') {
    $Model = if ($cfg.ContainsKey('model')) { $cfg['model'] } else { 'qwen3:4b-instruct' }
}

Write-Host "[deploy-ollama] repo root : $RepoRoot"
Write-Host "[deploy-ollama] model     : $Model"

# ---- 1) 准备目录（运行时/模型在仓库内；目录结构随仓库走） -------------------
foreach ($d in @($RuntimeDir, $ModelsDir)) {
    if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
}

# ---- 2) 安装 Ollama 便携版（幂等：已存在则跳过下载） ------------------------
$OllamaExe = Join-Path $RuntimeDir 'ollama.exe'
if (Test-Path $OllamaExe) {
    Write-Host "[deploy-ollama] 已存在 $OllamaExe，跳过下载（如需重装请先删除 ai/runtime/）"
} else {
    $zip = Join-Path $env:TEMP 'ollama-windows-amd64.zip'
    $downloaded = $false
    foreach ($url in $Mirrors) {
        Write-Host "[deploy-ollama] 尝试下载: $url"
        try {
            # 便携包约数百 MB：给足超时（受限网络下宁可慢也不轻易失败）
            Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing -TimeoutSec 600
            if ((Test-Path $zip) -and ((Get-Item $zip).Length -gt 1MB)) {
                $downloaded = $true
                Write-Host "[deploy-ollama] 下载成功: $url"
                break
            }
            Write-Host "[deploy-ollama] 下载内容异常（体积过小），继续尝试下一个镜像"
        } catch {
            Write-Host "[deploy-ollama] 下载失败：$($_.Exception.Message)"
        }
    }

    if (-not $downloaded) {
        Write-Host ''
        Write-Host '=== 自动下载失败：请手动兜底 ===' -ForegroundColor Yellow
        Write-Host '  1) 在任意可联网机器下载 Ollama Windows 版：https://ollama.com/download'
        Write-Host '     （或 GitHub releases 的 ollama-windows-amd64.zip）'
        Write-Host "  2) 把压缩包内文件解压到： $RuntimeDir"
        Write-Host '     解压后该目录下应存在 ollama.exe'
        Write-Host '  3) 重新执行本脚本（会跳过下载，直接进入拉模型步骤）'
        Write-Host ''
        Write-Host '说明：AI 助手为增强功能，未部署不影响任何业务功能。' -ForegroundColor Cyan
        exit 2
    }

    Write-Host "[deploy-ollama] 解压到 $RuntimeDir"
    Expand-Archive -Path $zip -DestinationPath $RuntimeDir -Force
    Remove-Item $zip -Force -ErrorAction SilentlyContinue

    if (-not (Test-Path $OllamaExe)) {
        Write-Host "[deploy-ollama] 解压后未找到 ollama.exe，请检查压缩包结构后手动放置到 $RuntimeDir" -ForegroundColor Red
        exit 3
    }
}

# ---- 3) 拉取模型（OLLAMA_MODELS 指向仓库内 ai/models） ----------------------
Write-Host "[deploy-ollama] OLLAMA_MODELS = $ModelsDir"
$env:OLLAMA_MODELS = $ModelsDir
$env:OLLAMA_HOST   = '127.0.0.1:11434'

Write-Host "[deploy-ollama] 拉取模型 $Model（首次约数 GB，请耐心等待）"
& $OllamaExe pull $Model
if ($LASTEXITCODE -ne 0) {
    Write-Host "[deploy-ollama] 拉取模型失败（exit=$LASTEXITCODE）。" -ForegroundColor Red
    Write-Host '  排查：① 本机是否可访问 ollama.com；② 磁盘剩余空间；③ 代理设置（HTTPS_PROXY）。'
    Write-Host '  手动兜底：设置 $env:OLLAMA_MODELS 后手动执行 `ollama pull <模型>`。'
    exit 4
}

Write-Host ''
Write-Host "[deploy-ollama] 完成。下一步：powershell -ExecutionPolicy Bypass -File ai/scripts/start-ollama.ps1" -ForegroundColor Green
Write-Host "[deploy-ollama] 自检：      powershell -ExecutionPolicy Bypass -File ai/scripts/status-ollama.ps1"
exit 0
