# SKILL: rbac-backend —— RBAC 权限后端落地

> 来源任务：T-101（建表）/ T-102（认证授权）/ T-107（维护界面）｜ 提炼：Copilot ｜ 2026-09-11
> 2026-09-13 GLM 补充「§维护界面失效模式防护清单」（T-107 实测）
> 状态：✅ 已在本仓库验证（豆包实测 nj001 登录 /me 49 权限 / @PreAuthorize 生效；
> T-107 全部防护经真实 HTTP 实测触发，见 `docs/journal/2026-09-13-glm-t105-107-603-803.md`）

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
- **权限标识在代码中使用但 seed 未定义** = 非特权角色永远 403，而被 R100 硬编码权限掩盖，
  测试时容易漏掉。**每加一个 `@PreAuthorize("hasAuthority('x:y')")` 就顺带 grep 一次 seed 是否有该行**。
  （本项目 T-106 踩到：`base:lib:add/edit/remove` 三个标识只在 Controller 里有，seed 只有 `base:lib:list`。）
- **`LocalDateTime` 在 VO 里必须显式 `@JsonFormat`**：`spring.jackson.date-format` 只对
  `java.util.Date` 生效，对 JSR-310 无效。不标则输出 ISO 串（`2026-09-13T14:58:39`），
  与项目其余 VO 的 `yyyy-MM-dd HH:mm:ss` 不一致，前端要写两套解析。

---

## §维护界面失效模式防护清单（T-107 实测，**必读**）

RBAC 维护界面看起来是「最简单的 CRUD」，但它是**失效模式最密集**的地方——
一次误操作可能让全系统失管且不可逆。完整原理见
`docs/knowledge/2026-09-13-rbac-maintenance-guardrails.md`，此处为速查表。

### 13 项必做防护

| # | 失效模式 | 防护 | 返回 |
|---|---|---|---|
| 1 | 自锁（删/停用自己） | 拒绝；且保护「最后一个启用中的特权账号」 | 409 |
| 2 | 特权角色（R100）被破坏 | ①编码不可改 ②权限绑定请求**直接跳过**（写了也不生效，显式忽略比静默写入诚实）③不可删除 | 409 |
| 3 | 无角色用户 | 有用户绑定时拒绝删除，**返回具体人数** | 409 |
| 4 | 树形成环 | 沿 `parent_id` 上溯检测 + **`guard < 64` 迭代上限**（防脏数据致 while 挂死服务） | 400 |
| 5 | 权限标识重复 | 唯一性前置拦截（DB 有 UNIQUE，前置拦截给可读错误而非 SQL 异常） | 400 |
| 6 | 权限标识格式错 | `@Pattern(regexp = "^$|^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$")` | 400 |
| 7 | 菜单形态错配 | 按钮必有 permission / **目录菜单不应有**（防误导性数据）/ 菜单必有 path | 400 |
| 8 | 孤儿关联行 | 删除菜单时**级联物理清理** `sys_role_menu`（关联表无逻辑删除） | — |
| 9 | 删有引用的主数据 | 子节点 + 用户双向校验（**计数须过滤 `deleted=0`**，否则已删子节点会让父节点永远删不掉） | 409 |
| A | 敏感字段泄露 | VO **类型层面无** `password`/`salt` 字段（不用 `@JsonIgnore`——注解被误删即泄露） | — |
| B | 密码误改 | 重置密码**独立接口 + 独立 DTO**（混入主 update 会在缺字段时误清密码） | — |
| C | 审计锚点漂移 | 登录名创建后不可改（`created_by`/`operator` 都引用它） | — |
| D | 重复绑定 | 关联绑定一律**全量覆盖式**（先删后建）+ `distinct()` | — |
| E | N+1 | 反查字段（deptName/roleNames）用**批量 IN** 再内存映射 | — |

### 关键代码片段

**自锁保护 + 最后一个特权账号**：

```java
if (currentUserId.equals(targetUserId)) {
    throw new BizException(ResultCode.CONFLICT, "不能删除当前登录账号");
}
// ⚠️ 必须同时防「停用」——只防删除是常见疏漏
if (newStatus != null && newStatus == 0 && isAdminUser(target.getId())
        && countActiveAdmins() <= 1) {
    throw new BizException(ResultCode.CONFLICT,
        "系统必须保留至少一名启用状态的综合管理（R100）账号");
}

/** 用 inSql 子查询统计，避免 N+1 */
private long countActiveAdmins() {
    return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
        .eq(SysUser::getStatus, 1)
        .inSql(SysUser::getId,
            "SELECT ur.user_id FROM sys_user_role ur "
          + "JOIN sys_role r ON ur.role_id = r.id "
          + "WHERE r.role_code = 'R100' AND r.deleted = 0"));
}
```

**成环检测（必须有 guard 上限）**：

```java
Long cursor = parentId;
int guard = 0;
while (cursor != null && cursor != 0) {
    if (Objects.equals(cursor, id)) {
        throw new BizException("上级节点不能是自身的下级节点（会形成循环）");
    }
    if (++guard > 64) {          // ⚠️ 库里已有脏数据时，无上限的 while 会挂死服务
        throw new BizException("层级过深或存在循环，请检查数据");
    }
    Dept parent = getById(cursor);
    cursor = parent == null ? null : parent.getParentId();
}
```

**删除时级联清理关联表**：

```java
@Transactional(rollbackFor = Exception.class)
public void remove(Long id) {
    // 关联表无逻辑删除 → 必须物理清理，否则角色权限树出现「指向不存在菜单的勾选态」
    sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
        .eq(SysRoleMenu::getMenuId, id));
    removeById(id);
}
```

**全量覆盖式绑定**：

```java
private void rebindRoles(Long userId, List<Long> roleIds) {
    sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
        .eq(SysUserRole::getUserId, userId));
    if (CollUtil.isNotEmpty(roleIds)) {
        roleIds.stream().distinct().forEach(rid -> /* insert */);   // distinct 必要
    }
}
```

### 验证方式

**全部防护必须用真实 HTTP 请求触发验证**，确认返回 409（状态冲突）/ 400（请求非法）
且错误信息为**可读中文并指明具体数量或原因**。不要只写代码不验证——
这些防护恰恰是「看起来写了、实际没生效」的高发区。

```bash
# 实测样例（本项目已通过）
curl -X DELETE -H "Authorization: Bearer $T" "$B/sys/user/1"          # → 409 不能删除当前登录账号
curl -X PUT    -d '{"id":1,...,"status":0}' "$B/sys/user"             # → 409 必须保留至少一名 R100
curl -X DELETE "$B/sys/role/1"                                        # → 409 R100 不允许删除
curl -X POST   -d '{"menuType":3,"title":"按钮"}' "$B/sys/menu"        # → 400 按钮必须填权限标识
curl -X DELETE "$B/sys/dept/1"                                        # → 409 还有 5 个子部门
```
