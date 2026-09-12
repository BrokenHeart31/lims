---
name: judge-engine
description: 为检验/检测类系统实现「结果自动判定引擎」的标准模板与一键复现流程——闭集白名单矩阵 + BigDecimal 严格比较 + 默认「待判定 + WARN 日志」+ 原始值/派生值分层留痕 + 构造数据穷举单测 + 端到端断言。当需要根据标准值（限量/不得检出/无要求）自动判定实测值合格性（LIMS 判定引擎、质检结论生成、规则明确的合规判定）时使用。
agent_created: true
---

# 判定引擎：闭集白名单矩阵实现模板

> 理论依据：`docs/knowledge/2026-09-12-judge-engine-research.md`（选型侦察，含「为什么不用规则引擎」）
> 口径依据（本项目）：`docs/knowledge/2026-09-11-judge-engine-whitelist.md`（裁决定稿）
> 实战落地：T-601（`service/judge/JudgeEngine` + `sample_result` + `/api/result/*`，85 单测 + 45 端到端断言全过）

## 触发场景

- 需要按「标准值 + 实测值」自动给出合格 / 不合格结论（食品、环境、材料、计量等检验场景）
- 标准值形态有限且稳定（如 纯数值限量 / `≤X` / `不得检出` / `不得使用` / `--` 无要求）
- 结论要能写进正式报告，需要**可解释 + 可复现 + 可审计**

## 一、四条不可让步的原则

### 原则 1：判定语义必须是**代码**，标准值只是**数据**

**禁止**把 `std_value` 当表达式求值（Aviator / QLExpress / SpEL / Groovy / Drools DRL 动态加载）。
数据一旦获得执行语义：① 标准库被污染即产生不可预测结论；② 无法静态审计「到底按什么规则判的」；
③ 单测无法覆盖全部输入。**规则是代码，判定依据才是数据。**

> 也因此**不引入任何规则引擎**（Drools 的 Rete 网络对 < 10 条固定规则毫无回报，
> Easy Rules 只是把 if-else 搬进对象，LiteFlow 解决的是流程编排与热更新——而判定口径变更**应该**走发版 + 单测回归，不允许线上热改）。

### 原则 2：闭集之外一律「待判定 + WARN」，**绝不静默判合格**

食品/安全类判定最危险的失效模式是「默认合格」。所以：

```java
// 任何解析不了 / 形态与类型矛盾 / 依据缺失 → 待判定，同时记 WARN 日志
private JudgeOutcome pending(String basis) {
    log.warn("[判定引擎] 无法自动判定 → 落「待判定」（禁止静默判合格）：{}", basis);
    return JudgeOutcome.pending(basis);   // 结论=PENDING，且带 requiresAttention=true 标记
}
```

`basis`（人可读依据）与 `requiresAttention`（布尔标记）**一起产出**：
前者落库供审计与人工复核，后者供编排层统计与单测断言（无需引入日志框架即可测）。

### 原则 3：数值比较一律 `BigDecimal.compareTo`

```java
new BigDecimal("2.00").equals(new BigDecimal("2.0"))        // ❌ false（equals 比 scale）
new BigDecimal("2.00").compareTo(new BigDecimal("2.0")) == 0 // ✅ true（比数值）
0.1 + 0.2 == 0.3                                            // ❌ false（IEEE 754）
```

- 解析用 `new BigDecimal(String)`；**禁止** `new BigDecimal(double)`。
- 比较用 `compareTo`；**禁止** `==` / `<=` 直接作用于 `double`。
- 解析失败**不抛异常**（一条脏数据不该打断整批录入）→ 返回 `null` → 走「待判定」。

### 原则 4：原始值 / 派生值**分层落库**

| 层 | 字段 | 语义 |
|---|---|---|
| 原始值 | `test_value` | 检验员录入的值（数值 或 「未检出」），**唯一的人为输入** |
| 派生值 | `conclusion` | 引擎按规则算出：1 合格 / 2 不合格 / 3 待判定 |
| 派生值 | `conclusion_source` | 1 引擎自动 / 2 人工判定（感官项走人工） |
| 派生值 | `judge_basis` | 判定依据说明（人可读）：`限量值 0.5，实测 0.60 > 限量，判定不合格` |

