# STATUS.md（共享白板）

> 规则：开工前在此声明本轮占用的文件/模块；收工后更新。任何 Agent 30 秒读懂全局。

## 当前工作分支
- 豆包：`agent/doubao`（仓库初始化 + 目录架构 + 治理文件）✅ 完成
- GLM：`agent/glm`（T-003 前端工程骨架）✅ 完成（Copilot 终审通过）
- Copilot：`agent/copilot`（T-002 后端工程骨架 + api-spec 认证域）🔵 进行中

## 项目位置
- 本地仓库：`D:\lims`（远程 https://github.com/BrokenHeart31/lims.git ）

## 本轮占用文件
- 豆包：根目录治理文件 + docs/api/api-spec.md + prompts/*（已提交并推送）
- GLM：frontend/ 全部新增文件（已提交并推送）
- Copilot：backend/**（pom.xml、application.yml、LimsApplication、common/、config/、security/）+ docs/api/api-spec.md + 治理文件四件套更新（STATUS/TODO/HANDOFF/DECISIONS）

## 他人占用
- （无）

## 当前阻塞
- ✅ 已解除：仓库已初始化并推送，五个分支就绪。
- ⚠️ WorkBuddy 沙箱内 git.exe 无法写入 `.git/refs/heads/agent/` 子目录（静默丢弃，无报错）；恢复方法：shell 手工 `echo <hash> > .git/refs/heads/agent/<name>`，恢复值看 reflog 末行。
- ⚠️ 沙箱内 git 推送走 MITM 代理证书校验失败；用一次性 `git -c http.sslVerify=false push` 绕过（不落盘配置）。
- ℹ️ 后端构建环境：JDK 21 可用（D:\Program Files\Java\jdk-21.0.10）；Maven 3.9.12 缓存在 ~/.m2/wrapper/dists，但 mvn 脚本在沙箱内解析 MAVEN_HOME 失败，需直启 classworlds（启动器脚本见 HANDOFF 2026-09-10 Copilot 条目）。
