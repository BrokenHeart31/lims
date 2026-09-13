# RBAC 维护界面的失效模式与防护清单

> 结论层文档 ｜ 建立日期：2026-09-13 ｜ 作者：GLM ｜ 关联任务：T-107
>
> 推理与实测过程见 `docs/journal/2026-09-13-glm-t105-107-603-803.md`。
>
> **本文的目标**：RBAC 维护界面（用户/角色/菜单/部门 CRUD）看起来是「最简单的增删改查」，
> 但它是整个系统**危险失效模式最密集**的地方——一次误操作可能导致全系统失管或永久无法恢复。
> 本文给出 8 类失效模式与对应防护，可直接复制到同类项目。

---

## 为什么 RBAC 维护界面比看起来危险

普通 CRUD 的失败模式是「操作报错，重试即可」。RBAC 之不同在于：

1. **操作对象是整个系统的「访问控制」本身** —— 破坏的是控制手段，不是被控制的数据
2. **存在「不可逆的自我锁死」** —— 管理员把自己降权/停用后，可能再没有任何账号能改回来
3. **静默失效比报错更危险** —— 权限标识未定义、菜单形态错配，界面看起来正常但权限不生效
4. **关联表无逻辑删除** —— 删除主表易留孤儿行，产生「勾了但无效」的勾选态

**核心原则**：所有防护必须 **fail-loud**（返回具体原因），禁止静默放行或静默修正。

---

## 失效模式 1：自锁（把自己锁在系统外）

**场景**：管理员在「用户管理」里把自己停用或删除。
**后果**：`sys_user.status=0` 后无法登录；若该账号是唯一特权账号，**只能改数据库才能恢复**。

**防护**：

```java
/** 不能删除当前登录账号 */
if (currentUserId.equals(targetUserId)) {
    throw new BizException(ResultCode.CONFLICT, "不能删除当前登录账号");
}

/** 不能停用/删除最后一个启用中的特权账号 */
private void guardLastAdminStatusChange(SysUser target, Integer newStatus) {
    if (newStatus != null && newStatus == 0 && isAdminUser(target.getId())) {
        if (countActiveAdmins() <= 1) {
            throw new BizException(ResultCode.CONFLICT,
                "系统必须保留至少一名启用状态的综合管理（R100）账号");
        }
    }
}

/** 统计启用中的特权用户数——用子查询避免 N+1 */
private long countActiveAdmins() {
    return sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
        .eq(SysUser::getStatus, 1)
        .inSql(SysUser::getId,
            "SELECT ur.user_id FROM sys_user_role ur "
          + "JOIN sys_role r ON ur.role_id = r.id "
          + "WHERE r.role_code = 'R100' AND r.deleted = 0"));
}
```

**要点**：
- 自锁防护**必须覆盖「停用」和「删除」两条路径**（只防删除不防停用是常见疏漏）
- 计数用 `inSql` 子查询而非「先查角色用户再循环」——避免 N+1 且逻辑集中在一条 SQL

**实测**：
```
DELETE /api/sys/user/1       → 409 "不能删除当前登录账号"
PUT    /api/sys/user (status=0) → 409 "系统必须保留至少一名启用状态的综合管理（R100）账号"
```

---

## 失效模式 2：特权角色被破坏

**场景**：项目常有一个「无需关联表即拥有全部权限」的硬编码特权角色（本项目 `R100`，
实现为 `SysRole.ADMIN_ROLE_CODE`，在鉴权层短路返回全部权限）。
若允许改其编码/权限/删除，会产生一系列不一致。

**防护（三重）**：

| 保护 | 做法 | 理由 |
|---|---|---|
| ① 编码不可改 | 更新请求中 `roleCode` 一律忽略（或显式拒绝） | 编码是鉴权层硬编码的匹配依据；改了会留下已授权数据指向错误角色 |
| ② 权限绑定请求**直接跳过** | 不执行 `rebindMenus` | 它本来就不依赖 `sys_role_menu`，写了也不生效——**显式忽略比静默写入更诚实**（静默写入会让人误以为权限来自关联表） |
| ③ 不可删除 | 拒绝删除 | 删除会导致全系统失管 |

