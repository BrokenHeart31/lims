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
| 2026-09-10 | 基础表 DDL（basis/customer/dept）由豆包按 6.1 规范先落 db/init/01_basic_tables.sql，product_lib/product_lib_item 作为 T-401 草稿一并落库供 V1 迁移 | T-103/T-104 需可跑；product_lib* 为 S 级领域草稿，Copilot 终审可改 | 豆包 |
| 2026-09-10 | T-201 前端按约定契约 /api/task/*（page/{id}/POST/PUT/DELETE，camelCase，分页 records/total）先行 | Copilot 任务域契约未落地；调整面收敛在 src/api/task.ts | 豆包（代 GLM） |
| 2026-09-10 | 前端引入 ESLint 9 扁平配置（eslint.config.js），新增 devDeps eslint/typescript-eslint/eslint-plugin-vue/globals，npm run lint 门禁 | AGENTS 第 9 章要求 npm run lint；T-004 补齐 | 豆包（代 GLM） |
| 2026-09-10 | RBAC 表采用 sys_ 前缀（sys_user/sys_role/sys_menu/sys_user_role/sys_role_menu），部门复用业务 dept 表并补 parent_id | user 为 MySQL 函数名须转义，sys_ 前缀（RuoYi 惯例）规避；dept 已被 01 建为业务表，加列比重建安全 | Copilot |
| 2026-09-10 | 业务字典字段（任务性质/区域级别/抽样环节/任务状态等）以 VARCHAR 存中文字典值；样品状态机 S10→S90 强制 TINYINT+枚举 | 字典值源自下达文书需原样展示打印，值域稳定；6.1 的 TINYINT 规则针对状态机类枚举（T-301 起严格执行） | Copilot |
| 2026-09-10 | product_lib_item 增加 judge_type（1=限量比较 2=不得检出/不得使用 3=文本/感官人工），V1 按 stdValue 形态推导；T-401 表结构就此定稿 | T-601 自动判定引擎（AGENTS 7.3 规则 1/2/3）直接消费，避免引擎每次重判文本形态 | Copilot |
| 2026-09-10 | JWT 过滤器每请求按 username 从 DB 装配 LoginUser，token 内 perms claim 仅作签发快照不作鉴权依据；R100 在代码层 isAdmin 短路拥有全部权限/菜单 | 权限变更即时生效（踢人/改权无需等 token 过期）；小系统 DB 查询开销可接受 | Copilot |
| 2026-09-10 | 路由守卫对已登录未加载 me 的导航先 await fetchMe | 保证 v-permission 指令在页面渲染前拿到权限数据，避免按钮被误移除 | Copilot |

| 2026-09-10 | V1 迁移源旧表以 customer_legacy/dept_legacy 后缀导入（lims.sql 旧 customer/dept 与 01 新建表同名异构），V1 源表同步改名；lims.sql 本身不入库 | 同库并存前提下旧表与新表同名会覆盖/1050 冲突；_legacy 后缀最小改动，第 5 节校验仍读新表 | 豆包（实测发现，Copilot 终审通过 2026-09-11） |
| 2026-09-11 | application.yml 的 JDBC url 中 characterEncoding 用 utf8（Java 字符集名），禁用 utf8mb4 | Connector/J 8 报 Unsupported character encoding 'utf8mb4'；库表 utf8mb4 由 DDL/连接协商保证 | Copilot |
| 2026-09-11 | 追认 AGENTS.md 角色表修订：GLM=首席架构师/后端核心/终审（S 级），Copilot=前端主力/常规 CRUD（A 级）；Copilot 分支以 `agent/copilot` 为准（首页表格"agent/gpt"为笔误，豆包下轮统一） | 与实际分工一致；分支名以 0.3 节和实际远程分支为准 | Copilot |
