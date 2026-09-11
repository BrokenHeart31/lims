---
name: sandbox-git-push
description: 在 WorkBuddy 沙箱内安全完成 git 提交、分支合并与向 GitHub 推送，规避「agent 分支引用被静默丢弃」「切分支被 SIGTERM」「证书 MITM」「无凭据时 push 挂起」「密钥被 Push Protection 拦截」等已知坑。当需要在 D:\lims（或任何 WorkBuddy 沙箱仓库）里 commit / merge / push 时使用。
agent_created: true
---

# 沙箱内 git 提交与推送工作流

## 触发场景

- 需要在本环境执行 `git commit` / `git merge` / `git push` / `git update-ref`
- 出现以下任一现象：
  - `fatal: your current branch 'agent/xxx' does not have any commits yet`（但明明刚 commit 过）
  - `fatal: Unable to create '.git/index.lock': File exists`
  - `schannel: next InitializeSecurityContext failed: CRYPT_E_NO_REVOCATION_CHECK`
  - `git push` 长时间无输出后挂起
  - `remote: error: GH013: Repository rule violations found ... push cannot contain secrets`
  - `git branch -vv` 显示 `[origin/xxx: gone]`
- 目标分支名含斜杠（如 `agent/glm`、`agent/copilot`）

## 前置条件

- 仓库已初始化，`origin` 可访问（本环境走 FastGithub 转发，`github.com` 可达；`api.github.com` 不可达）
- 有一个**具写权限**的 GitHub PAT（fine-grained 需 Contents: Read and write；只读会 403）
- 已确认当前在正确分支（`git branch -v`）

## 核心规则（必须遵守）

### 规则 1：`refs/heads/agent/*` 引用必须用 shell 回填

**git.exe 写含斜杠的分支引用会被静默丢弃**（`commit`/`update-ref`/`merge` 返回成功但 ref 不落盘，且会删掉整个 `.git/refs/heads/agent/` 目录）。`develop`/`main` 这类无斜杠分支不受影响。

每次 git.exe 操作后**立即**执行：

```bash
cd /d/lims
# 从 reflog 末行取真实 hash（第 2 列是新值）
HASH=$(tail -1 .git/logs/refs/heads/agent/glm | awk '{print $2}')
mkdir -p .git/refs/heads/agent
printf '%s\n' "$HASH" > .git/refs/heads/agent/glm
git branch -v   # 校验：不应再出现 "does not have any commits yet"
```

`refs/remotes/origin/*` 同样会被丢弃（表现为 `[origin/xxx: gone]`），纯显示问题，用 `ls-remote` 的真实结果回填：

```bash
mkdir -p .git/refs/remotes/origin/agent
printf '<hash>\n' > .git/refs/remotes/origin/agent/glm
printf '<hash>\n' > .git/refs/remotes/origin/develop
printf '<hash>\n' > .git/refs/remotes/origin/main
printf '<hash>\n' > .git/refs/remotes/origin/HEAD
```

### 规则 2：用 `update-ref` 代替 `checkout` 做分支快进

`git checkout <branch>` 可能被 SIGTERM 打断，留下半切换工作树（大量 ` D` 删除）+ 残留 `index.lock`。**合并 develop/main 时不要切分支**：

```bash
git update-ref refs/heads/develop <hash>   # 快进等价，且不会丢引用（无斜杠分支安全）
git update-ref refs/heads/main    <hash>
```

若已残留 `index.lock`：

```bash
rm -f .git/index.lock
git checkout -- .      # 仅在确实发生半切换时使用
```

### 规则 3：推送命令定型（含 `credential.helper=` 置空）

本机 `credential.helper=GCM` 但无缓存凭据，**无凭据时 `git push` 不报错而是长时间挂起**（GCM 弹窗阻塞沙箱）。必须：

```bash
GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never \
git -c http.sslVerify=false -c credential.helper= \
  push "https://<PAT>@github.com/<owner>/<repo>.git" agent/glm develop main
```

- `-c http.sslVerify=false`：绕过沙箱 MITM 代理（`127.0.0.1:2400`）的证书错误。**一次性用，勿写入 config。**
- `-c credential.helper=`：置空凭据助手，避免 GCM 挂起。**这是关键。**
- 诊断挂起：`GIT_CURL_VERBOSE=1 git ... push`，看是否 `401 WWW-Authenticate: Basic realm="GitHub"`。

