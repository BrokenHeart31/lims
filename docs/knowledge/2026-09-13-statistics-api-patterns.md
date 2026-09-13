# 后台统计接口的设计模式（聚合 SQL + 第三态语义）

> 结论层文档 ｜ 建立日期：2026-09-13 ｜ 作者：GLM ｜ 关联任务：T-803
>
> 推理过程见 `docs/journal/2026-09-13-glm-t105-107-603-803.md`；
> 选型与「禁 mock / 不加缓存」裁决见 `DECISIONS.md`「2026-09-13 T-803 图表库选型」。
>
> **本文的目标**：给出后台统计看板后端接口的一套可复制模式，重点解决
> ①N 次 count 往返 ②聚合 SQL 的逻辑删除过滤 ③「无数据」的语义表达 ④时间序列补零
> ⑤MySQL 保留字踩坑 六个高频问题。

---

## 1. 接口设计：一个信息域一个权限标识

统计看板是**独立信息域**（看趋势）而非「查询」（找样本）。不要复用 `query:*`：

```
❌ 复用 query:testing  → 权限语义混淆（看趋势 ≠ 看某个样品）
✅ 新增 stat:view      → 一个标识成本极低，语义清晰
```

**本项目 9 个接口统一 `stat:view`**：

| 方法 | 路径 | 参数 |
|---|---|---|
| GET | `/stat/overview` | — |
| GET | `/stat/sample-status` | — |
| GET | `/stat/inspect-type` | — |
| GET | `/stat/top-clients` | `limit`(≤50, 默认10) |
| GET | `/stat/category` | `limit` |
| GET | `/stat/tester-workload` | `limit` |
| GET | `/stat/dept` | — |
| GET | `/stat/unqualified-items` | `limit` |
| GET | `/stat/monthly-trend` | `months`(1~24, 默认6) |

**参数边界用 Bean Validation 声明**，不要靠 Service 手写 if：

```java
@GetMapping("/top-clients")
@PreAuthorize("hasAuthority('stat:view')")
public R<List<StatNameValueVO>> topClients(
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
    return R.ok(statService.topClients(limit));
}
```

⚠️ **`limit` 必须有上限**：统计接口若不限制 `limit`，一个 `?limit=999999` 就能拖垮服务。
即使 Service 层再兜底一次（`normalizeLimit`），注解层拦截能更早返回可读错误。

**Service 层二次兜底**（防止绕过 Controller 的内部调用）：

```java
private int normalizeLimit(int limit) {
    if (limit <= 0) return 10;
    return Math.min(limit, 50);
}
private int normalizeMonths(int months) {
    if (months <= 0) return 6;
    return Math.min(months, 24);
}
```

---

## 2. 总览指标：一条 SQL 取回多列，不要 N 次 count

**反例**（10 次往返）：

```java
long total = sampleMapper.count();
long testing = sampleMapper.countByStatus(50);
long completed = sampleMapper.countByStatus(60);
// ... 还有 7 次
```

**正例**（1 次往返，`SUM(CASE WHEN ...)` 多列聚合）：

```xml
<select id="selectOverview" resultType="com.lims.vo.StatOverviewVO">
    SELECT
        COUNT(*)                                                        AS totalSamples,
        SUM(CASE WHEN status &gt;= 40 AND status &lt; 60 THEN 1 ELSE 0 END) AS testingSamples,
        SUM(CASE WHEN status &gt;= 60 THEN 1 ELSE 0 END)                    AS completedSamples,
        SUM(CASE WHEN status = 90 THEN 1 ELSE 0 END)                     AS reportCount,
        SUM(CASE WHEN conclusion IN (1, 2) THEN 1 ELSE 0 END)            AS judgedSamples,
        SUM(CASE WHEN conclusion = 1 THEN 1 ELSE 0 END)                  AS qualifiedSamples,
        SUM(CASE WHEN conclusion = 2 THEN 1 ELSE 0 END)                  AS unqualifiedSamples,
        SUM(CASE WHEN conclusion = 3 THEN 1 ELSE 0 END)                  AS pendingSamples
    FROM sample_info
    WHERE deleted = 0
</select>
```

