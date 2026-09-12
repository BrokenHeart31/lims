# 结果自动判定引擎：选型侦察与设计依据（2026-09-12，T-601 前置）

> 目的：为 T-601「结果录入 + 自动判定引擎」确定**实现形态**与**数据留痕形态**。
> 口径（判什么）已由 `docs/knowledge/2026-09-11-judge-engine-whitelist.md` 定稿；
> 本文只解决「怎么实现最稳、最可审计、最可测」。
> 结论：**不引入任何规则引擎/表达式引擎**，用「闭集白名单 + 纯函数 + 结构化留痕」实现。

## 1. 规则引擎选型侦察（结论：全部不采用）

| 方案 | 定位 | 为什么不选 |
|---|---|---|
| **Drools 8.x** | 企业级 BRMS，Rete/Phreak 模式匹配 | 规则规模 < 10 条、语义固定（六个分支），Rete 网络的空间/复杂度开销毫无回报；DRL 语法 + Kie 容器让「一条判定」的可读性与单测成本上升；实训项目无法承担其学习与运维成本 |
| **Easy Rules 4.x** | 轻量 POJO 规则库（Rule/Condition/Action） | 定位是「消除 if-else」，本质是把 if-else 挪进对象。本项目判定是**纯函数**（输入→结论），不需要规则注册、优先级、议程调度；引入只会多一层间接 |
| **LiteFlow** | 组件化编排 | 解决的是「流程编排/热更新」，本项目判定无流程、无需热更新（口径变更应走发版 + 单测，不允许线上热改判定规则） |
| **Aviator / QLExpress / SpEL** | 表达式求值引擎 | ⚠️ **最关键一条**：本项目 `std_value` 是**数据**（来自项目标准库），若把数据当表达式求值，等于让标准库内容获得**执行语义**——标准库一旦被污染（或字段被注入），判定结果不可预测且无法审计。**判定规则必须是代码，判定依据才是数据** |

**本项目的正确形态**：判定语义是**代码里的有限状态矩阵**（白名单闭集），`std_value` 只是被**解析**（解析失败 → 待判定 + 日志），从不被**执行**。收益：
- 确定性：同一输入恒得同一结论（可复现、可回归测试）；
- 可审计：结论可解释（引擎同时产出「判定依据说明」字符串）；
- fail-loud：闭集之外一律 `待判定` + WARN 日志，绝不静默判合格；
- 零依赖：不新增任何三方库（`frontend/src` 与前几轮同）。

> 参考（选型对照）：Drools / Easy Rules / LiteFlow / Aviator 的定位与适用边界见文末「参考来源」。

## 2. 数值比较的浮点陷阱（结论：BigDecimal 且必须 compareTo）

检验值判定是「数值比较」，这里有一个必踩的坑：

```java
// ❌ 反例：0.1 + 0.2 == 0.3 → false（IEEE 754 二进制表示误差）
if (testValue == lowerLimit) { ... }

// ❌ 反例：BigDecimal.equals 会比较 scale
new BigDecimal("2.00").equals(new BigDecimal("2.0"))  // false！
new BigDecimal("2.00").compareTo(new BigDecimal("2.0")) == 0  // true ✅
```

**定稿**：
1. 一切数值**解析为 `BigDecimal`**（`new BigDecimal(String)`，**禁止** `new BigDecimal(double)` 与 `BigDecimal.valueOf(double)` 之外的路径）；
2. 一切比较用 **`compareTo`**，禁止 `equals`、禁止 `==`/`<=` 直接作用于 `double`；
3. 解析失败（含空串、非数值文本）→ **不抛异常、不默判**，按 `待判定` 返回并在 `basis` 里写明原因（避免一条脏数据把整批录入打断）；
4. `lower_limit`（最低检出限）用同一套 `BigDecimal` 比较，不允许字符串比大小。

## 3. 数据留痕形态（结论：原始值不可变语义 + 结论为派生值，二者同时落库）

