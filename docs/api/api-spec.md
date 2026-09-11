# LIMS 接口契约（api-spec.md）

> 所有权：Copilot。其他人只读，禁止自创接口格式。
> 最后更新：2026-09-11 GLM（T-301 样品域契约落地）
> 变更记录：2026-09-11 新增第 3 章样品登记域（/api/sample/*），原「待落地域」顺延为第 4 章。

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

> 实现状态：契约与实现均已落地（T-002 契约 / T-102 实现，2026-09-10）。
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

## 2. 监抽任务域 `/api/task`（T-201）

> 实现状态：前后端均已落地（2026-09-10）。实体 `supervise_task`（db/init/03_task_tables.sql）。
> 前端对接文件：`frontend/src/api/task.ts`（已核对，与本契约一致）。

### 2.1 字段模型（SuperviseTask，camelCase）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | number | 更新必填 | 主键，新建不填 |
| taskNo | string | ✅ | 任务编号，全局唯一，≤50 |
| taskName | string | ✅ | 任务名称，≤200 |
| taskNature | string | ✅ | 字典：监督抽检/委托抽样/委托送样 |
| taskSource | string | | 任务来源（下达单位） |
| regionLevel | string | | 字典：省级/市级/区级 |
| leader | string | | 负责人 |
| batchNo | string | | 批次 |
| receiveDate / issueDate / completeDate | string(yyyy-MM-dd) | | 接受/下达/完成日期 |
| priority | string | | 任务等级 |
| positiveRateRequirement | string | | 阳性率要求 |
| samplingStage | string | | 字典：生产/流通/餐饮 |
| testScope | string | | 检测项目范围，≤500 |
| status | string | | 字典：草稿/进行中/已完成/已中止；新建缺省=草稿 |
| remark | string | | 备注，≤500 |
| createdBy / createdAt / updatedAt | string | 只读 | 审计字段（后端自动填充） |

### 2.2 分页查询

`GET /api/task/page?pageNum=1&pageSize=10&taskNo=&taskName=&status=`　权限：`task:list`

- taskNo：前缀匹配；taskName：模糊匹配；status：精确匹配；均选填。按 id 倒序。
- 响应 data：分页结构（见 0.3），records 元素为 2.1 字段模型。

### 2.3 详情

`GET /api/task/{id}`　权限：`task:list`
响应 data：2.1 字段模型。失败：`code=400` 任务不存在或已删除。

### 2.4 新建

`POST /api/task`　权限：`task:add`
请求 body：2.1 字段模型（不含 id/审计字段）。校验失败 `code=400`（msg 为首个字段错误）。
失败：`code=400` 任务编号已存在。响应 data：创建后的完整对象（含 id/审计字段/缺省 status=草稿）。

### 2.5 更新

`PUT /api/task`　权限：`task:edit`
请求 body：同 2.4 但 **id 必填**。status 缺省时保留原值。
失败：`code=400` id 为空 / 任务不存在 / 任务编号与他条重复。响应 data：`null`。

### 2.6 删除（逻辑删除）

`DELETE /api/task/{id}`　权限：`task:remove`
响应 data：`null`。失败：`code=400` 任务不存在或已删除。

---

## 3. 样品登记域 `/api/sample`（T-301）

> 实现状态：契约与实现均已落地（2026-09-11 GLM）。实体 `sample_info` / `sample_import_batch`
> （db/init/05_sample_tables.sql；样品表取名 `sample_info` 而非 `sample` —— `SAMPLE` 为 SQL 关键字，
> 与 MyBatis-Plus 分页插件 JSqlParser 冲突，见 DECISIONS 2026-09-11）。
> 前端对接文件：`frontend/src/api/sample.ts`。
> 权限标识：`sample:import` / `sample:confirm` / `sample:query`（与 seed `sys_menu`、
> AGENTS.md 8.2 严格一致；此前交接留言中的 `sample:list` 表述作废，以 `sample:query` 为准）。
> 状态机：样品状态 `status` 为 TINYINT code（S10=10 … S90=90，见 AGENTS 7.2），
> 流转经 `common/enums/SampleStatusTransition` 白名单，导入落库即 S10。

### 3.0 采样单 Excel 格式（导入文件约定）

- 后缀 `.xls` / `.xlsx`；读取 `sheet1`。
- **第 1 行 A1**：自定义文件标记，同名标记重复导入将整文件拒绝（防重复导入）。
- **第 2 行**：列头（下表 22 列，通过列名匹配，列序与下列一致）。
- **数据自第 3 行起**；末行 A 列写「以下空白」表示结束，该行与空白行均被忽略。
- 列头（顺序）：样品编号 / 样品名称 / 受检单位 / 抽样地址 / 收款人 / 费用 / 样品数量 /
  项目名称 / 日期 / 备注 / 采样者 / 生产单位 / 抽样基数 / 样品状态 / 规格型号 / 商标 /
  样品等级 / 原编号或生产日期 / 检验类别 / 要求完成日期 / 任务编号 / 任务批号。
- 导入模板：`frontend/public/templates/sample_import_template.xlsx`（前端「下载导入模板」按钮）。

### 3.1 字段模型（Sample，camelCase）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | number | 更新必填 | 主键，新建/导入不填 |
| sampleNo | string | ✅ | 样品编号，全局唯一，≤50 |
| sampleName | string | ✅ | 样品名称，≤255 |
| clientName | string | | 受检单位，≤255 |
| samplingAddress | string | | 抽样地址，≤255 |
| payee | string | | 收款人，≤50 |
| fee | number | | 费用 |
| sampleQuantity | string | | 样品数量（文本，如 3kg），≤50 |
| projectName | string | | 项目名称（如 市级例行），≤100 |
| samplingDate | string(yyyy-MM-dd) | | 采样日期（Excel「日期」列，支持 2026.9.12 等写法） |
| remark | string | | 备注，≤500 |
| sampler | string | | 采样者，≤50 |
| manufacturer | string | | 生产单位，≤255 |
| samplingBase | string | | 抽样基数，≤50 |
| sampleState | string | | 样品状态（如 鲜活），≤50 |
| spec | string | | 规格型号，≤100 |
| brand | string | | 商标，≤100 |
| grade | string | | 样品等级，≤50 |
| originalNo | string | | 原编号或生产日期，≤100 |
| inspectType | string | | 检验类别（如 监督抽检），≤50 |
| requireCompleteDate | string(yyyy-MM-dd) | | 要求完成日期 |
| taskNo | string | ✅（导入） | 关联监抽任务编号 `supervise_task.task_no`，≤50 |
| taskBatchNo | string | | 任务批号，≤50 |
| status | number | 只读（导入固定 10） | 状态机 code：10/20/…/90 |
| statusLabel | string | 只读 | 状态中文名（后端派生输出） |
| confirmedBy / confirmedAt | string | 只读 | 登记确认人/时间（S10→S20 写入） |
| createdBy / createdAt / updatedBy / updatedAt | string | 只读 | 审计字段（后端自动填充） |

### 3.2 采样单导入

`POST /api/sample/import`　权限：`sample:import`　`Content-Type: multipart/form-data`

- 表单字段：`file`（.xls/.xlsx，单文件 ≤10MB）。
- 行为：合法行落库（状态 S10「已登记」）；**错误行不入库且不整批回滚**，逐条返回「行号 + 原因」。
- 响应 data：

```json
{
  "total": 10,
  "successCount": 8,
  "failCount": 2,
  "errors": [
    { "rowNum": 5, "sampleNo": "JK(2026)-SA-002", "message": "样品名称不能为空" },
    { "rowNum": 7, "sampleNo": null, "message": "任务编号不存在: RW-XX-999" }
  ]
}
```

失败（HTTP 200 + body.code）：
| code | 场景 |
|---|---|
| 400 | 未选择文件 / 格式非 .xls/.xlsx / 该文件标记已导入（防重复导入） / 解析失败 |

逐行校验规则：样品编号必填且不重复（文件内 + 库内查重）；样品名称必填；任务编号必填且须存在于 `supervise_task`；各列长度不超限；日期须可解析（yyyy.M.d / yyyy-MM-dd / yyyy/M/d / yyyy年M月d日）；费用须为数值。

### 3.3 分页查询

`GET /api/sample/page?pageNum=1&pageSize=10&sampleNo=&sampleName=&taskNo=&status=`　权限：`sample:query`

- sampleNo：前缀匹配；sampleName：模糊匹配；taskNo：精确匹配；status：精确匹配（状态 code）；均选填。按 id 倒序。
- 响应 data：分页结构（见 0.3），records 元素为 3.1 字段模型。

### 3.4 详情

`GET /api/sample/{id}`　权限：`sample:query`
响应 data：3.1 字段模型。失败：`code=400` 样品不存在或已删除。

### 3.5 登记信息维护

`PUT /api/sample`　权限：`sample:import`
请求 body：3.1 字段模型（id 必填）。
限制：**仅 status=10「已登记」可改**，否则 `code=400`；sampleNo 须保持全局唯一。
失败：`code=400` id 为空 / 样品不存在 / 样品编号已存在 / 非 S10 状态。
响应 data：`null`。

### 3.6 登记确认（S10→S20，批量）

`POST /api/sample/confirm`　权限：`sample:confirm`

请求：

```json
{ "ids": [1, 2, 3] }
```

- 逐条经状态机白名单校验（仅 S10→S20 合法），并用乐观条件 UPDATE（`WHERE id=? AND status=旧值`）
  防并发双击跳态；确认后写入 `confirmedBy` / `confirmedAt`。
- 响应 data：`{ "confirmedCount": 3 }`。
- 失败：`code=400` ids 为空 / 样品不存在 / 状态非法（如已确认或已进入分解）/ 并发状态已变更。

---

## 4. 项目分解域 `/api/item`（T-401）

> 实现状态：契约与实现已落地（2026-09-11 GLM）。
> 实体 `sample_item`（db/init/06_item_tables.sql），承载**样品 × 检测单项**的分解结果。
> 业务依据：业务说明书「五、检验业务流程之二：样品检验明细项目分解（自动套用项目库）」——
> 系统按项目标准库**自动加载全部检测单项**，并允许在此基础上**增加、删减调整**，
> 「确认保存」后进入任务安排流程（S20→S30）。
> 权限标识：`item:decompose`（与 seed `sys_menu` id=41、AGENTS.md 8.2 严格一致）。
> 查询复用 `sample:query`（分解页需先查样品）。
> 状态机：进入本域要求样品为 **S20（登记确认）**；确认保存流转 **S20→S30（已分解）**，
> 经 `common/enums/SampleStatusTransition` 白名单校验。

### 4.0 套库匹配规则（自动加载）

**匹配键**：`sample_info.sample_name` = `product_lib.product_name`（精确匹配，TRIM 后比较）。

- 命中唯一产品 → 取其全部 `product_lib_item`（按 `item_order` 升序）作为分解初稿。
- 命中 0 条 → `matched=false`，返回空清单 + 提示「未找到产品标准库，请人工添加检测单项」，
  **不报错**（允许人工建单）。
- 命中多条 → 取 `id` 最小的一条并返回 `matchedLibId` 与 `candidates` 列表供前端提示。
- **仅生成初稿、不落库**：套库结果由前端展示、用户可增删调整后，随「保存分解」一次性落库。
  （说明书要求分解结果可调整，故初稿与最终结果分离。）

**字段下沉（快照）**：套库时把标准库的 `unit` / `basis_code` / `methods` / `std_value` /
`judge_type` / `is_reference` / `lower_limit` / `method_note` **复制**进 `sample_item`，
并记 `lib_item_id` 与 `source_type=1`。
理由：国标会更新，报告须固化当时的判定依据；且人工调整后的值必须独立于标准库保存。
→ **T-601 判定引擎只读 `sample_item`，不回溯 `product_lib_item`。**

### 4.1 字段模型（SampleItem，camelCase）

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| id | number | 更新必填 | 主键，新增不填 |
| sampleId | number | ✅ | 样品ID |
| sampleNo | string | 只读 | 样品编号（后端冗余写入） |
| itemOrder | number | ✅ | 项次（样品内排序，从 1 起，唯一） |
| itemName | string | ✅ | 检验项目名称，≤255 |
| libItemId | number | | 来源标准库明细ID；人工新增为 null |
| unit | string | | 单位，≤50 |
| basisCode | string | | 判定依据标准号，≤100 |
| methods | string | | 检验方法（多个以 `#` 分隔），≤500 |
| stdValue | string | | 标准值（限量值文本），≤50 |
| judgeType | number | ✅ | 判定类型：1=限量比较 2=不得检出/不得使用 3=文本/感官人工 |
| isReference | number | ✅ | 是否参考性限量：0=否 1=是 |
| lowerLimit | string | | 最低检出限，≤20 |
| methodNote | string | | 方法备注，≤100 |
| sourceType | number | ✅ | 来源：1=标准库自动套用 2=人工新增 |
| remark | string | | 备注，≤255 |
| createdBy / createdAt / updatedBy / updatedAt | string | 只读 | 审计字段 |

### 4.2 套库预览（自动加载检测单项，不落库）

`GET /api/item/match/{sampleId}`　权限：`item:decompose`

- 行为：按 `sample_info.sample_name` 匹配 `product_lib.product_name`，返回标准库明细初稿。
- 响应 data：

```json
{
  "sampleId": 1,
  "sampleName": "花鲢",
  "matched": true,
  "matchedLibId": 7,
  "matchedProductName": "花鲢",
  "candidates": [],
  "items": [
    {
      "itemOrder": 1,
      "itemName": "铅（以Pb计）",
      "libItemId": 120,
      "unit": "mg/kg",
      "basisCode": "GB 2762-2017",
      "methods": "GB 5009.12#GB 5009.268",
      "stdValue": "0.5",
      "judgeType": 1,
      "isReference": 0,
      "lowerLimit": "0.02",
      "methodNote": null
    }
  ]
}
```

- `matched=false` 时 `items` 为空数组，`candidates` 为空；前端提示人工添加。
- 匹配到多条时 `candidates` 返回 `[{ "libId": 7, "productName": "花鲢" }, ...]`（含全部候选）。

### 4.3 查询样品分解结果

`GET /api/item/list/{sampleId}`　权限：`item:decompose` 或 `sample:query`

- 行为：返回该样品**已保存**的分解明细（`deleted=0`，按 `itemOrder` 升序）。
- 响应 data：`{ "sampleId": 1, "sampleNo": "JK(2023)-SA-001", "status": 20, "items": [SampleItem...] }`
- 样品不存在 → `code=400`。

### 4.4 保存分解（覆盖式，S20→S30 由 4.5 触发）

`PUT /api/item/save`　权限：`item:decompose`

- 请求体：

```json
{
  "sampleId": 1,
  "items": [ { "itemOrder": 1, "itemName": "铅（以Pb计）", "libItemId": 120, "unit": "mg/kg",
               "basisCode": "GB 2762-2017", "methods": "GB 5009.12", "stdValue": "0.5",
               "judgeType": 1, "isReference": 0, "lowerLimit": "0.02", "methodNote": null,
               "sourceType": 1, "remark": null } ]
}
```

- 行为：**覆盖式保存**——先逻辑删除该样品已有明细，再按 `items` 重建（全量替换，避免增量同步歧义）。
- 校验：
  - 样品必须存在且状态为 **S20**；否则 `code=400`。
  - `items` 不能为空（至少 1 个检测单项）。
  - `itemOrder` 在样品内唯一；后端不自动重排，重复即 `code=400`。
  - `itemName` 非空。
  - `judgeType` 必须 ∈ {1,2,3}；`isReference` ∈ {0,1}；`sourceType` ∈ {1,2}。
- 响应 data：`{ "sampleId": 1, "itemCount": 12 }`。
- 事务：`@Transactional(rollbackFor = Exception.class)`。

### 4.5 分解确认（S20→S30）

`POST /api/item/confirm`　权限：`item:decompose`

- 请求体：`{ "sampleId": 1 }`
- 行为：
  1. 校验样品存在且状态为 S20；
  2. 校验该样品**已有分解明细**（`deleted=0` 且 count ≥ 1），否则 `code=400`
     「请先完成项目分解再确认」；
  3. 经 `SampleStatusTransition.assertTransition(S20, S30)` 校验；
  4. 乐观条件 UPDATE（`WHERE id=? AND status=20`）落 S30，防并发双击跳态。
- 响应 data：`{ "sampleId": 1, "status": 30, "statusLabel": "已分解" }`。
- 失败：`code=400` 样品不存在 / 状态非 S20 / 无分解明细 / 并发状态已变更。

### 4.6 分页查询待分解样品

`GET /api/item/pending`　权限：`item:decompose`

- 查询参数：`current`（默认 1）、`size`（默认 10）、`sampleNo`（模糊）、`sampleName`（模糊）。
- 行为：分页返回 **status = S20** 的样品（供分解页列表）。
- 响应 data：`PageResult`，结构同 3.3（`records` / `total` / `current` / `size`），
  `records` 为 `Sample` 字段子集（含 `itemCount`：已保存明细数，供前端显示进度）。

---

## 5. 检验任务安排域 `/api/assign`（T-501）

> 阶段五：检验项目分解完成后，把每个**检测单项**指派给有资格的检验员（S30 → S40）。
> 规则来源：AGENTS 7.4。**指派粒度为「检测单项」**（不同单项方法不同 → 可能是不同检验员）。

### 5.0 自动分配规则

1. **分类规则（优先）**：样品编号含 `NA` → 农残共享检验员；含 `XA` → 畜残；含 `SA` → 水产。
   - 代码 → 工号的映射以 **`user_method`** 表（`method` 列为 `NA`/`XA`/`SA`）为**数据源**；
     该表缺行时回退到 AGENTS 7.4 约定的默认工号 `njna000` / `njxa000` / `njsa000`。
   - 编号同时含多个代码时按固定顺序 `NA` → `XA` → `SA` 取**先命中者**。
   - 命中则 `assignType = 1`。
2. **方法资质规则**：分类规则未命中时，取该单项的 `methods`（可能以 `#` 分隔多个方法标准号），
   匹配 `tester_method.method_no` 且 `qual_status = 1` 的记录；命中即指派该检验员 → `assignType = 2`。
3. **兜底（fail-loud）**：两条规则均未命中 → **不指派**，`assignStatus = 0`（待人工指派），
   `testerNo` 保持 NULL。**禁止默认指派任意检验员**。
4. **人工改派**：`assignType = 3`。仅允许指派**有资质者**——候选列表接口只返回具备资质者；
   无资质者不出现在候选中（若某单项无任何有资质者，候选为空，需先补录资质）。

> ⚠️ **数据现状（2026-09-11 实测，务必知悉）**：`tester_method` 当前 **0 行**，且旧表 `user_item`
> 引用的 `nj009`/`nj010` **不在 `sys_user`** 中。即「方法资质规则」当前**无数据可用**，
> 实际会自动落到 `assignStatus=0`（待人工指派）。这是**数据缺口而非实现缺陷**——
> 由业务方补录资质（`/api/base/tester-method`）后规则自然生效。
> 分类规则有数据（`user_method` 3 行 + `sys_user` 3 个共享检验员），可正常命中。

### 5.1 字段模型（`sample_item` 分配字段增量，camelCase）

| 字段 | 类型 | 说明 |
|---|---|---|
| `assignStatus` | int | 0=待指派 1=已指派 |
| `assignType` | int | 0=未指派 1=分类规则 2=方法资质 3=人工改派 |
| `testerNo` | string\|null | 检验员工号（`sys_user.username`） |
| `testerName` | string\|null | 检验员姓名（出网冗余字段，取自 `sys_user.nickname`，便于列表展示） |
| `assignedAt` | datetime\|null | 指派时间 |
| `assignedBy` | string\|null | 指派操作人（工号） |

样品层增量：`assignTotal`（单项总数）、`assignDone`（已指派数）。

### 5.2 分页查询待安排样品

- `GET /api/assign/pending`
- 权限：`assign:confirm`
- 参数：`current`（默认 1）、`size`（默认 10，≤500）、`sampleNo`（模糊）、`sampleName`（模糊）
- 仅返回 **status = S30（已分解）** 的样品；无分解明细的样品不出现在列表中。
- 响应 `data`：`{ records, total, current, size }`，`records[]` 字段：
  `id / sampleNo / sampleName / clientName / taskNo / inspectType / samplingDate / status / statusLabel / assignTotal / assignDone`

### 5.3 查询样品安排明细

- `GET /api/assign/detail/{sampleId}`
- 权限：`assign:confirm`
- 响应 `data`：
  - `sampleId / sampleNo / sampleName / status / statusLabel / assignTotal / assignDone / inputPermitted`
    （`inputPermitted` = 是否已全部指派，前端据此决定「确认安排」是否可点）
  - `items[]`：`{ id, itemOrder, itemName, methods, unit, stdValue, judgeType, isReference,
    assignStatus, assignType, testerNo, testerName, assignedAt }`
  - `candidates[]`：`{ testerNo, testerName, matchedMethodNo, source }`
    （**仅含对当前样品任一单项具备资质者**；`source` = `METHOD`(方法资质) / `CATEGORY`(分类规则)）

### 5.4 执行自动分配（可重跑）

- `POST /api/assign/auto`，body `{ "sampleId": 1 }`
- 权限：`assign:confirm`
- 语义：对该样品**所有单项**重跑 5.0 的规则；**已人工改派（`assignType=3`）的单项不覆盖**。
- 幂等：重复执行结果一致。
- 响应 `data`：`{ sampleId, total, assigned, pending, details: [{ itemOrder, itemName, assignStatus, assignType, testerNo, testerName, reason }] }`
  （`reason` 为未指派原因，如 `未命中分类规则且无方法资质`）

### 5.5 人工改派

- `POST /api/assign/reassign`，body `{ "itemId": 12, "testerNo": "njsa000" }`
- 权限：`assign:reassign`
- 校验：① 单项存在；② 样品处于 **S30**；③ `testerNo` 存在于 `sys_user` 且 `status=1`；
  ④ **该检验员对该单项具备资质**（分类规则命中的代码一致，或 `tester_method` 命中其方法之一）；
  否则返回 `code=400` 并给出原因（**仅列出有资质者**，AGENTS 7.4）。
- 成功后 `assignType = 3`、`assignStatus = 1`。
- 响应 `data`：`{ itemId, testerNo, testerName, assignType, assignStatus }`

### 5.6 安排确认（S30 → S40）

- `POST /api/assign/confirm`，body `{ "sampleId": 1 }`
- 权限：`assign:confirm`
- 校验：① 样品流转 `S30 → S40`（状态机白名单）；② **全部单项必须已指派**，否则 `code=400`
  并提示「仍有 N 个检测单项待指派」。
- 并发：乐观条件 UPDATE（`WHERE id=? AND status=30`），`updated==0` → 「样品状态已变更，请刷新后重试」。
- 响应 `data`：`{ sampleId, status, statusLabel }`

---

## 6. 待落地域（占位，按七阶段顺序补充）

| 域 | 前缀 | 对应任务 | 状态 |
|---|---|---|---|
| 认证 | /api/auth/* | T-102 | ✅（第 1 章） |
| 监抽任务 | /api/task/* | T-201 | ✅（第 2 章） |
| 系统管理（用户/角色/菜单/部门） | /api/sys/* | T-101 后续 | ⬜ |
| 基础数据（lib/basis/tester-method/customer） | /api/base/* | T-103 | ⬜ |
| 样品登记（Excel 导入） | /api/sample/* | T-301 | ✅（第 3 章） |
| 项目分解 | /api/item/* | T-401 | ✅（第 4 章） |
| 任务安排 | /api/assign/* | T-501 | ✅（第 5 章） |
| 结果录入（自动判定） | /api/result/* | T-601 | ⬜ |
| 报告审核签发/生成 | /api/report/* | T-701/T-702 | ⬜ |
| 查询与省平台上报 | /api/query/* /api/export/* | T-801/T-802 | ⬜ |