### 规则 4：令牌权限判断只看 401/403/成功三态

`api.github.com` 在本环境不可达，无法用 API 验权限。且**不要凭令牌长度判断有效性**（本项目实测过一个远短于常规长度的令牌读写均正常）：

| 结果 | 含义 |
|---|---|
| `401` | 令牌无效 / 未提供 |
| `403` + `Permission ... denied` | 令牌**有效但无写权**（fine-grained PAT 的 Contents 默认 Read-only） |
| 推送成功 | 权限正确 |

### 规则 5（🔴 最重要）：令牌不得写入仓库任何文件

Push Protection 会以 `GH013: push cannot contain secrets` 拦截含密钥的推送。若已误提交：

```bash
# 1. 脱敏工作区文件
# 2. 改写掉含密钥的提交
git add -A
git commit -q --amend --no-edit
# 3. 回填引用（规则 1）
# 4. 重推
```

**记录推送结果时只写结论**（「令牌用于推送成功」「401 无效」「403 无写权」），**永不写令牌值**到 HANDOFF.md / DECISIONS.md / STATUS.md / 脚本。

## 标准执行清单

```bash
# ① 自检
cd /d/lims && git branch -v && git status --short

# ② 提交
git add -A
git -c user.name="GLM" -c user.email="glm@lims.local" \
  commit -q -m "<type>: <subject>"

# ③ 回填 agent 分支引用（规则 1）
HASH=$(tail -1 .git/logs/refs/heads/agent/glm | awk '{print $2}')
mkdir -p .git/refs/heads/agent && printf '%s\n' "$HASH" > .git/refs/heads/agent/glm
git branch -v

# ④ 推送
GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never \
git -c http.sslVerify=false -c credential.helper= \
  push "https://<PAT>@github.com/<owner>/<repo>.git" agent/glm

# ⑤ 快进 develop/main 再推（规则 2 + 3）
git update-ref refs/heads/develop "$HASH"
git update-ref refs/heads/main    "$HASH"
GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never \
git -c http.sslVerify=false -c credential.helper= \
  push "https://<PAT>@github.com/<owner>/<repo>.git" develop main

# ⑥ 核对远程权威状态
GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never \
git -c http.sslVerify=false ls-remote "https://<PAT>@github.com/<owner>/<repo>.git"

# ⑦ 按 ⑥ 结果回填 refs/remotes/origin/*（规则 1）
```

## 踩坑记录

| 现象 | 根因 | 处理 |
|---|---|---|
| `branch does not have any commits yet`（刚 commit 过） | git.exe 丢弃 `refs/heads/agent/*` | 规则 1 shell 回填 |
| `[origin/xxx: gone]` | git.exe 丢弃 `refs/remotes/origin/*` | 用 `ls-remote` 结果 shell 回填 |
| `index.lock: File exists` | 前次 SIGTERM 遗留 | `rm -f .git/index.lock` |
| 半切换工作树（大量 ` D`） | `git checkout` 被 SIGTERM | `rm -f .git/index.lock && git checkout -- .`，后续改用 `update-ref` |
| `CRYPT_E_NO_REVOCATION_CHECK` | 沙箱 MITM 代理证书链 | `-c http.sslVerify=false`（一次性） |
| `push` 长时间挂起 | GCM 弹窗阻塞，无缓存凭据 | `-c credential.helper=` + `GCM_INTERACTIVE=never` |
| `403 Permission denied` | fine-grained PAT Contents 只读 | 改为 Contents: Read and write |
| `GH013 push cannot contain secrets` | 仓库文件含明文令牌 | 脱敏 + `commit --amend` 改写历史后重推 |
| `Everything up-to-date`（但远程确实落后） | 本地 develop/main 引用未跟上 | 先 `update-ref` 再推 |

## 附：Bash 工具传大段 Python 代码的坑

用 `python -c "..."` 传含**中文 + 反引号 + 引号**的大段代码时，会被 shell 错误解析（大量 `command not found` + SIGTERM，甚至误执行产物）。

**正确做法**：用 Write 工具写临时 `.py` 文件到 `C:\Users\Chen\AppData\Local\Temp\`，再执行：

```bash
"C:/Users/Chen/.workbuddy/binaries/python/versions/3.13.12/python.exe" "C:/Users/Chen/AppData/Local/Temp/xxx.py"
```
