# LIMS 接口契约（api-spec.md）

> 所有权：Copilot。其他人只读，禁止自创接口格式。
> 最后更新：2026-09-10 Copilot（T-002 认证域终审落地）

---

## 0. 通用约定

### 0.1 基础 URL 与请求
- 后端统一前缀：`/api`（`server.servlet.context-path=/api`，与 vite 代理无 rewrite 对齐）。下文路径均含 `/api` 前缀。
- 请求/响应均为 `application/json; charset=UTF-8`。
- 鉴权：除标注"公开"的接口外，一律要求请求头 `Authorization: Bearer <accessToken>`。
- 时间格式：`yyyy-MM-dd HH:mm:ss`（GMT+8）。

### 0.2 统一响应

```json
{ "code": 0, "msg": "success", "data": {} }
```

| code | 含义 | 说明 |
|---|---|---|
| 0 | 成功 | data 为业务数据 |
| 400 | 参数错误 | JSR-303 校验失败等，msg 可直接展示 |
| 401 | 未认证/登录过期 | 前端清 token 跳 /login |
| 403 | 无权限 | 前端提示无权限 |
| 404 | 接口不存在 | |
| 500 | 系统异常 | 前端提示稍后重试 |
| 1000+ | 业务自定义 | 按模块分段，新增时必须登记在本文件对应域 |

- 安全层（未带/非法 token、权限不足）返回 **HTTP 401/403 + 上述响应体**；业务异常返回 HTTP 200 + body.code 区分。
- 前端只认 `body.code`（见 `frontend/src/utils/request.ts`），调用方无需关心 HTTP 层差异。

### 0.3 分页约定（列表接口统一）
- 请求参数：`pageNum`（从 1 起）、`pageSize`（默认 10，上限 500），外加各域查询条件。
- 响应 data：

```json
{
  "records": [],
  "total": 0,
  "current": 1,
  "size": 10
}
```

### 0.4 字段命名
JSON 字段一律 **camelCase**（终审结论，见 DECISIONS.md 2026-09-10）；数据库列 snake_case 由 MyBatis-Plus 自动映射，禁止出网 snake_case 字段。

---

## 1. 认证域 `/api/auth`

> 实现状态：契约已定稿（T-002）；接口实现在 T-102 落地。
> 角色与权限标识全集见 AGENTS.md 8.1/8.2。

### 1.1 登录（公开）

`POST /api/auth/login`

请求：

```json
{
  "username": "nj001",
  "password": "明文密码（内网传输，服务端 BCrypt 比对）"
}
```

响应 data：

```json
{
  "accessToken": "eyJhbGciOi...",
  "refreshToken": "eyJhbGciOi...",
  "expiresIn": 7200
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| accessToken | string | JWT，2 小时有效；请求头携带 |
| refreshToken | string | JWT，7 天有效；仅用于 1.2 换发 |
| expiresIn | number | accessToken 有效期（秒），前端可选使用 |

失败：`code=400` 用户名或密码错误（不区分具体原因，防枚举）；`code=401` 账号停用。

### 1.2 刷新令牌（公开）

`POST /api/auth/refresh`

请求：

```json
{ "refreshToken": "eyJhbGciOi..." }
```

响应 data：同 1.1（换发新 accessToken + refreshToken，旧 refreshToken 作废）。

失败：`code=401` refreshToken 非法/过期 → 前端跳登录。

### 1.3 当前登录用户信息

`GET /api/auth/me`

请求：无 body，需 accessToken。

响应 data：

```json
{
  "user": {
    "id": 1,
    "username": "nj001",
    "nickname": "张三",
    "deptId": 10,
    "deptName": "食品检验科",
    "roles": ["R100"]
  },
  "permissions": ["sys:user:list", "report:audit", "..."],
  "menus": [
    {
      "id": 1,
      "parentId": 0,
      "title": "样品登记",
      "path": "/sample/register",
      "icon": "document",
      "children": []
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| user.id | number | 用户主键 |
| user.username | string | 工号（预置账号见 AGENTS.md 8.1：nj001/nj002/nj003/njna000/njxa000/njsa000） |
| user.nickname | string | 姓名 |
| user.deptId / user.deptName | number / string | 所属部门（数据权限用） |
| user.roles | string[] | 角色编码集合（R100/R1/R2/R3） |
| permissions | string[] | 权限标识全集（resource:action，AGENTS.md 8.2），供 `v-permission` 使用 |
| menus | MenuNode[] | 菜单树，动态路由数据源；`parentId=0` 为根，`path/icon/children` 可空 |

> 前端对接说明（T-003 已按此实现，`src/api/auth.ts`）：`user.deptId` 与 `MenuNode.parentId` 为契约新增字段，前端类型可随后续动态路由任务补声明，运行时零影响。

### 1.4 退出登录

`POST /api/auth/logout`

请求：无 body，需 accessToken。
响应 data：`null`。
说明：无状态 JWT 服务端不做强制失效；本接口预留（审计日志/后续 token 黑名单）。前端退出以本地清 token 为准（T-003 已实现）。

---

## 2. 待落地域（占位，按七阶段顺序补充）

| 域 | 前缀 | 对应任务 | 状态 |
|---|---|---|---|
| 系统管理（用户/角色/菜单/部门） | /api/sys/* | T-101/T-102 | ⬜ |
| 基础数据（lib/basis/tester-method/customer） | /api/base/* | T-103 | ⬜ |
| 监抽任务 | /api/task/* | T-201 | ⬜ |
| 样品登记（Excel 导入） | /api/sample/* | T-301 | ⬜ |
| 项目分解 | /api/item/* | T-401 | ⬜ |
| 任务安排 | /api/assign/* | T-501 | ⬜ |
| 结果录入（自动判定） | /api/result/* | T-601 | ⬜ |
| 报告审核签发/生成 | /api/report/* | T-701/T-702 | ⬜ |
| 查询与省平台上报 | /api/query/* /api/export/* | T-801/T-802 | ⬜ |