对应 ALCOA+（Original / Accurate / Legible / Attributable）：报告上的每个结论都能回放到
「哪条规则 + 哪个原始值 + 谁录的 + 什么时候」。**判定依据参数（标准值/检出限/是否参考项）不冗余存放**，
一律取自业务行的**快照字段**（标准库更新不得追溯篡改历史报告）。

## 二、一键复现步骤

### Step 1：先把口径写成矩阵（先文档后代码）

把「判定类型 × 实测值形态」的每个格子填满，包括**闭集外的兜底**。示例（本项目定稿）：

| 判定类型 | 实测值 | 结论 |
|---|---|---|
| jt1 限量比较（标准值 数值/≤X） | 未检出 | 合格 |
| jt1 | 数值 < 最低检出限 | 合格（视同未检出） |
| jt1 | 数值 ≤ X / > X | 合格 / 不合格 |
| jt2 不得检出·不得使用 | 未检出 | 合格 |
| jt2 | 数值 ≥ 检出限 / < 检出限 | 不合格 / 合格 |
| jt2 | 数值 且 检出限为空 | **待判定** |
| 标准值 `--`（无依据） | 未检出 或 数值 < 检出限 | 合格 |
| 标准值 `--` | 数值（≥ 检出限 或 无检出限） | **待判定** |
| jt3 文本/感官 | — | 人工选 合格/不合格（source=2） |
| 其余任意组合 | — | **待判定 + WARN** |

**矩阵里每个空格都要有归属**——没有空格，就没有「忘了判」的可能。

### Step 2：三个枚举 + 纯函数引擎

```java
// 结论闭集（落库 TINYINT，@EnumValue/@JsonValue 与项目既有状态枚举同约定）
public enum ResultConclusion { QUALIFIED(1,"合格"), UNQUALIFIED(2,"不合格"), PENDING(3,"待判定"); }

// 结论来源
public enum ConclusionSource { ENGINE(1,"自动判定"), MANUAL(2,"人工判定"); }

// 输入/输出用 record（不可变、自带 equals/toString，便于断言）
public record JudgeInput(Integer judgeType, String stdValue, String lowerLimit,
                         String testValue, Integer manualConclusion) {}

public record JudgeOutcome(ResultConclusion conclusion, ConclusionSource source,
                           String basis, boolean requiresAttention) {
    public static JudgeOutcome engine(ResultConclusion c, String basis) { ... requiresAttention=false }
    public static JudgeOutcome manual(ResultConclusion c, String basis) { ... }
    public static JudgeOutcome pending(String basis) { ... conclusion=PENDING, requiresAttention=true }
}

@Component
@Slf4j
public class JudgeEngine {          // 纯函数：无状态、无 IO → 可被单测直接构造覆盖
    public JudgeOutcome judge(JudgeInput in) { /* 见 Step 3 */ }

    // 形态解析器必须是 public static，便于独立单测
    public static StdValueForm parseStdValue(String raw) { ... }
    public static TestValueForm parseTestValue(String raw) { ... }
    public static BigDecimal parseNumber(String raw) { ... }
}
```

### Step 3：引擎主干（分派 → 解析 → 兜底 → 矩阵）

