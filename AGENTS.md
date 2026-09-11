# LIMS Project - Agents Guide（多 Agent 协作开发版）

**项目名称**：食品质量检验测试中心 实验室信息管理系统（LIMS）
**技术架构**：前后端分离
- 前端 `frontend/`：Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router 4
- 后端 `backend/`：Spring Boot 3 + Java 17 + Maven + MyBatis-Plus
- 数据库：MySQL 8（InnoDB / utf8mb4），开发账号 root / 11111111
- 权限模型：RBAC（用户 / 角色 / 权限 / 菜单 / 部门 / 数据权限）
- 参考数据：`lims.sql` 为旧系统导出数据，仅作迁移参考（见 0.1）

**协作模式**：本仓库由三个 AI Agent 协作开发，各自提示词存于 `prompts/` 目录：

| Agent | 工作分支 | 定位 | 模型 |
|---|---|---|---|
| WorkBuddy（GLM） | `agent/glm` | **S 级 + A 级**：架构 + 后端核心（含判定引擎/报告引擎）+ 前端主要负责人 + Skill 提炼 | GLM5.3（额度不足降级 HY4 preview，S/A 任务排队不降能力） |
| Copilot CLI | `agent/copilot` | **契约终审 + 规则裁决 + diff 审查**（不承接 S/A 实现任务） | auto 模型 |
| 豆包 | `agent/doubao` | B 级：文档 / 数据 / 状态维护 / 杂务 | 豆包 2.1 Turbo |

三个 Agent 必须同时遵守本文件与 `STATUS.md`、`TODO.md`、`HANDOFF.md`、`DECISIONS.md` 的约束。**开工前必读这四个状态文件，收工后必更新 HANDOFF.md。**

---

## 0. 三个核心优化决策（本次修订重点，任何 Agent 不得违反）

### 0.1 新旧数据库并存原则【数据规范决策】

- **新表规范**：本项目所有**新建表**必须遵循第 6.1 章新表规范：`id BIGINT AUTO_INCREMENT` 主键、snake_case 字段名、`created_by/created_at/updated_by/updated_at` 审计四字段、`deleted` 逻辑删除字段。
- **旧库定位**：`lims.sql` 是旧系统导出的**参考数据**，其风格（int 主键、驼峰列名如 `customerId`/`basisName`、无审计字段、无逻辑删除）**禁止在新代码中复用**。
- **迁移方式**：旧数据（basisname 约 1150 条判定依据、customer 9 家、lib 项目标准库、dept）由 `db/migrations/V1__import_legacy_data.sql` 清洗迁移至新表结构，脚本必须包含字段映射与去重逻辑；迁移完成后旧表废弃。
- **Entity 过渡规则**：迁移完成前如确需读取旧表做数据比对，Entity 必须用 `@TableField` 显式映射列名并加注释标记 `// [LEGACY] 旧表过渡，禁止新代码依赖`，比对完成后删除。

### 0.2 业务模块与状态机先行【需求建模决策】

- 系统业务主线固定为**七阶段**（见第 7 章），任务拆分、接口命名、权限标识、合并顺序全部按业务阶段对齐，**禁止跨阶段跳做**（例：未完成 T-4xx 分解相关任务，不得开始 T-5xx 安排相关任务）。
- 样品状态机 **S10 → S90**（见 7.2）是全系统唯一状态流转标准：后端以枚举 + 流转白名单实现，前端按状态渲染操作按钮，任何 Agent 不得私增状态或跳态流转。
- 结果自动判定规则（见 7.3）只能由 GLM 实现于后端，检验员不可手改单项结论，前端不可自算结论；判定口径的歧义解释权归 Copilot（见 2.3）。

### 0.3 分支策略升级【协作决策】

- 在实训"三分支"基础上升级为 **三分支 + Agent 专属分支**：
  - `main`：受保护。**每周实训结束由组长操作 develop → main 固化一次**，作为每周考核版本，禁止任何人直接提交；
  - `develop`：集成分支，仅接受三个 Agent 分支的合并；
  - `agent/copilot`、`agent/glm`、`agent/doubao`：三个 Agent 的长期工作分支，各自只在本人分支提交。
- 合并路径唯一：`agent/xxx → develop → main`。**任何 Agent 禁止直接 commit/push 到 main 和 develop。**
- Commit 规范沿用 Conventional Commits：`feat:` / `fix:` / `refactor:` / `chore:` / `docs:` / `test:` / `data:`（豆包数据脚本专用）。

