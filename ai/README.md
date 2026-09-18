# 本地 AI 助手（AI Assistant）—— 运行时与标准库导入

> 特性 A（本地 AI 助手）的**运行时与导入脚本**部分。对应设计
> `docs/design/2026-09-17-arch-ai-assistant-and-rollback.md` §2 / §5.3 / §7，
> 后端接口契约见 `docs/api/api-spec.md` 第 16 章。

本目录可**整体搬走**：Ollama 运行时、模型权重、GB 标准文件全部落在仓库内
（`ai/runtime/`、`ai/models/`、`ai/standards/`），不写系统目录。

---

## 0. 一句话上手（五个命令）

```powershell
# ① 部署运行时 + 拉模型（只需一次；约 2.5GB 权重）
powershell -ExecutionPolicy Bypass -File ai/scripts/deploy-ollama.ps1

# ② 启动本地模型服务
powershell -ExecutionPolicy Bypass -File ai/scripts/start-ollama.ps1

# ③ 自检（进程 / 端口 / /api/tags / 模型是否就绪）
powershell -ExecutionPolicy Bypass -File ai/scripts/status-ollama.ps1

# ④ 投放标准：把 PDF 放进 ai/standards/inbox/（也支持 txt/md/html/csv）
#    然后预处理 + 建索引（一步到位）
powershell -ExecutionPolicy Bypass -File ai/scripts/import-standards.ps1

# ⑤ （可选）停止服务
powershell -ExecutionPolicy Bypass -File ai/scripts/stop-ollama.ps1
```

前端登录后，右下角悬浮窗即为 AI 助手入口；「AI 助手 → 标准库导入」页可查看
导入任务进度（A8/A9）。

---

## 1. 目录结构

```
ai/
├── README.md                      本文件
├── config/
│   ├── ollama.yml                 Ollama 运行时配置（host/port/model/think…，脚本读取）
│   └── standards.yml              标准库导入配置（inbox/parsed 目录、来源类型…）
├── runtime/                       Ollama 便携版（gitignore；由 deploy 脚本落位）
│   └── ollama.exe
├── models/                        模型权重 OLLAMA_MODELS（gitignore）
├── scripts/
│   ├── deploy-ollama.ps1          下载便携版（多镜像回退 + 手动兜底）+ 拉取模型
│   ├── start-ollama.ps1           以仓库内配置启动服务（幂等）
│   ├── stop-ollama.ps1            停止服务
│   ├── status-ollama.ps1          fail-loud 自检
│   ├── prepare-standards.py       PDF → TXT（pypdf）
│   ├── import-standards.ps1       预处理 + 调后端建索引（一键）
│   ├── requirements.txt           Python 依赖（固定小版本）
│   └── ollama.env.example         环境变量样板（复制为 ollama.env，已 gitignore）
└── standards/
    ├── inbox/                     原始投放（PDF/txt/md…；内容 gitignore）
    └── parsed/                    预处理产物（*.txt；内容 gitignore）
```

---

## 2. 部署本地模型服务

### 2.1 一键部署

```powershell
powershell -ExecutionPolicy Bypass -File ai/scripts/deploy-ollama.ps1
```

脚本做两件事：

1. **下载 Ollama 便携版**到 `ai/runtime/`，按下列候选镜像**顺序尝试、逐个回退**：
   - `https://github.com/ollama/ollama/releases/latest/download/ollama-windows-amd64.zip`
   - `https://ollama.com/download/ollama-windows-amd64.zip`
   - `https://mirror.ghproxy.com/https://github.com/...`（GitHub 加速镜像）
2. 以 `OLLAMA_MODELS=ai/models` 拉取模型 **`qwen3:4b-instruct`**。

> **不假设一定能联网**。本机为离线/受限网络环境，故：
> - 已有 `ai/runtime/ollama.exe` 时**跳过下载**（幂等）；
> - 全部镜像失败时**不静默吞掉**，而是打印手动兜底步骤并以非 0 退出。

### 2.2 手动兜底（离线环境）

1. 在任意可联网机器下载 Ollama Windows 版（<https://ollama.com/download> 或 GitHub releases 的 `ollama-windows-amd64.zip`）；
2. 解压到 `ai/runtime/`，确认该目录下存在 `ollama.exe`；
3. 重新执行 `deploy-ollama.ps1`（会跳过下载，直接进入拉模型步骤）。
   模型权重也可在有网机器上 `ollama pull qwen3:4b-instruct` 后，把
   `OLLAMA_MODELS` 目录整体拷贝到本仓库 `ai/models/`。

### 2.3 启动与自检

```powershell
powershell -ExecutionPolicy Bypass -File ai/scripts/start-ollama.ps1
powershell -ExecutionPolicy Bypass -File ai/scripts/status-ollama.ps1
```

- 服务只监听 **`127.0.0.1:11434`**（零外发红线，**禁止**改成对外地址）；
- `status-ollama.ps1` 四步逐项检查（进程 / 端口 / `/api/tags` / 模型就绪），
  任一失败以非 0 退出并给出修复命令。

### 2.4 为什么是 `qwen3:4b-instruct`

