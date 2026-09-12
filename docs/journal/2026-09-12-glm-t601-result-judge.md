# T-601 检验结果录入 + 自动判定引擎（GLM，2026-09-12）

## 一、目标

落实 AGENTS 7.3 六条判定规则 + `docs/knowledge/2026-09-11-judge-engine-whitelist.md`（T-902 裁决定稿），
完成阶段六：检验员按检测单项录入结果 → 引擎自动判定单项结论 → 全录齐 S50→S60。

## 二、做法（顺序）

1. **开工六步**：读 STATUS/TODO/HANDOFF/DECISIONS；核查工作区与进程（昨日留言的 PID 11640 已不存在，
   8080/5173 无监听，无需杀进程）；读 T-501 代码作为风格模板。
2. **前置侦察（T-909）**：检索规则引擎选型（Drools/Easy Rules/LiteFlow/Aviator）、浮点比较陷阱、
   LIMS 数据完整性（ALCOA+ / 21 CFR Part 11），产出
   `docs/knowledge/2026-09-12-judge-engine-research.md`——**结论是不引入任何规则引擎**。
3. **引擎先行**（纯函数）：`service/judge/JudgeEngine` + `JudgeInput`/`JudgeOutcome`，
   闭集白名单矩阵 + `BigDecimal.compareTo` + 闭集外一律「待判定 + WARN」。
4. **数据层**：`db/init/07_result_tables.sql`（`sample_result`）+ `V4__add_sample_conclusion.sql`
   （`sample_info.conclusion`）+ 同步 `05_sample_tables.sql`。
5. **编排层**：`ResultServiceImpl`（读快照 → 调引擎 → 覆盖式 upsert → 流转状态 → 聚合整体结论）+ Controller。
6. **契约**：`docs/api/api-spec.md` 第 6 章（5 接口 + 判定矩阵表 + 字段模型）。
7. **前端**：`api/result.ts` + `views/result/index.vue`（实时判定预览 + 可编辑录入表）+ 路由/菜单。
8. **门禁 + 端到端**：`mvn test` 85/85、`npm run lint` 0 问题、`npm run build` ✅、
   本机 MySQL + 起服务跑 **45 项端到端断言全过**（含负向用例）、Edge headless 视觉回归。

## 三、关键设计决策（自裁，已落 DECISIONS）

| # | 决策 | 理由 |
|---|---|---|
| 1 | **不引入规则引擎/表达式引擎** | 判定语义固定且规模 < 10 条；`std_value` 是**数据**，若当表达式求值等于让标准库获得执行语义（注入/污染风险 + 不可审计）。规则必须是代码，判定依据才是数据 |
| 2 | **引擎与编排分离**（纯函数 vs Service） | 判定矩阵可用构造数据穷举覆盖；编排可用 Mock 验证。T-601 测试基线要求的「构造数据覆盖 jt2/jt3 全分支」因此变得廉价 |
| 3 | **原始值 / 派生值分层落库** | ALCOA+：`test_value` 是人录的原始值，`conclusion`/`judge_basis` 是引擎派生值，报告上每个结论都能回放到「哪条规则 + 哪个原始值」 |
| 4 | **判定依据参数不冗余存放** | `std_value`/`judge_type`/`lower_limit`/`is_reference` 一律取自 `sample_item` 快照，避免两处真相 |
| 5 | **一个单项恒一行结果（覆盖式 upsert）** | 唯一键 `(sample_item_id, deleted)`；重复保存为 UPDATE，修订由审计字段留痕，不产生第二行 |
| 6 | **整体结论只在保存/提交时重算回写；明细查询实时重算** | 列表用持久化值（便宜），明细/提交用实时聚合（权威）；避免 GET 产生写副作用 |
| 7 | **存在「待判定」不阻断提交** | 待判定成因可能是数据缺口（缺检出限/缺标准文本），阻断会让样品永久卡在 S50——「用流程阻断掩盖数据问题」；放行红线由 T-701 审核/签发把关。fail-loud 体现在**绝不自动判合格**而非阻断流程 |
| 8 | **全参考项样品整体 = 待判定** | 白名单 D3 补充：参考项不作放行依据 |
| 9 | **形态与判定类型矛盾 → 待判定 + WARN** | 如 jt2 配数值型标准值、jt1 配「不得检出」：口径矛盾时交人工，不猜 |

## 四、踩坑

1. **🔴 缺陷（自行发现并修复）：标准值「不得检出」被误判为闭集外**。
   常量复用错误——我把**标准值**形态的关键词也写成「未检出」（那是**检验值**的形态词），
   而标准值写的是「不得检出」。结果 jt2 全部分支退化为「待判定」。
   6 个单测同时报红，一眼定位。**教训：同一业务概念在「标准值侧 / 检验值侧」用词不同时，
   必须各自定义常量，不要复用字符串字面量。**
