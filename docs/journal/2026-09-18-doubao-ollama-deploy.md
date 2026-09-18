# 2026-09-18 doubao · 本机部署 Ollama + qwen3:4b（feature A / T04）

## 1. 本轮目标
按用户指令在本机（Windows）完成 LIMS 本地 AI 助手的运行时部署：
`deploy-ollama.ps1` → `start-ollama.ps1` → `status-ollama.ps1`，模型 `qwen3:4b`（项目配置为 `qwen3:4b-instruct`）。

## 2. 实际做法
- 依次执行三个脚本；最终状态：Ollama 0.34.2 便携版位于 `D:\lims\ai\runtime`，模型 2.33GB 位于 `D:\lims\ai\models`，服务监听 `127.0.0.1:11434`（PID 31004）。
- 冒烟测试：`POST /api/generate`（prompt=自我介绍，num_predict=48）→ 正常回答，CPU 推理约 2.7s。
- 修复动作：5 个 ps1（deploy/start/status/stop/import-standards）添加 UTF-8 BOM。

## 3. 踩坑记录
1. **ps1 中文乱码导致解析失败**：deploy 首跑报 `Unexpected token '}'` / `Array index expression is missing`，且输出乱码（`灏濊瘯涓嬭浇`）。
   - 根因：文件为 UTF-8 **无 BOM**，Windows PowerShell 5.1（本机无 pwsh）按 ANSI/GBK 解析，中文字节破坏了引号/括号语法。
   - 处理：`[IO.File]::ReadAllText(严格UTF8) → WriteAllText(UTF8+BOM)` 对 5 个 ps1 统一加 BOM，Parser.ParseFile 验证 errors=0。
   - 可复用：本项目所有 ps1 必须带 UTF-8 BOM，或在脚本头部写 `#requires -Version` 并约定用 pwsh。
2. **deploy 拉模型报 `could not locate ollama app`**：便携包解压仅含 `ollama.exe`+`lib/`，无 `ollama app.exe`；`ollama pull` 时服务未运行、CLI 无法自启应用即报此错。
   - 根因：deploy 脚本设计缺陷——在未启动 `ollama serve` 的情况下直接 pull（Windows 安装版可自启，便携版不行）。
   - 处理：先跑 `start-ollama.ps1` 启动 serve，再手动 `ollama pull qwen3:4b-instruct`（`OLLAMA_MODELS=D:\lims\ai\models`），成功。
   - 建议：deploy-ollama.ps1 应在 pull 前先检查端口，未监听则先 `Start-Process ollama serve`（提 TODO 给 GLM 修复，属 B 级报告）。
3. **PowerShell 5.1 调用 curl.exe 传 JSON 引号被剥**：`-d $body`（含双引号）被剥离 key 引号，服务端报 `invalid character 'm'`。
   - 处理：JSON 写入临时文件后 `curl -d "@file"`。
4. **Invoke-WebRequest 慢/超时**：本机代理环境导致 IWR 走代理卡顿，改用 `curl.exe --max-time` 快速验证。

## 4. 进度
- 项目总进度：不涉及业务任务百分比调整；feature A / T04 部署验证完成（此前为脚本已就绪未部署）。
- 本次产出：运行时 + 权重全量落仓库（ai/runtime、ai/models，gitignore），`status-ollama.ps1` 自检通过。

## 5. 可复用结论
- 便携版 Ollama 部署顺序必须是：解压 → `ollama serve`（后台）→ `ollama pull`；deploy 脚本需补 serve 前置。
- ps1 编码规范（BOM）+ 原生 exe 传 JSON 用文件方式，是本机 PowerShell 5.1 环境的两个固定坑。
