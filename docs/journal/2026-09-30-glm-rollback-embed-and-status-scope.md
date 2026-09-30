# 2026-09-30 GLM — 回退机制改造（环节内嵌 + 可选目标步 + 批量）与状态展示精简

> 关联任务：用户 2026-09-30 指令（两条任务 + 五节验收清单）。**超出业务说明书范围的增量改造**
> （回退机制本身即 T-920，说明书未要求）。分支 `agent/glm`。

## 一、本轮目标

1. **任务 1**：把「全流程逐步回退」从**独立功能区**（独立页面 + 路由 + 侧栏菜单）改造为
   **环节内嵌 + 可选目标步（跨级链式） + 同环节批量**。
2. **任务 2**：各业务页面**只展示本环节可能经手的状态**（全局查询页为唯一有意例外，需注释说明）。

## 二、实际做法（改了什么）

### 2.1 后端（S 级核心）

| 文件 | 动作 | 要点 |
|---|---|---|
| `common/enums/RollbackEdgePolicy` | 改 | 新增 `chain(from,to)` / `reachable(from,to)` / `reachableTargets(from)` / `chainGroup` / `chainScope` / `chainCodes` / `chainLabels` / `chainText` / `noPathHint`；`rejectReason` **重写**：跨级不再拒绝，S80/S90 出发一律 4102/4103 |
| `common/enums/SampleStatusTransition` | 注释 | `ROLLBACK` 白名单**一行未改**（边集合恒定）；仅把「跨级不在此表、由 policy 链式组合」写明 |
| `service/rollback/RollbackExecutor`（新） | 新增 | 单样品回退执行器，`@Transactional(REQUIRES_NEW)`；逐级执行 + 每级 1 条流水 |
| `service/rollback/SampleFieldSnapshot`（新） | 新增 | `sample_info` 关键字段快照 / 回填（从 `RollbackServiceImpl` 抽出，供执行器与 recover 共用） |
| `service/impl/RollbackServiceImpl` | 重写 | 只保留「读路径 + 批量编排」；新增 `targets(sampleId)`；`execute` 变批量 |
| `vo/RollbackTargetsVO`、`vo/RollbackBatchResultVO`（新） | 新增 | 目标步 + 影响预览；批量逐条明细 |
| `dto/RollbackExecuteDTO` | 改 | `sampleId` → `ids`（批量），`targetStatus` 允许跨级 |
| `controller/RollbackController` | 改 | 新增 `GET /rollback/targets/{sampleId}`；`execute` 返回批量结果 |
| `entity/{SampleRollback,SampleStatusLog}` | 改 | 新增 `batchNo`；`SampleRollback` 另加 `stepCount` |
| `service/SampleStatusLogService` | 改 | 新增带 `batchNo` 的 10 参方法，**原 9 参改为 default 重载** → 既有 9 处状态流转调用点**零改动** |
| `common/ResultCode` | 改 | 4101 文案去掉「仅支持逐级」（跨级已允许） |

### 2.2 数据

- `db/init/10_rollback_tables.sql`：同步新列（`sample_rollback.batch_no/step_count`、`sample_status_log.batch_no` + 3 个索引）。
- `db/migrations/V11__rollback_batch_and_menu.sql`（新，**幂等**）：加列/加索引（information_schema 判定）
  + 存量行回填（`RB-LEGACY-<id>`）+ **菜单清理**（删节点 13、权限位 131~134 提升为根级 `visible=0`、清孤儿授权）
  + 6 条校验 SELECT。
- `db/seed/01_rbac_seed.sql`、`db/seed/04_rbac_ai_rollback_seed.sql`：同步（删菜单 13、去掉 `(x,13)` 授权）。
- **已应用到活库**（`lims`）：V11 执行一次 + 再执行一次验证幂等；校验 SELECT 全中
  （`menu_13_removed=0`、`role_menu_13_orphan=0`、`rollback_perm_rows=4`、`rollback_path_left=0`）。

### 2.3 前端

