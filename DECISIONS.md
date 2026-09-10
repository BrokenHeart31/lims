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
| 2026-09-10 | 认证域契约终审：采纳 camelCase（accessToken/refreshToken、user/permissions/menus），新增 expiresIn/user.deptId/MenuNode.parentId；JSON 字段一律 camelCase | 与 T-003 前端实现一致，前端零改动；camelCase 为 JSON 通例 | Copilot |
| 2026-09-10 | 共享检验员账号定为 njsa000（说明书"njsa00"系笔误；其密码列三行同为 njna000 按"账号名即初始密码"理解） | 与 njna000/njxa000 命名对齐，AGENTS.md 7.4/8.1 为准 | Copilot |
| 2026-09-10 | 后端引入 Lombok（BOM 管理 1.18.34），maven-compiler-plugin 显式声明 annotationProcessorPaths | 减少样板代码；高版本 JDK 下保证注解处理稳定生效 | Copilot |
| 2026-09-10 | 后端统一 server.servlet.context-path=/api | 与前端 vite 代理（/api→8080 无 rewrite）对齐，契约路径直观 | Copilot |
| 2026-09-10 | Spring Boot 定版 3.3.2（不追 4.x） | AGENTS 约束 Spring Boot 3.x；MP 3.5.7 对 Boot 4 兼容性未验证；本地 .m2 已缓存 | Copilot |
| 2026-09-10 | 安全层 401/403 返回 HTTP 状态码 + 统一响应体；业务异常 HTTP 200 + body.code | 前端 request.ts 双通道均已处理；语义清晰 | Copilot |
