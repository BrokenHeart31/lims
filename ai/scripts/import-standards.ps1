<#
.SYNOPSIS
  一键导入 GB 标准（先 Python 预处理 PDF，再调后端建索引）（feature A / T04）。

.DESCRIPTION
  两步：
    1) 若 ai/standards/inbox 内含 PDF，则调用 ai/scripts/prepare-standards.py 转成 ai/standards/parsed/*.txt
       （可用 -SkipPrepare 跳过；python/pypdf 缺失时会给出安装指引并终止）；
    2) 调后端 A7 接口 POST /api/ai/kb/import/scan，扫描 parsed 目录异步建索引。

  鉴权：优先用 -Token；未给时用 -Username/-Password 走 /api/auth/login 取 accessToken（默认 nj001/nj001）。

  ⚠️ 后端 A7 接口属 T03（AI 助手后端）。**T03 未落地前本脚本第 2 步会返回 404**——
     此时第 1 步的预处理产物仍有效，可待 T03 上线后再执行第 2 步。

.NOTES
  仅 Windows；兼容 Windows PowerShell 5.1。
  用法： powershell -ExecutionPolicy Bypass -File ai/scripts/import-standards.ps1
        powershell -ExecutionPolicy Bypass -File ai/scripts/import-standards.ps1 -Token "<JWT>"
#>
[CmdletBinding()]
param(
    [string]$RepoRoot,
    # 后端地址（默认取 vite 代理目标；context-path=/api 已含在端点里）
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$Token,
    [string]$Username = 'nj001',
    [string]$Password = 'nj001',
    # 待导入目录（相对仓库根）
    [string]$Dir = 'ai/standards/parsed',
    [int]$SourceType = 1,
    # 跳过 PDF 预处理（parsed 已有产物时用）
    [switch]$SkipPrepare
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot -or $RepoRoot.Trim() -eq '') {
    $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
}
$InboxDir  = Join-Path $RepoRoot 'ai\standards\inbox'
$ParsedDir = Join-Path $RepoRoot 'ai\standards\parsed'
$PyScript  = Join-Path $PSScriptRoot 'prepare-standards.py'

# ---- 1) Python 预处理（仅当 inbox 有 PDF 且未跳过） -------------------------
if (-not $SkipPrepare) {
    $pdfs = @()
    if (Test-Path $InboxDir) {
        $pdfs = Get-ChildItem -Path $InboxDir -Filter '*.pdf' -File -ErrorAction SilentlyContinue
    }
    if ($pdfs.Count -gt 0) {
        Write-Host "[import-standards] 发现 $($pdfs.Count) 个 PDF，调用 prepare-standards.py 预处理…"
        $py = Get-Command python -ErrorAction SilentlyContinue
        if (-not $py) {
            Write-Host '[import-standards] 未找到 python。请安装 Python 3 并 pip install -r ai/scripts/requirements.txt' -ForegroundColor Red
            exit 3
        }
        & python $PyScript
        if ($LASTEXITCODE -ne 0) {
            Write-Host "[import-standards] 预处理失败（exit=$LASTEXITCODE），已停止导入" -ForegroundColor Red
            exit 4
        }
    } else {
        Write-Host '[import-standards] inbox 无 PDF，跳过预处理（直接用 parsed 目录现有产物）'
    }
}

if (-not (Test-Path $ParsedDir)) {
    New-Item -ItemType Directory -Path $ParsedDir -Force | Out-Null
}
$files = Get-ChildItem -Path $ParsedDir -File -ErrorAction SilentlyContinue
if (-not $files -or $files.Count -eq 0) {
    Write-Host "[import-standards] $ParsedDir 内无可导入文件。请先投放标准文件并执行预处理。" -ForegroundColor Yellow
    exit 5
}
Write-Host "[import-standards] 待导入文件 $($files.Count) 个"

# ---- 2) 取 token（-Token 优先，否则登录） -----------------------------------
if (-not $Token -or $Token.Trim() -eq '') {
    Write-Host "[import-standards] 登录 $Username 获取 token…"
    $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json
    try {
        $loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
            -ContentType 'application/json; charset=utf-8' -Body $loginBody -TimeoutSec 15
    } catch {
        Write-Host "[import-standards] 登录请求失败：$($_.Exception.Message)" -ForegroundColor Red
        Write-Host '  请确认后端已启动（backend: mvn spring-boot:run），或用 -Token 直接提供 JWT。'
        exit 6
    }
    if (-not $loginResp -or $loginResp.code -ne 0 -or -not $loginResp.data.accessToken) {
        Write-Host "[import-standards] 登录失败：$($loginResp.msg)" -ForegroundColor Red
        exit 6
    }
    $Token = $loginResp.data.accessToken
}

# ---- 3) 调后端 A7 扫描导入 --------------------------------------------------
$uri = "$BaseUrl/api/ai/kb/import/scan"
$body = @{ dir = ($Dir -replace '\\', '/'); sourceType = $SourceType } | ConvertTo-Json
Write-Host "[import-standards] POST $uri  body=$body"

try {
    $resp = Invoke-RestMethod -Uri $uri -Method Post -Headers @{ Authorization = "Bearer $Token" } `
        -ContentType 'application/json; charset=utf-8' -Body $body -TimeoutSec 60
} catch {
    Write-Host "[import-standards] 导入请求失败：$($_.Exception.Message)" -ForegroundColor Red
    Write-Host '  若为 404：A7 接口属 T03（AI 助手后端），待其落地后重试；预处理产物已就绪。'
    exit 7
}

if ($resp.code -ne 0) {
    Write-Host "[import-standards] 后端返回业务失败：code=$($resp.code) msg=$($resp.msg)" -ForegroundColor Red
    exit 8
}

Write-Host '[import-standards] 已提交建索引任务。进度可在系统「AI 助手 → 标准库导入」页或 A8/A9 接口查询。' -ForegroundColor Green
Write-Host "[import-standards] data: $($resp.data | ConvertTo-Json -Compress)"
exit 0