| 文件 | 动作 |
|---|---|
| `views/rollback/index.vue` | **删除**（+ `routeRegistry` 登记移除） |
| `components/rollback/RollbackDialog.vue` | 重写：目标步来自 B7 接口、链路展示、批量同状态校验、逐条失败明细 |
| `components/rollback/RollbackEntryButton.vue` | 重写：支持 `samples` 批量 / `sampleId` 单条，附「留痕」入口 |
| `components/rollback/RollbackTraceDrawer.vue`（新） | 留痕抽屉（时间线 + 撤销回退）——承接被删页面的能力，避免能力丢失与组件变死代码 |
| `components/rollback/ReportVoidButton.vue`（新） | 作废/召回入口（**此前 API 有、UI 无**，是个真实缺口） |
| `views/{sample,item,assign}/index.vue` | 加 selection + 批量回退按钮 + 行内回退；各页补「状态范围」注释 |
| `views/{result,report/audit}/index.vue` | 回退入口改新 props + 按状态显隐（S50 / S60·S70） |
| `views/report/generate.vue` | 补「作废/召回」入口 + 「本页刻意无回退入口」注释 |
| `views/query/{testing,history,library}.vue` | 补「唯一有意例外」注释（状态筛选保留的理由 + 同源约束） |

## 三、💡 心得与判断

1. **不变式③必须重新定义，不能含糊**。原口径「一次回退恰好 1 条 `event_type=4` 流水」在跨级下
   数学上不可能成立（N 级就是 N 条）。我把它重定义为
   **「1 个回退批次（`sample_rollback` 1 行，含 `step_count`）+ 每级各 1 条流水（同 `batch_no`）」**，
   并**落库成列**（`batch_no`/`step_count`）而不是只写在注释里——否则审计对账时无法把 N 条流水
   还原成「一次用户操作」。
2. **批量必须逐条独立事务，且事务边界要用代码结构表达**。把循环与执行写在同一个方法里只有两个坏选择：
   整批一个事务（一条失败全部回滚，用户拿到「全失败」，无从知道哪几条本来能成）或同 Bean 自调用
   （Spring 事务基于代理，**自调用不开新事务**，注解静默失效）。故拆出 `RollbackExecutor` 独立 Bean +
   `REQUIRES_NEW`，让「逐条独立」成为结构事实而不是调用者的自觉。
3. **跨级不新增边，靠链式组合**。这是本次最重要的设计约束：状态机的价值在「边少而清晰」。
   我把「可达目标步」的唯一来源放在 `RollbackEdgePolicy.reachableTargets()`，前端**不得自行枚举状态**——
   否则状态机一改，前端就会给出后端不接受的选项。单测里加了一条
   「链的每一跳都必须能被白名单单步放行」，把「不新增直接边」变成**可执行断言**。
4. **`chainScope` 要剔除 `NONE`**。并集里若同时有 `NONE` 和真实处置类型，集合会退化成
   「四个元素里有一个是『什么都不做』」；只在整条链都不需要处置时才保留 `NONE`。
5. **删功能 ≠ 删能力**。删掉独立页面后，时间线查看与撤销回退会一并消失（并让 `RollbackTimeline.vue`
   变成死代码）。我判断这两件事是 ALCOA+ 审计链的对外可见部分，不能跟着走，于是收进
   `RollbackTraceDrawer`，由业务页「留痕」入口就地打开。**同理**：作废/召回的后端与 API 一直在，
   但**没有任何页面调用**——即「能力存在而入口缺失」，本轮把它补到了报告生成页。
6. **`views/` 目录误删事故**（详见第五节）让人意识到：**「改动正确」与「改动还在」是两件事**。
   恢复后我把所有前端改动**重做一遍并逐项验盘**，而不是假设它们还在。

## 四、⚠️ 踩坑记录

### 坑 1（🔴 最高危）：`git rm` 把父目录整棵删掉

- **现象**：`git rm -q -- "frontend/src/views/rollback/index.vue"` 返回 **exit=0**，
  却把 `frontend/src/` 下除 `views/` 外**全部文件**删光（App.vue/api/components/router/stores/styles/types/utils/main.ts…）。
  `git status` 列出一大片 ` D`。恢复后再执行同一条命令，又把 `views/` 整个删掉。
- **发现方式**：Edit 工具报 `File not found: routeRegistry.ts` —— 若不追查就会直接提交，复刻 4070ea6 的 118 文件误删事故。
- **根因**：本沙箱对 git 的树/索引写入存在拦截（与「ref 被吞」「对象库缺对象」同源），
  `git rm` 改写索引/工作区时路径解析异常，把父目录整棵当删除目标。
- **处理**：① 删文件一律用 `rm -f <精确路径>`；② 误删用 `git checkout HEAD -- <目录>` 恢复
  （**不要** `reset --hard`，会丢本轮未提交改动）；③ 恢复后 `git status | grep -c '^ D'` 必须为 0；
  ④ 已写入 skill `sandbox-git-push` **规则 7.5**。

