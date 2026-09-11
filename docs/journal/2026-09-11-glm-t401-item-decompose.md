# 2026-09-11 GLM — T-401 项目分解（自动套库）全链路

> 任务：T-401（S 级）｜分支 `agent/glm`｜起 `5ab522d` → 止见 HANDOFF
> 本轮附办：T-903 数据补齐、T-901 治理落地（工作纪律三件套）、D5 裁决请求

## 一、目标

阶段四「检验项目分解（自动套库）」全链路落地：样品 S20 状态下按样品名匹配产品标准库，
生成检测单项初稿 → 人工可增删调整 → 覆盖式保存 → 确认流转 S20→S30。

顺带处理交接留言指派的两件事：**T-903**（`product_lib.product_name/category` 全 NULL 补齐）
与 **D5**（judge_type 一次性订正脚本，含 before/after 统计）。

## 二、做法（可复用的推进顺序）

1. **先验数据前提，再动手写代码**。这是本轮最大的收获——不要照着裁决文档直接写脚本，
   先跑 SQL 确认「裁决所假设的数据分布」是否真实存在。
2. 读 `docs/knowledge/2026-09-11-judge-engine-whitelist.md`（Copilot 裁决定稿）作为唯一口径依据。
3. 建表 → 契约（api-spec 第 4 章）→ 实体/Mapper → DTO/VO → Service/Impl → Controller → 单测 → 前端 api → 前端页面 → 路由菜单。
4. 每层做完立即编译（后端 `mvn compile`、前端 `vue-tsc`），不攒到最后一起 debug。

## 三、关键设计决策（本轮沉淀）

### 3.1 套库预览不落库
`GET /item/match/{sampleId}` 只返回初稿，不写 `sample_item`。
**理由**：业务允许分解员在初稿上人工增删调整，「自动套库的初稿」与「最终确认的分解结果」是两个概念。
若套库即落库，人工调整就得先删再写，语义混乱且污染审计字段。

### 3.2 保存为覆盖式（先逻辑删除，再全量重建）
`PUT /item/save` 不提供「增量 patch」语义。
**理由**：分解页是一个整体工作台，前端已保证项次连续唯一（1..N）；覆盖式让后端逻辑最简，
也避免「前端删了一行但后端没收到」的中间态。

### 3.3 标准库字段快照下沉至 sample_item
`unit / basis_code / methods / std_value / judge_type / is_reference / lower_limit / method_note`
**八个字段从 `product_lib_item` 复制进 `sample_item`**，而不是只存 `lib_item_id` 外键。
**两条理由**：
1. 国标会更新，报告必须固化「检验当时的判定依据」，事后查报告不能被新标准追溯篡改；
2. 分解员人工调整后的值必须独立于标准库——若只存外键，人工改动就无处安放。

**推论（写给 T-601）**：判定引擎只读 `sample_item`，**禁止回溯 `product_lib_item`**。
这条已写入契约与实体注释。

### 3.4 项次唯一性用三列唯一键
`uk_sample_item_order (sample_id, item_order, deleted)`。
带上 `deleted` 是为了兼容逻辑删除——否则删掉第 2 项后再新增第 2 项会撞唯一键。
前端在删除行后调用 `resequence()` 重排为连续 1..N，保证后端约束永远满足。

## 四、🔴 本轮最重要的发现：D5 的前提不成立

交接留言说「D5 裁决的 judge_type 一次性订正脚本」——隐含前提是
`product_lib_item.std_value` 里存在 `不得检出` / `≤数值` 等非纯数值形态，需要按白名单归一化。

**实测结论：前提不成立。** 证据链：

| 探针 | 结果 |
|---|---|
| `product_lib_item` 共 3728 行，`std_value` 形态诊断 | **100% 纯数值**，`≤数值`/`不得检出`/`不得使用`/`--` 各 0 行 |
| 数据源旧 `lib` 表 `std_value` 形态 | **同样 100% 纯数值** → 不是迁移漏迁 |
| `prj_detail` 中 `不得检出` 出现次数 | 220 条，但只涉及 5 个项目名（全为兽残：硝基呋喃类代谢物/诺氟沙星类/恩诺沙星/孔雀石绿/氯霉素） |
| 上述 5 项在 `product_lib_item` 中的匹配数 | **0** → 两表不同源 |

**根因判断**：旧 `lib` 库只覆盖农残 `GB 2763-2021`（`basis_code` 唯一值仅此一本标准），
兽残类项目从未进入该库。`prj_detail` 里的兽残数据来自旧系统另一条录入路径，不构成「迁移遗漏」。

**处理**：V3 脚本照常产出，但定位从「数据回填」改为「**可重跑的口径校验器/归一化器**」，
当前实测为零变更（no-op），并输出 5 段证据供裁决。已写成
`docs/knowledge/2026-09-11-adjudication-request-d5.md` 交 Copilot 裁决（全项目 2 次配额中的第 1 次）。