---

## 1. 总体原则

- **不偏离技术栈**：前端只用 Vue3 生态，后端只用 Spring Boot 3 生态。引入新依赖必须写入 DECISIONS.md 说明用途并获确认。
- **安全第一**：权限校验、数据校验、业务安全逻辑必须在后端实现；前端仅做体验层展示控制。密码 BCrypt 加密，VO 禁止出现 password/salt。
- **契约先行**：任何需要前后端联调的功能，GLM 必须先把接口定义写入 `docs/api/api-spec.md`（路径/方法/请求/响应/权限标识），**Copilot 终审**；全员以终审后的契约为唯一依据，禁止自创接口格式。
- **改表必同步**：SQL 迁移脚本 → Entity → Mapper → DTO/VO → 前端接口与页面，五件套缺一不可。
- **可构建原则**：交付必须保证 `mvn clean compile` 与 `npm run build && npx vue-tsc --noEmit` 通过，禁止无法编译的示意代码。
- **小步提交**：一个可编译、可验证的小功能 = 一次提交，禁止攒大招。
- **敏感信息零提交**：不提交密码、密钥、Token、`application-dev.yml` 真实配置（已在 .gitignore 排除）。
- **状态同步**：开工读 STATUS/TODO/HANDOFF/DECISIONS，收工写 HANDOFF（格式见 prompts/doubao.md）。

## 2. 多 Agent 协作规则

### 2.1 文件所有权（越界即冲突，严禁违反）

| 目录 / 文件 | WorkBuddy/GLM | Copilot | 豆包 |
|---|---|---|---|
| `backend/` 核心（config/security/RBAC/业务主流程/判定引擎/报告引擎） | ✅ **独有** | ❌（仅 review/裁决） | ❌ |
| `backend/` 简单 CRUD（customer/dept/basis/菜单页对应模块） | ✅ 可写（新建文件为主） | ✅ review | ❌ |
| `frontend/` | ✅ **主要所有者（含架构与疑难页面）** | 仅 review | 仅纯静态页/文案 |
| `docs/api/api-spec.md` | ✅ 起草/写入 | ✅ **终审（契约权威）** | 只读 |
| `db/migrations/`、`db/seed/` | 只读/设计评审 | 审核 | ✅ 主要维护 |
| `docs/`（除 api-spec）、`*.md` 治理文件 | ✅ 主要维护（技术+结构） | 决策类/裁决记录 | 部分维护 |
| `.agents/skills/` | ✅ 创建/审核 | ✅ 审核 | 只读使用 |
| 公共文件（pom.xml、package.json、路由配置、vite.config） | ✅ **独有** | 提 TODO 申请 | ❌ |

规则：需要改公共文件或他人领地 → 在 TODO.md 提任务给对应 Owner，Owner 统一修改。

### 2.2 开工六步（三个 Agent 每次会话必须执行）

1. `git checkout agent/<自己> && git pull origin agent/<自己>`
2. **【前置检索】** 动手前先查资料——见 2.5「工作纪律三件套」第 3 条。顺序：`.agents/skills/` → `docs/knowledge/` → `docs/journal/` → 必要时上网搜（GitHub / 官方文档）。
3. 依次阅读：`STATUS.md` → `TODO.md` → `HANDOFF.md` → `DECISIONS.md`（+ 本轮相关 `docs/api/api-spec.md`）
4. 检查 STATUS.md 中他人占用文件，确认无冲突后声明本轮自己要改的文件
5. 从 TODO.md 领取**自己级别**的任务并标记 🔵进行中(名字)
6. 开发 → 自查 → **写日记 + 更新进度**（见 2.5）→ 提交 → 更新 HANDOFF.md 并 @ 下一人

### 2.3 任务分级与角色定位（2026-09-11 角色调整后）

- **S 级**（架构/状态机/判定引擎/报告引擎/业务主流程后端）：**GLM**（仍保留代码终审权）。
- **A 级**（页面/常规 CRUD/接口对接/前端整体）：**GLM**（与 Copilot 同级）。
- **B 级**（文档/数据/模板/状态维护）：豆包。
- **Copilot 的不可替代工作（三类，其余不再承接）**：
  1. **api-spec 契约**：对 GLM 起草的接口定义做终审（字段命名/路径风格/响应结构/权限标识一致性），契约冲突时以 Copilot 结论为准；
  2. **规则裁决**：判定口径（7.3 六条规则）的边界情形解释、数据形态歧义裁定（如「不得检出」型与检出限的关系）、跨模块语义冲突裁决；
  3. **diff 审查**：合并进 develop 前的代码审查（是否遵循 AGENTS.md、是否安全隐患、是否破坏契约）。