```java
public JudgeOutcome judge(JudgeInput in) {
    // ① 人工型先行（唯一允许人给结论的分支）
    if (Objects.equals(in.judgeType(), JT_MANUAL)) {
        ResultConclusion m = ResultConclusion.ofNullable(in.manualConclusion());
        return (m == null || m == PENDING)
            ? pending("文本/感官项目需检验员人工选择「合格」或「不合格」，当前未选择")
            : JudgeOutcome.manual(m, "文本/感官项目，由检验员人工判定为「" + m.getLabel() + "」");
    }
    // ② 类型越界 → 兜底
    if (in.judgeType() == null || (in.judgeType() != JT_LIMIT && in.judgeType() != JT_NOT_DETECTED))
        return pending("判定类型非法（judgeType=" + in.judgeType() + "），无法自动判定");
    // ③ 空值 → 兜底（不抛异常）
    if (stdRaw == null)  return pending("标准值为空，缺少判定依据");
    if (testRaw == null) return pending("未录入检验值");

    StdValueForm std = parseStdValue(stdRaw);
    TestValueForm tv = parseTestValue(testRaw);
    BigDecimal lower = parseNumber(in.lowerLimit());

    // ④ 闭集外 → 兜底
    if (std == UNKNOWN) return pending("标准值「" + stdRaw + "」不在判定白名单闭集内，需人工判定");
    if (tv  == UNKNOWN) return pending("检验值「" + testRaw + "」既非数值也非「未检出」，需人工判定");

    // ⑤ 形态与类型一致性校验（矛盾数据也兜底，不猜）
    if (std == NONE) return judgeNoBasis(...);                       // `--` 矩阵
    if (jt == JT_NOT_DETECTED) {
        if (std != NOT_DETECTED && std != NOT_USED)
            return pending("判定类型为「不得检出/不得使用」，但标准值为数值型，口径矛盾，需人工判定");
        return judgeNotDetected(...);
    }
    if (std != NUMERIC && std != LE_NUMERIC)
        return pending("判定类型为「限量比较」，但标准值不是数值型，口径矛盾，需人工判定");
    return judgeLimit(...);
}
```

**关键细节**：
- **形态关键词别复用**：标准值写「**不得**检出」，检验值写「**未**检出」。共用同一个常量会把
  jt2 全部分支打成「待判定」（本项目实测踩过，6 个单测同时报红）。
- **先长匹配后短匹配**：`不得检出` / `不得使用` 要在纯数值解析**之前**判断。
- **剥离迁移残留**：标准值尾部的参考项星号 `*`、全角破折号 `——`/`－`、千分位逗号、全角空格。
- **数值型子矩阵里 `parseNumber` 不会返回 null**（前面已校验），但仍写一次防御性兜底。

### Step 4：结果表 + 整体结论

```sql
CREATE TABLE `sample_result` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `sample_id` BIGINT NOT NULL, `sample_item_id` BIGINT NOT NULL,
  `test_value` VARCHAR(100) DEFAULT NULL,          -- 原始值
  `conclusion` TINYINT NOT NULL DEFAULT 3,          -- 1/2/3
  `conclusion_source` TINYINT NOT NULL DEFAULT 1,   -- 1 引擎 / 2 人工
  `judge_basis` VARCHAR(255) DEFAULT NULL,          -- 判定依据说明
  `entered_by` VARCHAR(64), `entered_at` DATETIME,
  -- 审计四字段 + deleted ...
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_result_item` (`sample_item_id`, `deleted`)   -- 一项一行，覆盖式 upsert
) COMMENT='检验结果（原始值 + 判定结论）';
```

- **覆盖式 upsert**：一项恒一行，重复保存走 UPDATE，修订由 `updated_by/at` 留痕（不产生第二行）。
- **整体结论**（聚合规则）单独存放在主业务行上，且**只用非参考项**：
  ```
  存在未录入项 → 待判定；无非参考项（全参考项）→ 待判定；
  存在非参考项不合格 → 不合格；存在非参考项待判定 → 待判定；其余 → 合格
  ```
- 整体结论**只在保存/提交时重算回写**；明细查询实时重算（GET 不要产生写副作用）。

### Step 5：状态流转（双保险）

进入域校验前置状态 → 首次保存推进一级 → 提交再推进一级；每次都：

```java
XxxStatusTransition.assertTransition(FROM, TO);                       // 白名单（EnumMap）
int updated = mapper.update(upd, wrapper.eq(id).eq(status, FROM));    // 乐观条件 UPDATE
if (updated == 0) throw new BizException(400, "状态已变更，请刷新后重试");
```

### Step 6：单测（构造数据穷举 + 日志断言）

```java
class JudgeEngineTest {
    private final JudgeEngine engine = new JudgeEngine();
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach void setUp() {
        Logger lg = (Logger) LoggerFactory.getLogger(JudgeEngine.class);
        appender = new ListAppender<>(); appender.start();
        lg.addAppender(appender); lg.setLevel(Level.WARN);
    }
    @AfterEach void tearDown() { ((Logger) LoggerFactory.getLogger(JudgeEngine.class)).detachAppender(appender); }

