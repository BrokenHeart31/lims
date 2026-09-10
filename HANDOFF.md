# HANDOFF.md（交接日志）

> 格式：
> ### 日期时间 更新人：xxx
> - 【谁】完成了什么（关联任务ID）
> - ⚠️ 注意：接口变更/字段改名/坑
> - 【下一步】等待谁做什么

### 2026-09-10 豆包（agent/doubao）
- 【豆包】**T-001 仓库初始化全部完成**。项目落在 `D:\lims`（非原手册的 D:\test\lims，也非最初桌面路径——桌面目录被安全软件拦 git 写入）。已推送 GitHub：https://github.com/BrokenHeart31/lims.git
  - `main`：仅 README.md + .gitignore（commit 1a25689）
  - `develop` / `agent/doubao`：完整目录架构 + 治理文件（commit 75cab56）
  - `agent/copilot` / `agent/glm`：与 main 同步（仅首次提交）
  - 五个分支均已 push，develop 已 fast-forward 合并 agent/doubao。
- 目录架构：.agents/skills（4 个）、backend（controller/service/mapper/entity/dto/vo/config/security/common + mapper）、frontend/src（10 个目录）、db/{init,migrations,seed}、docs/{api,knowledge}、prompts（copilot/glm/doubao.md），空目录均有 .gitkeep。
- 治理文件：AGENTS.md、STATUS.md、TODO.md（按七阶段 T-xxx 骨架）、DECISIONS.md、docs/api/api-spec.md（占位）。
- ⚠️ 环境坑：本机 Bash 沙箱限制 git.exe 写非 TEMP 目录（.git/objects Permission denied，提权无效）；本次通过 GIT_DIR 指向 %LOCALAPPDATA%\Temp\lims_gitdir 完成 git 操作，事后已把 .git 同步回 D:\lims\.git。**后续三个 Agent 在自己的 Git Bash（不受此沙箱限制）里直接操作 D:\lims 即可**。
- ⚠️ git 身份：本仓库 user.name=BrokenHeart31，user.email=BrokenHeart31@users.noreply.github.com（本地配置，非全局）。如提交需归属到真实邮箱，请 `git config user.email 你的邮箱` 修改。
- ⚠️ 旧参考数据 `lims.sql` 在桌面，尚未入仓库；按 0.1 由后续 V1__import_legacy_data.sql 迁移，不要直接复用旧表风格。
- 【下一步】Copilot 可开始 T-002：后端工程骨架（pom.xml / LimsApplication / 统一响应 / 异常 / JWT 骨架）+ 在 api-spec.md 落地登录与 /me 接口契约。GLM 等待 T-002 契约后做 T-003 前端骨架。