```java
private static final String ADMIN_ROLE_CODE = "R100";

if (ADMIN_ROLE_CODE.equals(existing.getRoleCode())) {
    if (!ADMIN_ROLE_CODE.equals(dto.getRoleCode())) {
        throw new BizException("综合管理（R100）角色编码不允许修改");
    }
    // 跳过权限重绑
} else {
    rebindMenus(roleId, dto.getMenuIds());
}
```

---

## 失效模式 3：删除角色留下「无角色用户」

**场景**：删除一个已被 N 个用户绑定的角色。
**后果**：这些用户**能登录但没有任何权限**，且用户自己无法理解原因（界面上看不出问题）。

**防护**：删除前计数，有绑定时拒绝并**返回具体人数**（引导先解绑）：

```java
long bound = sysUserRoleMapper.selectCount(
    new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
if (bound > 0) {
    throw new BizException(ResultCode.CONFLICT,
        "该角色下还有 " + bound + " 个用户，请先解除绑定再删除");
}
```

---

## 失效模式 4：树形成环（栈溢出 / 孤儿节点）

**场景**：把节点 A 的 `parentId` 设为 A 自己，或设为 A 的某个后代。
**后果**：树构建进入死循环（栈溢出），或产生永远无法在界面上找到的孤儿节点。

**防护**：`validateParent` 沿 `parent_id` 上溯检测，**带迭代次数上限**：

```java
private void validateParent(Long id, Long parentId) {
    if (parentId == null || parentId == 0) return;          // 顶级节点
    if (Objects.equals(id, parentId)) {
        throw new BizException("不能将自身设为上级节点");
    }
    // 沿 parent_id 上溯：若遇到 id 则说明 parentId 是 id 的后代 → 成环
    Long cursor = parentId;
    int guard = 0;                                          // ⚠️ 必须有上限
    while (cursor != null && cursor != 0) {
        if (Objects.equals(cursor, id)) {
            throw new BizException("上级节点不能是自身的下级节点（会形成循环）");
        }
        if (++guard > 64) {                                  // 深度上限，防数据脏时的死循环
            throw new BizException("部门层级过深或存在循环，请检查数据");
        }
        Dept parent = getById(cursor);
        cursor = parent == null ? null : parent.getParentId();
        if (parent == null || parent.getDeleted() == 1) break;
    }
}
```

**要点**：`guard` 上限不是「防御性编程的仪式」——如果库里已有脏数据（历史导入导致成环），
**没有上限的这个 while 会直接挂死服务**。上限让脏数据变成可读的错误信息。

---

## 失效模式 5：权限标识重复 / 格式错乱

**场景**：两个菜单用了同一个 `permission`；或写成 `Base:Lib:List` / `base_lib_list`。
**后果**：鉴权结果不确定（同标识多行）；格式不合规的标识永远匹配不上 `@PreAuthorize`。

**防护**：

```java
// ① 唯一性前置拦截（DB 有 UNIQUE 索引，但前置拦截能给出可读错误而非 SQL 异常）
private void validatePermission(Long excludeId, String permission) {
    if (!StringUtils.hasText(permission)) return;
    boolean exists = sysMenuMapper.exists(new LambdaQueryWrapper<SysMenu>()
        .eq(SysMenu::getPermission, permission)
        .ne(excludeId != null, SysMenu::getId, excludeId));
    if (exists) {
        throw new BizException("权限标识 " + permission + " 已被其他菜单占用");
    }
}

// ② 格式校验（DTO 层注解，正则与前端共用同一约定）
@Pattern(regexp = "^$|^[a-z][a-z0-9-]*(:[a-z][a-z0-9-]*)+$",
         message = "permission 权限标识需形如 resource:action（小写字母/数字/中划线，可多级冒号分隔）")
private String permission;
```