- 分工边界：**GLM 实现，Copilot 把关**。Copilot 不承接 S/A 级实现类任务；GLM 不单方面改动已终审的契约。
- 降级：GLM5.3 额度不足 → 任务拆小，体力部分转豆包；切 HY4 preview 后只做纯 CRUD，S/A+ 任务排队；**严禁为省额度让低能力模型做 S 级任务**（返工成本 > 省下额度）。

### 2.4 冲突处理

- 冲突必须由涉及双方代码的 Owner 共同核对，禁止强推覆盖；
- 公共文件冲突一律以 GLM 版本为基准，他人重放自己的增量；契约（api-spec.md）冲突以 Copilot 终审版本为基准。

### 2.5 工作纪律三件套（⚠️ 全员强制，2026-09-11 用户要求）

> 目的：本项目除了交付系统本身，还要交付**一套可复现的经验资产**（skill + 知识库），
> 使后人能以此为模板「一步到位」复现同类项目。因此每次工作都必须留下可复用的痕迹。

#### 第 1 件：工作日记（每次会话必须写）

- **位置**：`docs/journal/YYYY-MM-DD-<agent>-<主题>.md`（一天多次工作可多篇）
- **必须包含**：
  1. **本轮目标**（关联任务 ID）
  2. **实际做法**：改了什么文件、用了什么命令、关键代码片段
  3. **💡 心得与判断**：为什么这样做、当时排除了哪些方案、哪些判断被数据推翻了
  4. **⚠️ 踩坑记录**：现象 → 根因 → 处理（这部分最有价值，务必写全）
  5. **📊 进度**：见第 2 件，写清「本轮前 → 本轮后」
  6. **可复用结论**：本次有什么值得沉淀进 `.agents/skills/` 或 `docs/knowledge/` 的
- **不是流水账**：不写「我读了 X 文件」这类过程，只写有判断价值的内容。

#### 第 2 件：项目进度百分比（每次会话必须更新）

- 统一维护在 `STATUS.md` 的「进度评估」章节，并使用**固定口径**（避免各人各算）：

  | 权重 | 内容 |
  |---|---|
  | 55% | 业务主干 T-101 ~ T-802（按任务数加权，S 级权重 ×2、A 级 ×1.5、B 级 ×1） |
  | 15% | 前端完整度（动态路由、系统管理页、各业务页面联调） |
  | 10% | 数据基础（迁移完整度、种子数据、数据字典） |
  | 10% | 质量与测试（单测覆盖、质量门禁通过率） |
  | 10% | 工程化与可复现资产（skill / 知识库 / 治理完备度） |

- 每次工作结束时在日记与 STATUS 中同时写明：`项目总进度：X% → Y%`，并简述增量来源。

#### 第 3 件：动手前先检索（禁止无检索直接开写）

- **强制顺序**（自上而下，找到即止，但至少走完前两层）：
  1. `.agents/skills/` —— 有没有现成 skill 可直接套用/改造？
  2. `docs/knowledge/` —— 有没有已验证的选型、口径、模板、踩坑？
  3. `docs/journal/` —— 之前有没有人做过类似的事？当时怎么做的、踩了什么坑？
  4. 上网检索（GitHub 开源实现、官方文档、社区方案）——**当上述三层都无法回答时必做**；
     检索到的可复用方案须沉淀进 `docs/knowledge/`，可操作流程须转化为 skill。
- **日记中须留痕**：写明「查了什么、查到什么、是否采用、为什么不采用」。
- 反面案例：2026-09-11 曾因未核对暂存区就提交，把 118 个文件误删入库（见 HANDOFF 19:40 事故条目）。
  **提交前必须 `git status --short` 逐项核对。**

#### 第 4 件（延伸）：经验资产化

- 每完成一个**核心模块**或解决一个**非平凡问题**，必须产出至少一件资产：
  - 可操作流程 → `.agents/skills/<名称>/SKILL.md`
  - 选型/口径/调研结论 → `docs/knowledge/YYYY-MM-DD-<主题>.md`