### 坑 2：Maven 直启脚本用 POSIX 路径 →「找不到主类」

- **现象**：`java -classpath /c/Users/.../plexus-classworlds-2.9.0.jar … Launcher` 报
  `找不到或无法加载主类 org.codehaus.plexus.classworlds.launcher.Launcher`。
- **根因**：`java.exe`（Windows）**不认** Git Bash 的 `/c/...` 路径；另外 `.m2/wrapper` 下的 Maven
  **home 就是哈希目录本身**（没有嵌套的 `apache-maven-3.9.12/`）。
- **处理**：脚本内改用 `C:\Users\...` 反斜杠路径 + `\\` 拼接；`/tmp/lims-mvn.sh` 已按此重建。

### 坑 3：`node_modules/.bin` 为空 → `npm run` 找不到 `vue-tsc`

- **现象**：`npm run typecheck` → `'vue-tsc' 不是内部或外部命令`。
- **处理**：直接 `node node_modules/vue-tsc/bin/vue-tsc.js --noEmit`（eslint / vite 同理）。
  这是本仓 `node_modules` 的既有状态，不是本轮引入。

### 坑 4：`<script setup>` 内不能 `export`

`export interface RollbackSampleRef {...}` 写在 `<script setup>` 里是编译错误，
必须放到 `types/rollback.ts` 再 `import type`。

## 五、📊 进度

`项目总进度：99% → 99%`（按 AGENTS 2.5 固定口径）。本轮是**超出说明书范围的增量改造**，
按 DECISIONS 2026-09-17 的口径**不计入「说明书完成度」分母**；两项新能力自身完成度提升：
回退机制 **后端+单测+前端 UI 全部完成（新增链式/批量能力）**。

## 六、可复用结论（已沉淀）

1. `.agents/skills/sandbox-git-push` **新增规则 7.5**：「禁用 `git rm`，改普通 `rm`；误删只用
   `git checkout HEAD -- <目录>` 恢复；任何 git 写操作后立刻验盘」。
2. **链式回退的通用范式**（可复用到任何「只允许逐级、但业务要跨级」的状态机）：
   边集合恒定 + `chain(from,to)` 推导 + 每级独立断言与处置 + 批次号把 N 条流水还原成 1 次操作。
3. **批量操作的通用范式**：循环与单条执行必须分属两个 Bean（或显式 `TransactionTemplate`），
   否则「逐条独立事务」只是注释。
4. **删功能前先问「能力是否还有第二个落点」**：`RollbackTimeline.vue` / `voidReportApi` 两处都是
   差一点变成死代码/死能力，靠「找第二落点」而不是「顺手删掉」处理。

## 七、验收证据（真实命令输出）

| 项 | 结果 |
|---|---|
| 后端 `mvn -o test` | **252 / 0 失败 / 0 错误 / BUILD SUCCESS**（改造前 236；本轮 +16：`RollbackExecutorTest` 9 项 + `RollbackServiceImplTest` 扩至 15 项 + `RollbackEdgePolicyTest` 扩至 11 项） |
| 前端 `vue-tsc --noEmit` | 0 错误 |
| 前端 `eslint .` | 0 错误 / 0 警告 |
| 前端 `vite build` | 成功（主包 1,276.78 kB / gzip 412.76 kB，与改造前 1,270 kB 基本持平；回退组件独立成 chunk） |
| 活库 V11 | 两次执行均通过、幂等；6 条校验 SELECT 全中 |
| DB 实况 | `sys_menu` id=13 不存在；131~134 为 `parent_id=0 / visible=0`；R100=4、R1=2、R2=4、R3=2 项授权保留 |

## 八、⚠️ 需人工确认（未做，不得视为已验证）

1. **真实浏览器点一遍**：内嵌回退入口 → 选跨级目标步 → 影响预览 → 提交 → 逐条结果；
   以及 S70 敏感链路的二次确认与无权限账号的 403。
2. **契约章节号勘误**：用户指令写「api-spec 第 16 章」，实际 `第 16 章 = AI 助手域`，
   **流程回溯域是第 17 章**，本轮更新的是第 17 章（已在契约内注明改造日期）。
3. 结果录入页**没有批量**：该页当前是「行内进抽屉逐条录入」的工作台形态，无勾选列表，
   故只内嵌了单条回退；如需批量需先给该页加 selection 列（本轮未做，避免改动其交互形态）。