**要点**：
- `SUM(...)` 结果类型是 `Long`，映射到 VO 的 `long` 字段；**表为空时 `SUM` 返回 `NULL`**，
  MyBatis 转 `long` 会报错 → VO 用包装类型 `Long` 或让 ResultSet 层面兜底。
  本项目在 VO 里用基本类型 + Service 层 `if (vo == null) vo = new StatOverviewVO()` 兜底。
- **XML 中 `<` `>` 必须转义**为 `&lt;` `&gt;`（或整段包 `<![CDATA[...]]>`）。

---

## 3. 逻辑删除必须显式 `deleted = 0`

**坑**：统计 SQL 多为聚合 + 子查询，**MyBatis-Plus 的逻辑删除插件对这类语句的改写不可靠**
（尤其是 `UNION`、派生表、`GROUP BY` 场景）。不要依赖插件自动注入。

**做法**：每条 SQL 显式写 `WHERE deleted = 0`：

```xml
<select id="countByInspectType" resultType="com.lims.vo.StatNameValueVO">
    SELECT inspect_type AS name, COUNT(*) AS value
    FROM sample_info
    WHERE deleted = 0                      <!-- 显式，不依赖插件 -->
      AND inspect_type IS NOT NULL
    GROUP BY inspect_type
    ORDER BY value DESC
</select>
```

⚠️ **关联查询时每张表都要过滤**：

```sql
FROM sample_item i
JOIN sample_info s ON i.sample_id = s.id
WHERE i.deleted = 0        -- 不要漏
  AND s.deleted = 0        -- 不要漏
```

---

## 4. 「无数据」必须有第三态（`null` ≠ `0`）

**核心洞察**：数值型指标存在三种状态，不是两种。

| 状态 | 语义 | 应返回 |
|---|---|---|
| 有结论 | 真算出来是多少 | 数值（含 `0.0`） |
| 无有效数据 | **算不出来** | `null` |
| 无数据 | 表为空 | `0` |

**实例：合格率**

```java
// 分子分母都排除了「待判定」
long denominator = vo.getQualifiedSamples() + vo.getUnqualifiedSamples();
if (denominator > 0) {
    vo.setQualifiedRate(
        BigDecimal.valueOf(vo.getQualifiedSamples())
            .multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
            .doubleValue());
} else {
    // 无有效结论：返回 null（前端显示「暂无数据」），与「0%」语义不同
    vo.setQualifiedRate(null);
}
```

**为什么合格率的分母要排除「待判定」**：
待判定是**数据缺口**（检验员还没录值 / 值不足以判定），不是质量结论。
把它算进分母会：
1. 虚低合格率（尚未开工的样品被算成「不算合格」）
2. 让「尚未开工」与「质量差」在数字上无法区分

**为什么必须返回 `null` 而不是 `0`**：
若返回 `0`，前端会显示「合格率 0.0%」——在有资质含义（CMA/CATL）的报告场景里
这是**错误信息**（暗示全部不合格）。数值型指标必须有「无数据」这个第三态。

**前端对应处理**：

```ts
const rateText = computed(() => {
  const r = overview.value?.qualifiedRate
  return r === null || r === undefined ? '暂无' : r.toFixed(1)
})
```

---

## 5. 排名榜 vs 构成分析：`percent` 的适用边界

统一用 `StatNameValueVO` 承载，但**不是所有场景都算占比**：

```java
public class StatNameValueVO {
    private String name;
    private long value;
    /** 占比（%）；排名类接口为 null（Top N 的百分比无意义且会误导） */
    private Double percent;
}
```

| 场景 | 算 `percent`？ | 理由 |
|---|---|---|
| 状态分布 / 大类构成（饼图） | ✅ 算 | 是「部分 vs 整体」关系 |
| Top N 排名（横向条形图） | ❌ 不算 | Top N 不是全集；且「前十占比」容易误导 |

**实测输出对比**：
```json
// 构成分析：有 percent
{"name":"监督抽检","value":2,"percent":100.0}
// 排名榜：percent 为 null
{"name":"南通润发生态园","value":1,"percent":null}
```

---

## 6. 时间序列必须补零月