- 目标：项目结束时，`skill 库 + 知识库` 足以让后人**仿照模板直接复现同类 LIMS**。
- 判定标准：后人只读资产、不读源码，能否理解「为什么这么做」并复制「怎么做」。

## 3. 目录结构约定

```
lims/
├── frontend/                 # Vue3 前端工程
│   ├── src/
│   │   ├── api/              # 接口请求封装 (Axios)
│   │   ├── views/            # 页面组件（按业务模块分子目录）
│   │   ├── components/       # 通用/业务组件
│   │   ├── stores/           # Pinia
│   │   ├── router/           # 路由（含动态路由生成）
│   │   ├── utils/  types/  layouts/  directives/
│   │   ├── App.vue  main.ts
│   ├── .env.development  .env.production
│   └── vite.config.ts  tsconfig.json  package.json
├── backend/                  # Spring Boot 3 后端工程
│   ├── src/main/java/com/lims/
│   │   ├── controller/  service/  mapper/  entity/
│   │   ├── dto/（含 JSR-303 校验）  vo/（脱敏）
│   │   ├── config/  security/  common/
│   │   └── LimsApplication.java
│   ├── src/main/resources/
│   │   ├── application.yml  application-dev.yml
│   │   └── mapper/           # MyBatis XML
│   └── pom.xml
├── db/
│   ├── init/                 # 建库建表脚本（新表规范）
│   ├── migrations/          # 增量迁移 V1__import_legacy_data.sql ...
│   └── seed/                 # 测试数据（豆包维护）
├── docs/
│   ├── api/api-spec.md       # 接口契约（GLM 起草 / Copilot 终审）
│   ├── knowledge/            # 网上搜集的最佳实践沉淀 + 判定口径定稿
│   ├── journal/              # 📔 工作日记（每次会话必写，见 2.5）
│   └── database-dictionary.md# 数据字典（豆包维护）
├── .agents/skills/           # 可复用技能库（做完项目拷走即复现）
│   ├── rbac-backend/SKILL.md
│   ├── mybatisplus-crud/SKILL.md
│   ├── vue3-crud-page/SKILL.md
│   └── excel-import/SKILL.md
├── prompts/                  # 三个 Agent 的提示词
├── AGENTS.md  README.md  .gitignore
├── STATUS.md  TODO.md  HANDOFF.md  DECISIONS.md
```

## 4. 后端开发规范

### 4.1 技术约束
- JDK 17+，Spring Boot 3.x，Maven 3.8+，MyBatis-Plus 3.5.x+。
- 认证：JWT（access_token + refresh_token）。
- 授权：Spring Security + `@PreAuthorize("hasAuthority('权限标识')")`。
- 统一响应：`{ "code": 0, "msg": "success", "data": {} }`；code=0 成功；401 未认证；403 无权限；400 参数错误；500 系统异常。

### 4.2 分层职责
| 层 | 职责 | 禁止 |
|---|---|---|
| Controller | 参数校验、调用 Service、返回统一响应 | 写业务逻辑、直接操作数据库 |
| Service | 业务编排、事务管理（@Transactional） | 暴露 Mapper 细节 |
| Mapper | 继承 BaseMapper | 字符串拼接 SQL |
| Entity | 表映射，MP 注解 | 业务方法 |
| DTO | 请求对象 + JSR-303 | 复杂业务逻辑 |
| VO | 响应对象，脱敏 | password/salt/secret |

### 4.3 MyBatis-Plus 规范
- 只用 `LambdaQueryWrapper/LambdaUpdateWrapper`；分页用 `Page<T>` + `PaginationInnerInterceptor`。
- 业务状态字段（如样品状态）在后端用**枚举**定义，禁止魔法数字；状态流转必须经 Service 层校验白名单。
- 自动判定等核心业务规则必须写**单元测试**（参考 db/seed 中豆包准备的测试数据集）。

## 5. 前端开发规范

- Vue 3 Composition API（`<script setup lang="ts">`），TS strict，**禁止 any**。
- 业务状态入 Pinia；跨组件通信优先 Props/Emits。
- 按钮级权限用 `v-permission="'权限标识'"`；**前端权限只做显隐，安全由后端兜底**。
- 路由守卫：无 Token → /login；无菜单权限 → /403；登录后根据 `/api/auth/me` 动态生成路由。
- 禁止硬编码 API 地址（用 `import.meta.env.VITE_API_BASE_URL`）；禁止 Token 放 URL；生产代码禁 console.log。