    private void assertWarned(String frag) {   // 「必须记日志」的可测断言
        assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(frag)));
    }

    @Test void 不得检出_数值低于检出限_合格() { assertEquals(QUALIFIED, judge(2,"不得检出","0.02","0.01").conclusion()); }
    @Test void 不得检出_检出限为空_待判定并告警() {
        JudgeOutcome o = judge(2, "不得检出", null, "0.01");
        assertEquals(PENDING, o.conclusion()); assertTrue(o.requiresAttention()); assertWarned("未维护最低检出限");
    }
    @Test void 浮点陷阱_小数位不同仍判相等() { assertEquals(QUALIFIED, judge(1,"0.50",null,"0.5").conclusion()); }
    @Test void 双精度误差不放行() { assertEquals(UNQUALIFIED, judge(1,"0.3",null,String.valueOf(0.1+0.2)).conclusion()); }
}
```

**验收基线**：矩阵里**每一格**至少一条用例；**所有走「待判定」的分支都要有日志断言**；
人工型（jt3）与闭集外（文本标准值、矛盾类型、类型越界）必须有反例。

### Step 7：端到端（单测之外的第二道闸）

单测用 Mock，测不到 `@PreAuthorize`、MP 枚举集合参数、真实落库形态。必须补真实链路脚本
（模式见 `.agents/skills/lims-stage-delivery/SKILL.md` 第 7.5 步）：
登录 → 列表 → 明细 → **逐条打各分支预览** → 保存 → 提交 → **负向用例**（越权 / 越态 / 非法入参）。

## 三、验收自检清单

- [ ] 口径矩阵每一格都有归属，闭集外有兜底
- [ ] 没有任何路径会「默认合格」；待判定必带人可读依据 + WARN
- [ ] 全部数值比较走 `BigDecimal.compareTo`，无 `==`/`equals`
- [ ] 形态关键词「不得检出 / 未检出」分别定义，未复用
- [ ] 原始值、结论、结论来源、判定依据**四件都落库**
- [ ] 判定依据参数取自业务行快照，未回溯标准库
- [ ] 一项一行 + 覆盖式 upsert + 审计字段留痕
- [ ] 单测覆盖矩阵全格 + 待判定分支日志断言 + 浮点回归
- [ ] 端到端含负向用例，且整体结论与库中数据一致
- [ ] 「存在待判定」是否阻断流转有**明确决策并留档**（本项目不阻断：放行红线交给审核/签发环节）

## 四、踩坑速查

| 现象 | 根因 | 处理 |
|---|---|---|
| jt2 全部分支都成了「待判定」 | 标准值关键词误用「未检出」 | 标准值用「不得检出」，检验值用「未检出」，各自常量 |
| `0.5` 与 `0.50` 判为不相等 | `BigDecimal.equals` 比 scale | 改 `compareTo` |
| 一条脏数据导致整批录入失败 | 解析失败抛异常 | 解析返回 `null` → 该条落「待判定」 |
| 修好列表却「看起来没数据」 | 账号无对应权限（403 → 空列表） | 用管理员账号验证，或先确认权限标识 |
| 单测无法断言「记了日志」 | 没有日志捕获 | logback `ListAppender` 挂目标 logger |
| 同一文件两处 Edit 后编译报「找不到符号」 | 并行 Edit 互相覆盖 | 同文件多次编辑必须**串行** |