**问题**：请求近 6 个月，但 DB 里只有 2 个月有记录。直接 `GROUP BY DATE_FORMAT(created_at,'%Y-%m')`
只会返回 2 行。

**后果**：折线图出现「断点」或把不相邻的月份错误连线，趋势判断失真。

**做法**：Java 层补零。

```java
// ① 先生成连续的 N 个月（含空月）
YearMonth now = YearMonth.now();
LinkedHashMap<String, StatTrendVO> byMonth = new LinkedHashMap<>();
for (int i = months - 1; i >= 0; i--) {
    String key = now.minusMonths(i).format(DateTimeFormatter.ofPattern("yyyy-MM"));
    byMonth.put(key, new StatTrendVO(key, 0L, 0L));       // 占位 0
}

// ② 把 DB 结果覆盖进占位（SQL 已按月份聚合）
for (StatTrendVO row : statMapper.selectMonthlyTrend(months)) {
    if (byMonth.containsKey(row.getMonth())) {
        byMonth.put(row.getMonth(), row);
    }
}

// ③ 按时间顺序输出
return new ArrayList<>(byMonth.values());
```

**要点**：
- 用 `LinkedHashMap` 保序（`HashMap` 会打乱时间顺序）
- **循环方向是「从早到晚」**（`for (int i = months-1; i >= 0; i--)`），保证输出升序
- SQL 侧用 `DATE_FORMAT(created_at, '%Y-%m')` 或 `DATE_SUB(CURDATE(), INTERVAL n MONTH)` 限定范围，
  减少扫描；补零在 Java 做（SQL 生成日期序列需要递归 CTE，可读性差）

**实测输出**：
```json
[{"month":"2026-04","sampleCount":0,"completedCount":0},
 {"month":"2026-05","sampleCount":0,"completedCount":0},
 {"month":"2026-06","sampleCount":0,"completedCount":0},
 {"month":"2026-07","sampleCount":0,"completedCount":0},
 {"month":"2026-08","sampleCount":0,"completedCount":0},
 {"month":"2026-09","sampleCount":2,"completedCount":1}]
```

---

## 7. 中文翻译在 Java 层做，SQL 只返回 code / 原始名

**原则**：**SQL 里不要硬编码中文**。

```xml
<!-- ❌ 错误：SQL 硬编码中文，改文案要改 SQL -->
SELECT CASE status WHEN 90 THEN '已出报告' ELSE '未知' END AS name, ...

<!-- ✅ 正确：返回原始 code，Java 翻译 -->
SELECT status AS name, COUNT(*) AS value FROM sample_info WHERE deleted = 0 GROUP BY status
```

```java
// Java 层翻译
Integer code = parseCode(row.getName());
SampleStatus status = SampleStatus.ofNullable(code);
// fail-loud：未知状态码不静默丢弃，保留原始值并标注，便于排查状态机越界写入
String label = status != null ? status.getLabel() : "未知(" + row.getName() + ")";
```

**好处**：文案改动只改 Java 枚举一处；且能复用枚举的单一真相源
（避免「枚举写了 S90=已出报告，SQL 写了 S90=报告已出」这类不一致）。

⚠️ **坑**：`SampleStatus.ofNullable(Integer)` 若返回**枚举本身**（可为 null）而非 `Optional`，
则不能写链式 `.map(...).orElse(...)`。**先看签名再写链式调用**。

---

## 8. MySQL 8 保留字坑

**`generated` 是 MySQL 8 保留字**。作为列别名会报 1064：

```sql
-- ❌ 报错：You have an error in your SQL syntax ... near 'generated'
SELECT SUM(CASE WHEN conclusion = 1 THEN 1 ELSE 0 END) AS generated FROM ...

-- ✅ 加前缀
SELECT SUM(CASE WHEN conclusion = 1 THEN 1 ELSE 0 END) AS cnt_generated FROM ...
```

**同类保留字**（后台统计常撞）：`generated`、`groups`、`rank`、`system`、`current`、`interval`。

**规避方式**：别名一律加语义前缀（`cnt_` / `sum_` / `stat_`），既避保留字又提升可读性。

> 其他常见保留字陷阱（本项目已踩）：`SAMPLE`（→ 表名用 `sample_info`）、
> `USER`（→ 表名用 `sys_user`）。