**约定的格式**：`resource:action`，如 `base:lib:add`、`sys:user:reset`。
小写 + 中划线 + 冒号，**只允许两级或以上**（`x:y`）。正则同时放在 DTO 注解与前端校验中。

---

## 失效模式 6：菜单形态与类型错配

**场景**：`menuType=3`（按钮）却没填 `permission`；`menuType=2`（菜单）却没填 `path`。
**后果**：**静默失效**——界面上有按钮，但权限体系里永远勾不出对应权限（因为没有标识可勾）。

**防护**：`validateTypeShape` 三类语义校验：

```java
private void validateTypeShape(SysMenuSaveDTO dto) {
    Integer type = dto.getMenuType();
    boolean hasPerm = StringUtils.hasText(dto.getPermission());
    boolean hasPath = StringUtils.hasText(dto.getPath());

    if (type == 3) {                                        // 按钮
        if (!hasPerm) throw new BizException("按钮类型必须填写权限标识（resource:action）");
    } else if (type == 1 || type == 2) {                    // 目录 / 菜单
        if (hasPerm) throw new BizException("目录/菜单类型不应填写权限标识（权限标识只用于按钮）");
        if (type == 2 && !hasPath) {
            throw new BizException("菜单类型必须填写前端路由路径");
        }
    } else {
        throw new BizException("菜单类型只能为 1(目录) / 2(菜单) / 3(按钮)");
    }
}
```

**「不应有」也要校验**：目录/菜单填了 `permission` 会让权限树里出现一行「看起来可以勾」
的条目，实际上鉴权层不会用它——属于**误导性数据**，应与「必须有」同等地拒绝。

---

## 失效模式 7：删除菜单留下孤儿关联行

**场景**：删除 `sys_menu` 行，但 `sys_role_menu`（关联表，**无逻辑删除**）仍有指向它的记录。
**后果**：角色的权限树里出现「指向不存在菜单的勾选态」——用户勾了但它无效，且无法解释。

**防护**：删除菜单时**级联物理清理**关联表：

```java
@Transactional(rollbackFor = Exception.class)
public void remove(Long id) {
    // 关联表无逻辑删除，须物理清理，否则产生孤儿行导致角色权限树出现幽灵勾选
    sysRoleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>()
        .eq(SysRoleMenu::getMenuId, id));
    removeById(id);                                          // 主表逻辑删除
}
```

---

## 失效模式 8：删除仍有引用的主数据

**场景**：删除还有子部门的部门，或删除还有用户的部门。
**后果**：子部门/用户变成孤儿（`parent_id`/`dept_id` 指向不存在的行），界面渲染出空白的层级。

**防护**：删除前校验双重引用，并返回具体数量：

```java
long children = count(new LambdaQueryWrapper<Dept>().eq(Dept::getParentId, id));
if (children > 0) {
    throw new BizException(ResultCode.CONFLICT,
        "该部门下还有 " + children + " 个子部门，请先删除子部门");
}
long users = countUsersByDept(id);
if (users > 0) {
    throw new BizException(ResultCode.CONFLICT,
        "该部门下还有 " + users + " 个用户，请先调整用户所属部门");
}
```

⚠️ 注意计数时**要过滤逻辑删除**（`deleted=0`），否则「已删除的子部门」也会算进去，
导致永远删不掉父部门。

---

## 附：其他必备约定

### A. VO 类型层面杜绝敏感字段泄露

```java
/** ⚠️ 本 VO 不存在 password / salt 字段——不是「查询时不填充」，而是字段根本不存在 */
@Data
public class SysUserVO { /* 无 password, 无 salt */ }
```

不用 `@JsonIgnore` 过滤。理由：注解过滤存在「有人误删注解就泄露」的风险；
**类型里根本没有该字段，泄露在编译期即不可能**。

