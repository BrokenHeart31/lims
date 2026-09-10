# STATUS.md（共享白板）

> 规则：开工前在此声明本轮占用的文件/模块；收工后更新。任何 Agent 30 秒读懂全局。

## 当前工作分支
- 豆包：`agent/doubao`（仓库初始化 + 目录架构 + 治理文件）✅ 完成
- GLM：`agent/glm`（T-003 前端工程骨架）✅ 完成，待 Copilot review

## 项目位置
- 本地仓库：`D:\lims`（远程 https://github.com/BrokenHeart31/lims.git ）

## 本轮占用文件
- 豆包：根目录治理文件 + docs/api/api-spec.md + prompts/*（已提交并推送）
- GLM：frontend/ 全部新增文件（package.json、vite.config.ts、tsconfig*、.env.*、index.html、src/main.ts、App.vue、router/、utils/request.ts、api/auth.ts、stores/auth.ts、layouts/、views/、types/）+ 治理文件四件套更新（STATUS/TODO/HANDOFF/DECISIONS）

## 他人占用
- （无）

## 当前阻塞
- ✅ 已解除：仓库已初始化并推送，五个分支就绪。
- ⚠️ WorkBuddy 沙箱内 git.exe 无法写入 `.git/refs/heads/agent/` 子目录（静默丢弃，无报错）；本轮已用 shell 手工恢复三个 agent/* 分支引用。详见 HANDOFF.md 2026-09-10 GLM 条目。
- ⚠️ 沙箱内 `git fetch/push` 受 schannel 证书吊销检查限制（CRYPT_E_NO_REVOCATION_CHECK），已在仓库本地配置 `http.schannelCheckRevoke=false`，推送若仍失败需在本机非沙箱终端补推。