对照 GLP / FDA 21 CFR Part 11 / EU Annex 11 的 **ALCOA+** 原则（Attributable 可归属、Legible 清晰、Contemporaneous 同步、Original 原始、Accurate 准确、Complete/Consistent/Enduring/Available），LIMS 对检验结果有三条硬要求，直接决定本任务的数据模型：

1. **原始数据（raw data）与判定结论分层保存**：原始值是「检验员看到的数」（如 `0.10`、`未检出`），结论是「系统按规则算出来的」（如 `合格`）。二者都要落库，报告上的每一个字都能回放到「哪条规则 + 哪个原始值」。
2. **判定依据要版本化**：标准值/检出限必须在**检验时点**固化，不能因标准库更新而追溯改变历史报告 → 这正是 T-401 的「快照下沉」决策（`sample_item` 存 `std_value`/`judge_type`/`lower_limit`/`is_reference`）。**引擎只读 `sample_item`，永不回溯 `product_lib_item`**。
3. **可归属 + 可追溯**：录入必须有 `enteredBy`/`enteredAt`，改写必须有 `updatedBy`/`updatedAt`（审计四字段）；结论**不允许前端自算、不允许检验员手改**（唯一例外：`judge_type=3` 文本/感官型，由检验员选合格/不合格，属业务规定的人工判定）。

**落地取舍**：本项目不做「影子表 + SHA-256 指纹链」级别的内核审计（成本远超实训项目范围），但**把上述三条的结构位留足**：
- `sample_result.test_value`（原始值，可重录但每次覆盖都留 `updated_by/at`）
- `sample_result.conclusion` + `conclusion_source`（1=引擎自动 / 2=人工判定）+ `judge_basis`（判定依据说明，人可读）
- `sample_info.conclusion`（整体结论，规则 6 派生）

## 4. 「单项结论 vs 整体结论」的两级聚合

AGENTS 7.3 规则 6「任一单项不合格 → 样品整体不合格」是**聚合规则**，容易被误实现为「边录边改整体结论」。定稿：

- **单项结论**：录入即算（`POST /result/judge` 预览、`PUT /result/save` 落库），是引擎的直接输出；
- **整体结论**：**只在读/提交时重算**（由「该样品全部非参考项单项结论」聚合），不存中间态；
- 聚合矩阵（落实白名单 D3 裁决）：
  - 存在非参考项不合格 → **不合格**；
  - 无非参考项不合格，但存在非参考项待判定 → **待判定**；
  - 全部非参考项合格 且 至少有 1 个非参考项 → **合格**；
  - **没有任何非参考项**（全部是参考项）→ **待判定**（禁止自动判合格，交人工）；
  - 存在未录入的单项 → 整体 = **待判定**。

## 5. 参考来源

- Java 规则引擎选型（Drools / Easy Rules / LiteFlow / Aviator 定位与适用边界）：
  - https://blog.csdn.net/boonya/article/details/151709140
  - https://tencentcloud.csdn.net/69e9e3b20a2f6a37c5a284b9.html
- 浮点比较与 BigDecimal 陷阱：
  - https://howtodoinjava.com/java-examples/correctly-compare-float-double/
  - https://tpointtech.com/comparing-doubles-in-java
- LIMS 数据完整性（ALCOA+ / 21 CFR Part 11 / Annex 11、原始数据不可变、审计追踪常开）：
  - https://revollims.com/hplc-data-integrity-challenges-lims
  - https://sgsystemsglobal.com/glossary/lims-laboratory-information-management-system/
  - https://www.pharmagmp.in/lims-data-integrity-ensuring-trusted-laboratory-results/

> 本文为**方向性定稿**：后续任何判定相关实现（T-701 审核退回、T-702 报告合成）都必须复用本形态——
> 引擎是纯函数、结论是派生值、`std_value` 只解析不执行、闭集外一律待判定 + 日志。