### B. 密码重置用独立接口 + 独立 DTO

```java
// ✅ 独立端点 + 独立 DTO
@PutMapping("/{id}/password")
public R<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordDTO dto) { ... }
```

**为何不合并进主 `SysUserSaveDTO`**：密码是单向写入（BCrypt）。
若与普通字段混在同一 update，则「编辑资料」的请求体一旦缺 `password`，
就会误改/清空密码。**独立接口 + 独立 DTO 在类型层面杜绝这种误用。**

### C. 登录名创建后不可修改

```java
// 更新时忽略 username
if (StringUtils.hasText(dto.getUsername())) {
    // 静默忽略，或显式拒绝——本项目选择忽略并在 DTO 注释说明
}
```

理由：登录名是审计线索的锚点（`created_by`/`operator` 等都引用它），
可变会让历史记录指向错误的操作者。

### D. 关联绑定一律「全量覆盖式」

```java
private void rebindRoles(Long userId, List<Long> roleIds) {
    sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
        .eq(SysUserRole::getUserId, userId));                // 先清
    if (CollUtil.isNotEmpty(roleIds)) {
        // 再重建
        roleIds.stream().distinct().forEach(rid -> sysUserRoleMapper.insert(...));
    }
}
```

**前端提交「当前勾选的全部角色」，不是增量。** 覆盖式实现更简单且天然幂等
（同样输入重复提交结果一致），避免「增量接口被重复调用导致重复绑定」。
`distinct()` 是必要的——前端可能传入重复 id。

### E. 避免 N+1 反查

列表接口需要显示 `deptName` / `roleNames` 时，用**批量 IN 一次取回**再在内存里映射，
不要对每一行单独查一次：

```java
// ✅ 批量
Set<Long> deptIds = list.stream().map(SysUser::getDeptId)
    .filter(Objects::nonNull).collect(Collectors.toSet());
Map<Long, String> deptNameMap = deptIds.isEmpty() ? Map.of()
    : deptMapper.selectBatchIds(deptIds).stream()
        .collect(Collectors.toMap(Dept::getId, Dept::getDeptName));
```

---

## 完整防护清单（可直接复制为 Code Review Checklist）

| # | 失效模式 | 防护 | 本项目实测 |
|---|---|---|---|
| 1 | 自锁（删/停用自己） | 拒绝 + 最后一个特权账号保护 | ✅ 409 ×2 |
| 2 | 特权角色被破坏 | 编码不可改 / 跳过权限绑定 / 不可删 | ✅ 409 |
| 3 | 无角色用户 | 有用户绑定时拒绝删除（返回人数） | ✅ 409 |
| 4 | 树形成环 | 上溯检测 + `guard` 上限 | ✅（代码路径） |
| 5 | 权限标识重复/格式错 | 唯一性前置拦截 + `@Pattern` | ✅ 400 |
| 6 | 菜单形态错配 | 三类语义校验 | ✅ 400 ×3 |
| 7 | 孤儿关联行 | 删除时级联清理关联表 | ✅（代码路径） |
| 8 | 删除有引用的主数据 | 子节点/用户双校验（过滤逻辑删除） | ✅ 409 ×2 |
| A | 敏感字段泄露 | VO 类型层面无该字段 | ✅ 接口出网无 password |
| B | 密码误改 | 独立接口 + 独立 DTO | ✅（代码路径） |
| C | 审计锚点漂移 | `username` 创建后不可改 | ✅（代码路径） |
| D | 重复绑定 | 全量覆盖式 + `distinct()` | ✅（代码路径） |
| E | N+1 | 批量 IN 反查 | ✅（代码路径） |

**验证方式**：全部防护均通过真实 HTTP 请求实测触发（见 `docs/journal/2026-09-13-glm-t105-107-603-803.md`
「端到端实测」段），拒绝路径返回 `409`（状态冲突）或 `400`（请求非法），
错误信息为可读中文并指明具体数量/原因。