---

## 9. 检验员工号 → 姓名的三态处理

统计「检验员工作量」时，`tester_no` 是工号，需要显示姓名。但存在三种情况，
**不能合并处理**：

| 情况 | 显示 | 理由 |
|---|---|---|
| 工号能查到用户 | `姓名（工号）` | 正常 |
| **未指派**（`tester_no` 为空） | 原样（空 / 「未指派」） | 这是「还没分配任务」，不是异常 |
| 无对应账号（工号查不到用户） | `工号（账号已不存在）` | 这是**数据异常**，须提示 |

```java
private List<StatNameValueVO> resolveNicknames(List<StatNameValueVO> raw) {
    Set<String> testerNos = raw.stream()
        .map(StatNameValueVO::getName)
        .filter(StringUtils::hasText)
        .collect(Collectors.toSet());
    if (testerNos.isEmpty()) return raw;

    // 批量 IN 一次取回，避免 N+1
    Map<String, String> nameByNo = sysUserMapper
        .selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUsername, testerNos))
        .stream()
        .collect(Collectors.toMap(SysUser::getUsername, SysUser::getNickname, (a, b) -> a));

    for (StatNameValueVO row : raw) {
        String no = row.getName();
        if (!StringUtils.hasText(no)) continue;              // 未指派：保留原样
        String nickname = nameByNo.get(no);
        row.setName(nickname != null
            ? nickname + "（" + no + "）"
            : no + "（账号已不存在）");                        // fail-loud 标注
    }
    return raw;
}
```

**注意 `Collectors.toMap` 的合并函数**：`(a, b) -> a` 是必要的——
若 DB 里有重名 `username`（数据异常），不加合并函数会抛 `IllegalStateException`，
整个统计接口 500。**统计接口不应该因为一行脏数据而完全不可用。**

---

## 10. 缓存策略：先别加

**本项目裁决（DECISIONS 2026-09-13）：统计接口不在后端加缓存。**

理由：
- 演示/中小规模数据下 SQL 聚合足够快（本项目 9 个接口全部 < 200ms）
- 后端加缓存会引入失效策略与「看到旧数」的困惑——对管理层决策页尤其危险
- 需要刷新时，前端提供「刷新」按钮主动重取即可

**反例排除**：数据量上万后再引入 Caffeine/Redis，届时按需加。
**不要在数据量未知时就上缓存**——那是过早优化，且会掩盖慢 SQL 的真实问题。

**前端并行取数的正确写法**：

```ts
// ✅ allSettled：单接口失败不应导致整页空白
async function load() {
  loading.value = true
  await Promise.allSettled([
    getStatOverviewApi().then(d => (overview.value = d)),
    getSampleStatusStatApi().then(d => (statusDist.value = d ?? [])),
    // ...
  ])
  loading.value = false
}

// ❌ all：首个 reject 就中断 → 「一个接口挂了」升级为「整页不可用」
```

---

## 完整 Checklist

- [ ] 统计域用独立权限标识（不复用 `query:*`）
- [ ] `limit` / `months` 有 `@Min` `@Max` + Service 层再兜底
- [ ] 总览用**单 SQL 多列聚合**，不做 N 次 count
- [ ] 每条 SQL 显式 `deleted = 0`（含关联表），不依赖插件
- [ ] 数值指标支持**三态**：数值 / `null`（算不出）/ `0`（无数据）
- [ ] 合格率类指标分母排除「待判定」（数据缺口非质量结论）
- [ ] 构成分析算 `percent`，排名榜 `percent = null`
- [ ] 时间序列**补零月**（`LinkedHashMap` 保序，从早到晚生成）
- [ ] SQL 不硬编码中文，翻译在 Java 层复用枚举
- [ ] 别名避开 MySQL 8 保留字（`generated` / `groups` / `rank`…）
- [ ] 工号→姓名三态处理（正常 / 未指派 / 账号已不存在）
- [ ] `Collectors.toMap` 带合并函数（防脏数据致 500）
- [ ] 后端不加缓存，前端 `Promise.allSettled` + 刷新按钮
- [ ] **先在 MySQL 实测 SQL 再写 Java**（本轮验证有效的关键习惯）