2. **🔴 工具坑：对同一文件并行发两条 Edit 会互相覆盖（丢失更新）**。
   我先改了 `JudgeEngine` 的两处（加常量 / 改用常量），两条 Edit 在同一消息内并行发出，
   其中一条基于旧内容写回，把另一条的改动抹掉 → 编译报「找不到符号 NOT_DETECTED_STD_TEXT」。
   **教训：同一文件的多次编辑必须串行；批量 Edit 前先想清楚依赖顺序。**
3. **vite dev server 只监听 IPv6 `[::1]`**：Edge/curl 用 `127.0.0.1:5173` 会「拒绝连接」，
   必须用 `localhost:5173`。
4. **vue-tsc：el-table 作用域插槽的 `row` 是 Element Plus 的 `DefaultRow`**，不是 `any`，
   直接把 `row` 传给强类型函数会报 TS2345。解法：加 `rowItem(row: unknown)` 收窄函数。
   （另：`computed` 里用 `.map().filter((x): x is T => ...)` 也会有类型谓词不兼容，
   改成显式 for 循环 + 类型化数组最省事。）
5. **分页为空可能不是 bug 而是权限正确**：用 R3 检验员账号看「任务安排」页永远为空
   （R3 无 `assign:confirm`）。视觉验证要挑对账号（管理员 nj001 / R1 / R2）。
6. **`--virtual-time-budget` 会压缩时间**，路由过渡可能停在半透明 enter 态，截图看起来「发灰」——
   判读截图时必须区分「过渡未完成」与「真的坏了」（T-906 已记录同类现象）。

## 五、可复用结论

- **判定引擎模板**：`枚举闭集 + 纯函数矩阵 + BigDecimal.compareTo + 默认待判定 + WARN 日志 + 单测用 ListAppender 断言日志`
  → 已沉淀为技能 `.agents/skills/judge-engine/SKILL.md`。
- **端到端联调脚本模式**：Python `urllib` + `ProxyHandler({})` 显式禁代理（本机 MITM 代理会拦 localhost）
  → 已沉淀为技能 `.agents/skills/sandbox-git-and-e2e/SKILL.md`。
- **沙箱 Maven 直启**：`mvn` 脚本解析 MAVEN_HOME 失败，必须直启 `plexus-classworlds`
  → 脚本与命令已写入上述技能。

## 六、门禁与实测数据

| 项 | 结果 |
|---|---|
| 后端单测 | **85/85 通过**（新增 49：判定引擎 34 + 服务编排 15） |
| 前端 lint | 0 错误 0 警告 |
| 前端 build | vue-tsc + vite ✅ |
| 端到端（本机 MySQL + 8080） | **45/45 断言通过**（登录/列表/明细/13 条判定矩阵预览/保存/提交/负向） |
| 视觉回归 | 结果录入页（新）、任务安排页（T-501 遗留项）截图确认 |
| fail-loud 证据 | `孔雀石绿`（不得检出 + 检出限为空 + 数值）落「待判定」，`judge_basis` 与 WARN 日志双留痕 |

实测落库样例（端到端跑出的真实数据，节选）：

```
item_name       test_value  conclusion  source  judge_basis
孔雀石绿         0.01        3 待判定      1 自动   标准值「不得检出」为不得检出型，但未维护最低检出限…
氯霉素           未检出       1 合格        1 自动   标准值「不得检出」，实测未检出，判定合格
菌落总数         色泽正常     1 合格        2 人工   文本/感官项目，由检验员人工判定为「合格」
铅（以Pb计）     0.01        1 合格        1 自动   实测 0.01 低于最低检出限 0.02，视同未检出，判定合格
镉（以Cd计）     0.20        2 不合格      1 自动   限量值 ≤0.1，实测 0.20 > 限量，判定不合格
恩诺沙星         0.05        2 不合格      1 自动   标准值「不得检出」，实测 0.05 ≥ 检出限 0.02，判定为检出 → 不合格
```

## 七、进度

- 业务主干 **6/9**（阶段六完成：T-101~T-104 / T-201 / T-301 / T-401 / T-501 / **T-601**）
- **总进度约 74%**（业务主干 55%×6/9≈36.7 + 前端 13 + 数据 8.5 + 质量 7.5 + 工程化 8.5）

## 八、下一步

- **T-701 审核/签发**（S，须补 `SampleStatusTransition`「审核退回 → S50」分支 + 同步 AGENTS 7.2 与单测）
- **T-702 CMA/CMA-CATL 报告合成**（S，消费 `sample_info.conclusion` + `sample_result.judge_basis`）
- T-801 查询（A，含动态路由）/ T-802 省平台上报（B，豆包）