- 4B 规模在**本机 CPU** 上可做到交互式响应；
- `instruct`（非思考变体）+ 请求体 `think=false` 双保险，避免 `thinking` 内容污染展示并降低延迟；
- Q4_K_M 量化约 2.5GB，8GB 内存机器可跑（`OLLAMA_NUM_PARALLEL=1` 保守配置）。

---

## 3. 投放 GB 标准并建索引

### 3.1 支持的格式

- **直接可用**：`.txt` / `.md` / `.html` / `.htm` / `.csv`
- **必须先预处理**：`.pdf` —— 后端**拒绝** PDF（业务码 `4211`），
  需先用本目录脚本转成 `.txt`。原因：PDF 解析进 Java 需引入额外依赖
  （违背本项目「零新增 Maven 依赖」约定），而 Python 侧 `pypdf` 成熟且可离线安装。

### 3.2 步骤

```powershell
# 1) 把文件放进 ai/standards/inbox/
# 2) 预处理 + 一键导入（内部：有 PDF 就调 prepare-standards.py，再调后端 A7 接口）
powershell -ExecutionPolicy Bypass -File ai/scripts/import-standards.ps1
```

单独跑预处理（只转 PDF、不建索引）：

```powershell
python ai/scripts/prepare-standards.py            # 需要 pypdf
python ai/scripts/prepare-standards.py --force    # 覆盖已有产物
```

Python 依赖安装：

```powershell
pip install -r ai/scripts/requirements.txt
# 离线：在有网机器 pip download -r requirements.txt -d whl ，拷贝后 pip install --no-index --find-links whl pypdf
```

### 3.3 导入产物与检索

- 预处理产物：`ai/standards/parsed/<标准号>.txt`（含 `# source:` 头与 `===== page N =====` 分页标记，
  便于回溯命中位置）；
- 建索引：后端 A7 `POST /api/ai/kb/import/scan`（`dir=ai/standards/parsed`）**异步**执行，
  进度写 `gb_import_job`，可在「标准库导入」页或 A8/A9 接口查询；
- 检索：A12 `POST /api/ai/kb/search`（MySQL FULLTEXT ngram）。

> ⚠️ **A6–A13 属 T03（AI 助手后端）**。T03 未落地前，`import-standards.ps1` 的第 2 步会返回 404；
> 此时第 1 步的预处理产物仍然有效，待 T03 上线后再执行第 2 步即可。

---

## 4. 配置

| 文件 | 作用 | 谁读它 |
|---|---|---|
| `ai/config/ollama.yml` | host / port / model / think / models_dir / 超时 | `deploy`、`start`、`status` 三个脚本（只解析顶层 `key: value`） |
| `ai/config/standards.yml` | inbox / parsed 目录、来源类型、切块长度约定 | `prepare-standards.py`、`import-standards.ps1`（默认值参考） |
| `ai/scripts/ollama.env`（可选，gitignore） | 本机差异（代理、并发、保活…） | 由你手动 `source`/`设置环境变量`；样板见 `ollama.env.example` |
| `backend/.../application.yml` 的 `lims.ai.*` | 后端侧 AI 配置（base-url / model / 超时 / top-n） | Spring Boot |

> **改端口时两边都要改**：`ai/config/ollama.yml` 的 `host`/`port` 与后端
> `lims.ai.base-url` 必须指向同一个 Ollama。

### 为什么脚本只支持「顶层 key: value」

本机离线，无法安装 PowerShell YAML 模块；`ollama.yml` 里需要被脚本消费的键
（`host`/`port`/`model`）都是标量，用六行解析即可，避免引第三方依赖。
`standards.yml` 里的列表（`allowed_extensions`）是**文档性约定**，由 Java 侧与
预处理脚本的常量实现，脚本不解析它。

---

## 5. 故障排查

| 现象 | 原因 | 处置 |
|---|---|---|
| 自检「进程 FAIL」 | 未启动 | 运行 `start-ollama.ps1` |
| 自检「端口 FAIL」 | 服务未就绪 / 端口被占 | 看 `ai/runtime/ollama.log`；确认 11434 未被占用 |
| 自检「模型 FAIL」 | 权重未拉取 | 运行 `deploy-ollama.ps1`；或按 §2.2 拷贝 `ai/models/` |
| 页面提示「AI 助手暂不可用」(4201) | 模型服务离线 | 正常降级，**业务功能不受影响**；启动服务即可恢复 |
| 上传 PDF 被拒 (4211) | PDF 未预处理 | 运行 `prepare-standards.py` 转 `.txt` |
| 预处理报缺少 `pypdf` | 依赖未装 | `pip install -r ai/scripts/requirements.txt` |
| 扫描件 PDF 转出的 txt 近乎为空 | 图片型 PDF（无文本层） | 属预期（脚本如实标记分页但不编造内容），需先 OCR 或改用官方电子版 |

---

## 6. 安全与合规红线

- **零外发**：Ollama 仅监听回环口；`base-url` 固定 `127.0.0.1`，禁止改外网地址；
- **不入库**：`ai/runtime/`、`ai/models/`、`ai/standards/{inbox,parsed}/*` 均 gitignore
  （体积 + GB 文本版权）；
- **AI 仅供参考**：助手回答不参与判定；判定一律以系统规则（`JudgeEngine`）为准，
  界面固定标注「AI 建议，仅供参考」。
