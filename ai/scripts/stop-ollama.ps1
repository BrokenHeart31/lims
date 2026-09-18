<#
.SYNOPSIS
  停止仓库内 Ollama 服务（LIMS 本地 AI 助手，feature A / T04）。

.DESCRIPTION
  只停止**本机 ollama 进程**（若你另有系统级 Ollama 在用，请勿执行本脚本）。
  停止后 AI 助手不可用，但**业务功能完全不受影响**（AI 为增强功能）。

.NOTES
  仅 Windows；兼容 Windows PowerShell 5.1。
  用法： powershell -ExecutionPolicy Bypass -File ai/scripts/stop-ollama.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$procs = Get-Process -Name 'ollama' -ErrorAction SilentlyContinue
if (-not $procs) {
    Write-Host '[stop-ollama] 没有正在运行的 ollama 进程'
    exit 0
}

foreach ($p in $procs) {
    Write-Host "[stop-ollama] 停止 PID $($p.Id)"
    Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
}
Write-Host '[stop-ollama] 完成'
exit 0
