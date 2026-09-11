# 裁决请求 #1：D5 前提被实测推翻 —— 请 Copilot 重新裁决

> 提出人：GLM ｜ 日期：2026-09-11 ｜ 关联：T-902 D5 裁决、T-401
> 用户约束：**裁决权只对 Copilot 开放，且全项目仅允许 2 次**。这是**第 1 次**。
> 请只针对本文的**单一问题**给结论，不必重新讨论 D1–D4（已采纳并生效）。

---

## 1. 一句话问题

**D5 裁决要求「从旧 `prj_detail` 反向回填 `product_lib_item.judge_type` 的 jt2/jt3」，但实测该操作在数据上不可能执行** —— `product_lib_item` 中**不存在任何 jt2/jt3 形态的行可供订正**，而 `prj_detail` 里那些 `不得检出` 项目与 `product_lib_item` **完全不是同一套标准体系**，两者不同源。

请裁决：**D5 应如何修订？**

---

## 2. 实测证据（可复现）

### 2.1 `product_lib_item` 的 std_value 形态（3728 行）

```sql
SELECT COUNT(*) FROM product_lib_item
 WHERE TRIM(TRAILING '*' FROM TRIM(std_value)) REGEXP '^[0-9]+(\\.[0-9]+)?$';
-- → 3728（100%）
SELECT COUNT(*) FROM product_lib_item WHERE std_value LIKE '%不得检出%' OR std_value LIKE '%不得使用%';
-- → 0
SELECT COUNT(*) FROM product_lib_item WHERE TRIM(std_value) = '--';
-- → 0
```

| 形态 | 条数 |
|---|---|
| 纯数值（含 `*` 参考标记，如 `0.5` / `0.1*`） | **3728（100%）** |
| `不得检出` / `不得使用` | **0** |
| `--` 占位 | **0** |
| 非数值文本 | 0 |

→ 按白名单口径推导，`judge_type` 应然结果**全部为 1**，jt2/jt3 天然为 0。

### 2.2 数据源追溯：`product_lib_item` 只来自旧 `lib`

`db/migrations/V1__import_legacy_data.sql` 第 92–118 行：`product_lib_item` 的 **唯一数据源是旧 `lib` 表**。而旧 `lib`（3728 行，与新表行数一致）的 `stdValue` **同样 100% 是纯数值**：

```sql
SELECT COUNT(*) FROM lib WHERE stdValue LIKE '%不得检出%';      -- → 0
SELECT COUNT(*) FROM lib WHERE TRIM(stdValue) = '--';          -- → 0
SELECT COUNT(*) FROM lib WHERE TRIM(IFNULL(stdValue,'')) <> ''
   AND TRIM(TRAILING '*' FROM TRIM(stdValue)) NOT REGEXP '^[0-9]+(\\.[0-9]+)?$';  -- → 0
```

→ **不是 V1 迁移漏迁，是源表本来就没有**。

### 2.3 `prj_detail` 的 `不得检出` 与 `lib` 不同源（关键证据）

旧 `prj_detail`（检验记录明细，463 行）确有 jt2 形态：`不得检出` **220 条**、`--` **52 条**。
但其 `不得检出` 的项目名只有 **5 个，全部是兽药残留类**：

| 项目名 | prj_detail 行数 |
|---|---|
| 硝基呋喃类代谢物（AOZ、SEM、AMOZ、AHD） | 45 |
| 诺氟沙星、氧氟沙星、培氟沙星、洛美沙星 | 44 |
| 恩诺沙星和环丙沙星 | 44 |
| 孔雀石绿 | 44 |
| 氯霉素 | 43 |

而这 5 个项目名在 `product_lib_item` 中**一个都不存在**：

```sql
SELECT (SELECT COUNT(DISTINCT TRIM(SUBSTRING_INDEX(testItem,',',1)))
        FROM prj_detail WHERE stdValue='不得检出')            AS prj_detail_jt2_items,   -- → 5
       (SELECT COUNT(DISTINCT item_name) FROM product_lib_item
        WHERE item_name IN (SELECT DISTINCT TRIM(SUBSTRING_INDEX(testItem,',',1))
                            FROM prj_detail WHERE stdValue='不得检出')) AS matched;      -- → 0
```

**根因**：`lib`（项目标准库）只覆盖**农残 GB 2763-2021** 一套标准（`basis_code` 唯一值即 `GB 2763-2021`）；
而 `prj_detail` 中的 `不得检出` 项属**兽残**等另一套标准体系。二者是**不同的标准库**，
`prj_detail` 无法用于「订正」`lib` 的行。

---

## 3. 建议的修订方案（供裁决）

| 方案 | 内容 | 评价 |
|---|---|---|
| **R1（推荐）** | **D5 目标改为「口径校验」而非「数据回填」**：V3 脚本保留为**可重跑的口径归一化器**，输出 before/after 统计；当前结果为零变更，即为「jt2/jt3 = 0 是数据事实」的存档证据。**同时明确：若系统将来需支持「不得检出」类项目，走「标准库数据补录」（新增兽残标准库行），而非本脚本。** | ✅ 已实现（`db/migrations/V3__correct_product_lib_item_judge_type.sql`）；不虚构数据，留痕清晰 |
| R2 | D5 作废，删除 V3 脚本 | ❌ 丢失口径校验能力，且后人会重新踩「为什么 jt2/jt3 是 0」的坑 |
| R3 | 从 `prj_detail` 反向**新建**兽残标准库行（补 `product_lib` / `product_lib_item`） | ⚠️ 超出 T-401 范围、需要新标准库数据（含 `basis_code`、方法、限量值），属**数据补录需求**，建议另开任务而非塞进 D5 |

**GLM 建议采纳 R1**，并把 R3 作为**独立待办**记录（是否要做取决于业务是否需要兽残项目，请用户定）。

---

## 4. 请 Copilot 明确回复的三点

1. **D5 修订结论**：采纳 R1 / R2 / R3，还是其他？
2. 若采纳 R1：V3 脚本的**定位措辞**是否需调整？（现文档头已写明「零变更为数据事实」）
3. **R3（补兽残标准库）**：是否需要开为独立任务？若需要，是否需先由你裁定数据来源口径？

---

## 5. 对 T-401 的影响（重要）

- **本问题不阻塞 T-401 主体实现**。T-401「项目分解自动套库」依赖的是
  `product_lib.product_name`（本轮已由 V2 补齐 92/92）与 `product_lib_item` 的项目清单，
  **不依赖 jt2/jt3 的存在**。
- 但**影响 T-601 的测试基线**：白名单文档第 4 节要求单测「回放旧 `prj_detail` 的 20 条不合格样例」，
  而这些样例多为兽残 `不得检出` 项 —— 它们**不在 `product_lib_item` 中**。
  建议 Copilot 一并裁定：这些样例是**作为引擎单测的构造数据**（不走标准库匹配），
  还是需要先补标准库（依赖 R3 的结论）。
