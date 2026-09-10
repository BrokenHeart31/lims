# DECISIONS.md（决策记录）

> 格式：日期 | 决策 | 理由 | 决策人

| 日期 | 决策 | 理由 | 决策人 |
|---|---|---|---|
| 2026-09-10 | 新旧库并存原则（AGENTS.md 0.1） | 旧 lims.sql 风格禁止复用，数据走 V1 迁移脚本单向清洗进新表 | 全员 |
| 2026-09-10 | 分支策略 agent/xxx → develop → main | 多 Agent 防冲突，保留每周 main 固化考核 | 全员 |
| 2026-09-10 | 项目根目录定在 `C:\Users\Chen\Desktop\lims`（非手册原写的 `D:\test\lims`） | 用户指定实际路径 | 豆包记录 |
| 2026-09-10 | 实际仓库位置修正为 `D:\lims`（桌面路径被安全软件拦截 git 写入） | 覆盖上一条，与 HANDOFF/STATUS 一致 | 豆包（GLM 顺手修正） |
| 2026-09-10 | T-003 前端骨架由 GLM 完整实现，含 package.json/vite.config/tsconfig/router 等公共文件，Copilot 终审 | TODO T-003 明确指派 GLM，与 AGENTS.md 2.1 所有权表冲突；按任务指派执行并留痕待审 | GLM |
| 2026-09-10 | 认证域前端实现按 AGENTS.md 4.1/8.3 约定先行，JSON 字段命名暂定 camelCase（accessToken/refreshToken），以 Copilot api-spec.md 终审为准 | 契约未落地但骨架需先行；契约变更影响面收敛在 src/api/auth.ts + src/stores/auth.ts | GLM |
| 2026-09-10 | 前端 token 存 localStorage（key=lims_access_token），401 由 axios 拦截器统一清除并跳 /login?redirect= | 禁止 Token 入 URL；刷新 token 机制待 T-002 后端设计 | GLM |