## 6. 数据库规范

### 6.1 新表规范（所有新建表必须满足）
- InnoDB，utf8mb4 / utf8mb4_general_ci；表名字段名全小写下划线。
- 主键 `id BIGINT NOT NULL AUTO_INCREMENT`；时间用 DATETIME；枚举用 TINYINT + 字典，不用 ENUM。
- 审计四字段：`created_by VARCHAR(64)`、`created_at DATETIME`、`updated_by VARCHAR(64)`、`updated_at DATETIME`；逻辑删除 `deleted TINYINT DEFAULT 0`。
- 高频查询字段、外键字段必须建索引。

### 6.2 新旧并存与迁移（对应优化决策 0.1）
- 新功能一律建新表（6.1 规范）；旧表数据只经 `db/migrations/` 脚本单向迁移进新表，迁移脚本须含：字段映射、去重规则、迁移前后条数校验 SELECT。
- Entity 禁止驼峰映射新表；旧表过渡映射必须标 `// [LEGACY]`。

### 6.3 修改流程
增量迁移 SQL → Entity → Mapper/DTO/VO → 前端接口与页面 → commit message 注明影响范围。

## 7. 业务建模（全员必读，对应业务说明书）

### 7.1 业务七阶段（任务与接口命名按此对齐）
基础数据准备 → 监抽任务管理 → 样品登记（采样单导入）→ 检验项目分解（自动套库）→ 检验任务安排 → 检验数据录入（自动判定）→ 报告审核签发 → 报告生成打印 →（查询 / 省平台上报）。

### 7.2 样品状态机（唯一标准，S/A/B 级全员引用，禁止私改）

| 状态 | 编码 | 触发 | 下一允许操作 |
|---|---|---|---|
| 已登记 | S10 | 采样单导入成功 | 登记维护/确认 |
| 登记确认 | S20 | 登记员确认 | 项目分解 |
| 已分解 | S30 | 分解确认保存 | 任务安排 |
| 已安排 | S40 | 安排确认保存 | 检验数据录入 |
| 检验中 | S50 | 检验员首次录入 | 继续录入 |
| 检验完成 | S60 | 全部项目录齐 | 提交审核 |
| 已审核 | S70 | 领导审核通过 | 签发 |
| 已签发 | S80 | 领导签发 | 报告生成 |
| 已出报告 | S90 | 报告生成完成 | 上报导出/归档 |
| （退回） | — | 审核退回 | S50 并通知检验员 |

### 7.3 结果自动判定规则（GLM 实现于后端，Copilot 裁决口径）
1. 标准值 `≤X` 型：检验值 ≤ X → 合格，> X → 不合格；
2. `不得检出/不得使用` 型：未检出 → 合格，检出 → 不合格；
3. 文本描述型（感官项目）：检验员选合格/不合格；
4. 检验值低于最低检出限 → 按"未检出"处理；
5. 带 `*` 的参考性限量参与计算但结论中标注"参考"；
6. 任一单项不合格 → 样品整体不合格；单项结论自动生成，不可手改。

### 7.4 任务安排自动分配规则
- 样品编号含 `NA` → njna000（农）；含 `XA` → njxa000（畜）；含 `SA` → njsa000（水）；
- 其余项目按"检验方法—检验员资质"自动匹配可执行人，允许人工改派（仅列出有资质者）。

## 8. RBAC 权限设计

### 8.1 预置角色
R100 综合管理（审核签发/权限管理/全部查询）；R1 样品登记员；R2 任务管理员（分解/安排/资质维护）；R3 检验员（含 R3-NA/XA/SA 共享检验员账号 njna000/njxa000/njsa000）。

### 8.2 权限标识（resource:action，与业务模块对齐，供接口与 v-permission 共用）
`sys:user:*`、`sys:role:*`、`sys:menu:*`、`sys:dept:*`、`base:lib:*`、`base:basis:*`、`base:tester-method:*`、`base:customer:*`、`task:*`、`sample:import`、`sample:confirm`、`sample:query`、`item:decompose`、`assign:confirm`、`assign:reassign`、`result:entry`、`result:export-excel`、`report:audit`、`report:sign`、`report:generate`、`report:print`、`query:testing`、`query:history`、`export:province`、`log:view`。

