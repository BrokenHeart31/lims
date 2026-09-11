# SKILL: rbac-backend —— RBAC 权限后端落地

> 来源任务：T-101（建表）/ T-102（认证授权）｜ 提炼：Copilot ｜ 2026-09-11
> 状态：✅ 已在本仓库验证（豆包实测 nj001 登录 /me 49 权限 / @PreAuthorize 生效）

## 触发场景

需要为模块加「用户/角色/菜单权限」体系，或给新接口挂按钮级权限时。

## 前置

- 建表脚本 `db/init/02_rbac_tables.sql` 已执行（sys_user/sys_role/sys_menu/sys_user_role/sys_role_menu + dept）
- 种子 `db/seed/01_rbac_seed.sql` 已执行（4 角色 + 6 账号，密码=账号名 BCrypt）
- 工程骨架已含 `security/`（JwtTokenProvider/JwtAuthenticationFilter/LoginUser/SecurityUtils）

## 权威示例文件（照抄结构，勿自创）

| 层 | 文件 |
|---|---|
| 认证接口 | `backend/.../controller/AuthController.java`（login/refresh/me/logout） |
| 认证编排 | `service/impl/AuthServiceImpl.java`（防枚举：用户不存在与密码错误同报"用户名或密码错误"；停用账号 401） |
| 权限装配 | `service/impl/SysPermissionServiceImpl.java` + `security/UserDetailsServiceImpl.java` |
| 权限取用 | `security/SecurityUtils.java`（getLoginUser/getUsername/hasPermission） |

## 关键决策（已记 DECISIONS.md，复用时直接沿用）

1. **JWT 过滤器每请求按 username 从 DB 装配 LoginUser**——token 内 perms 仅作签发快照，权限以 DB 为权威源（踢人/改权即时生效）。
2. **R100 系统管理员代码层 isAdmin 短路**全部权限与菜单，不靠 seed 穷举授权。
3. 鉴权入口双异常：`RestAuthenticationEntryPoint`(401) / `RestAccessDeniedHandler`(403) 返回统一 R 体且带 HTTP 状态码；业务异常一律 HTTP 200 + body.code。
4. 菜单树 perms 字段与前端 `v-permission`、后端 `@PreAuthorize("hasAuthority('xxx')")` **三处共用同一标识**，命名 `域:资源:动作`（如 `task:list`），全集登记在 AGENTS 8.2 + api-spec 对应域。

## 新模块挂权限步骤

1. api-spec 对应域接口条目上声明权限标识（Copilot 独有改动权）。
2. `db/seed/01_rbac_seed.sql` 增补菜单行（type=3 按钮级，perms=标识）+ 授权给角色，保持脚本可重复执行（INSERT 前先 DELETE 同键）。
3. Controller 方法加 `@PreAuthorize("hasAuthority('标识')")`。
4. 前端按钮加 `v-permission="'标识'"`（见 vue3-crud-page skill）。
5. 验证：无权限账号调接口应得 HTTP 403 + body.code=403。

## 踩坑

- **表名必须 sys_ 前缀**：`user`/`role` 是 MySQL 保留/函数名，裸用会踩坑。
- VO 禁止出现 password（AGENTS 1 红线）；SysUser 出网一律脱敏。
- login 接口不能把「用户不存在」和「密码错误」分开报（防账号枚举）。
- token 刷新只换 access，refreshToken 复用至 7 天过期；登出为前端清 token（无状态 JWT 无服务端注销）。