**方法论沉淀**：**「执行前先证伪前提」**。裁决文档给的是「规则」，但规则作用的「数据」可能根本不存在。
如果直接照写脚本，V3 会是一个永远输出 0 变更的脚本，而所有人以为 D5 已完成——
这是比脚本报错危险得多的静默失败。

## 五、T-903 顺利落地

旧 `product` 表（92 行，`id` / `prd_category` / `libName`）与 `product_lib`（92 行，`product_name` 全 NULL）
按 `product.id = product_lib.product_code` **一对一完美匹配**。
V2 脚本补齐 `product_name` 0→92、`category` 0→92，残留空值 0。脚本幂等（只填空值），可反复重跑。

## 六、踩坑记录

| 坑 | 表现 | 解法 |
|---|---|---|
| V2 脚本 SQL 双别名 | `COUNT(*) AS still_null AS product_lib_残留空值` 语法错 | 改为单别名 |
| V3 脚本第 3 段返回空 | LEFT JOIN 子查询写法不生效 | 改为两个标量子查询嵌套 `IN (SELECT ...)` |
| `vue-tsc` 报 `DefaultRow` 不能赋给 `ItemPendingRow` | el-table 插槽 row 类型为 `DefaultRow` | 模板内 `row as ItemPendingRow` 断言 |
| `vue-tsc` 报 TS6133 未使用函数 | 我预留的 `judgeTypeLabel/Tag` 最终没用上 | 直接删除（TS strict 不允许死代码） |
| 前端 node_modules 未安装 | `npx vue-tsc` 报不是内部命令 | `npm install`（52s，35 包） |
| el-table 插槽内联箭头函数含类型标注 | `@change="(v: boolean \| string \| number) => ..."` 触发格式告警 | `eslint --fix` 收尾 |

## 七、质量门禁（AGENTS 第 9 章硬要求，全部通过）

- 后端 `mvn test`：**23 项全过**（状态机 7 + 导入监听器 2 + 本轮新增 ItemServiceImpl 14）。
- 前端 `npm run build`（= `vue-tsc --noEmit && vite build`）：✅ 通过。
- 前端 `npm run lint`：✅ **0 错误 0 警告**（修完 136 个格式告警后）。

## 八、单测写法沉淀（Service 层 mock 的两个必备技巧）

写 MyBatis-Plus `ServiceImpl` 的单测时有两个必踩的坑：

1. **实体元数据未初始化** → `TableInfoHelper.initTableInfo(assistant, XxxEntity.class)`
   必须在 `@BeforeAll` 里对**每个实体**都调一次，否则 MP 无法生成 lambda 列名。
2. **`baseMapper` 是 protected 字段** → 无法直接赋值。
   用反射：`ServiceImpl.class.getDeclaredField("baseMapper")` + `setAccessible(true)` + `f.set(service, mapper)`。

另：`any(Wrapper.class)` 会触发 unchecked 警告（泛型擦除），**改 `any()` 并删掉 `Wrapper` import** 即可。

## 九、治理落地：工作纪律三件套（用户强制要求）

写入 `AGENTS.md` 新增 2.5 节，全员强制：
1. **工作日记**（本文件即是）——`docs/journal/YYYY-MM-DD-<agent>-<主题>.md`；
2. **进度百分比**——固定权重口径：业务主干 55% + 前端 15% + 数据 10% + 质量 10% + 工程化 10%；
3. **动手前先检索**——`.agents/skills/` → `docs/knowledge/` → `docs/journal/` → 上网；
4. **经验资产化**——做完即沉淀。

配套：2.2「开工五步」→「**开工六步**」（前置检索插为第 2 步）；第 3 章目录加 `docs/journal/`。
第 12 章给 GLM 加红字：**提交前必须 `git status --short` 逐项核对暂存区**（误删事故的教训）。

## 十、进度

**业务主干 3/9 → 4/9**。按 2.5 节固定口径评估：约 **41%**（详见 STATUS.md）。

## 十一、可复用结论（供后续 Agent 直接取用）

1. **动手前先证伪前提**：裁决/需求文档假设的数据分布，必须先跑 SQL 验证。静默的零变更比报错更危险。
2. **快照下沉原则**：凡是「报告要固化」或「人工可改」的字段，从标准库复制进业务表，不要只存外键。
3. **MP ServiceImpl 单测**：`TableInfoHelper.initTableInfo` 初始化实体 + 反射注入 `baseMapper`，两个都不能少。
4. **前端 el-table 插槽类型**：`DefaultRow` 与业务行类型不兼容时，模板内 `as` 断言是成本最低的解法。
5. **前端质量门禁一键过**：`npm run build` 报错 → 修 → `npx eslint --fix <改动文件>` → `npm run lint` 归零。