### 8.3 鉴权流程
`POST /api/auth/login` 返回 JWT → `GET /api/auth/me` 返回用户+角色+权限标识集合+菜单树 → 接口按权限标识鉴权 → 数据权限：普通用户仅本部门及下属部门，R100 全部。

### 8.4 安全红线
- ❌ 前端传 role/permission 参数决定放行；❌ 前端判断管理员控制数据可见性；❌ 前端隐藏即安全。
- ✅ 后端必须二次校验；菜单/按钮显隐只是体验层。

## 9. 构建与质量门禁

**前端**：`cd frontend && npm install && npm run build && npx vue-tsc --noEmit && npm run lint`
**后端**：`cd backend && mvn clean compile -q && mvn clean package -DskipTests`
任何 Agent 交付前必须通过本人端门禁；合入 develop 前必须通过 Copilot diff 审查（审查范围见 2.3）。

## 10. Git 工作流（对应优化决策 0.3）

- 每日流程：`git pull origin 自己的分支` → 开发 → 提交 → `git checkout develop && git merge agent/自己`（先 pull origin develop 解冲突）→ `git push origin develop` → 回到自己分支。
- 每周固化：组长执行 `git checkout main && git merge develop && git push origin main`。
- 禁止：force push main/develop、reset --hard 强推、提交 node_modules/target/.env/IDE 配置。

## 11. LIMS 领域术语对照

| 中文 | 英文 | 说明 |
|---|---|---|
| 样品 | Sample | 被检测对象（含采样单信息） |
| 监抽任务 | SuperviseTask | 监督抽检任务 |
| 检验项目/检测单项 | TestItem | 具体检测指标（如铅、氯霉素） |
| 项目标准库 | ProductLib | 产品应检项目+判定标准+方法库 |
| 判定依据 | Basis | 判定标准文件（GB xxxx 等） |
| 检验方法 | Method | 检测方法标准 |
| 检验任务 | TestTask | 项目×检验员的一次工作安排 |
| 检验数据 | TestResult | 录入结果+单项结论 |
| 报告 | Report | CMA / CMA-CATL 检验报告 |
| 客户 | Customer | 受检单位 |
| 共享检验员 | SharedTester | njna000 / njxa000 / njsa000 |
| 省平台上报 | ProvinceExport | 检验结果汇总 Excel 导出 |

## 12. 行为约束清单

> ⚠️ **全员强制前置**：以下每个角色的约束都**叠加第 2.5 节工作纪律三件套**——
> ① 每次会话写 `docs/journal/` 日记（含心得/踩坑/可复用结论）；
> ② 每次会话更新 `STATUS.md` 进度百分比（`X% → Y%`，按固定口径）；
> ③ 动手前先检索 `.agents/skills/` → `docs/knowledge/` → `docs/journal/` → 上网，并在日记留痕。
> 违反者视为未完成本轮工作。

**GLM（S 级 + A 级，与 Copilot 同级）**：起草 api-spec 契约（Copilot 终审）；核心业务只写完整可编译文件；每完成一个核心模块提炼 `.agents/skills/<模块>/SKILL.md`（含触发场景/前置/步骤/完整代码模板/踩坑）；定期执行 TODO 中"侦察"任务，把 GitHub 优秀实践（RuoYi-Vue-Plus、vue-element-plus-admin 等）沉淀进 `docs/knowledge/` 并转化为 skill；作为代码 Owner 维护 frontend/ 与 backend/ 主流程；**提交前必须 `git status --short` 逐项核对暂存区**（2026-09-11 误删 118 文件事故的根因）。

**Copilot（契约 + 裁决 + 审查，不接实现任务）**：对 `docs/api/api-spec.md` 做终审（字段命名/路径/响应结构/权限标识一致性）；对判定口径与跨模块语义歧义做最终裁决并写入 DECISIONS.md；合并进 develop 前对 diff 做审查（是否遵循 AGENTS.md、是否有安全隐患、是否破坏契约），不通过须在 TODO 退回并写明原因；可协助 GLM 起草契约，但不自行发起接口设计。

**豆包**：维护 STATUS/HANDOFF/TODO 状态（最高优先，每 2 小时或收工一次）；以代码为准修正文档不一致；数据脚本独立小提交（`data:` 前缀）；遇到设计/决策问题不自行解决，提 TODO 给 GLM。
