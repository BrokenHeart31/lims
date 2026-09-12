# 侦察记录：样品状态机实现选型（枚举 + EnumMap 流转白名单）

> 侦察人：Copilot ｜ 日期：2026-09-11 ｜ 服务任务：T-301 起全部涉状态流转任务（S10→S90）
> 来源：Spring StateMachine 官方文档、得物技术状态机实践、EnumMap 轻量状态机模式（josealopez.dev）

## 结论（已定，对齐 AGENTS.md 7.2）

**采用轻量「枚举 + EnumMap 流转白名单」方案，不引入 Spring StateMachine 依赖。**

| 维度 | 枚举 + EnumMap 白名单（选定） | Spring StateMachine 4.x |
|---|---|---|
| 依赖 | JDK 原生，零新增 | spring-statemachine-starter（重） |
| 适配度 | LIMS 流转是**确定性线性主线 + 少量分支**（S10→…→S90），无并行 Region/层次状态 | 面向多事件、Guard/Action 编排、渠道分流的复杂场景（如得物上新） |
| 性能 | EnumMap O(1) 数组索引 | 状态机实例化 + 消息驱动，开销大 |
| 可测试性 | 纯 Java 单测，断言 canTransition 即可 | 需启状态机上下文 |
| 学习/维护 | 流转表一目了然，新人 5 分钟读懂 | 配置类 + 监听器分散，上手成本高 |

得物实践说明：只有当「同一事件按渠道/条件流向不同目标态」「需要 Guard/Action 解耦大量业务动作」时才值得上框架。LIMS 样品流转不满足该复杂度，AGENTS.md 7.2 亦明文「后端以枚举 + 流转白名单实现」。

## 落地模板（T-301 起所有流转必须经此，禁止私增状态/跳态）

```java
// common/enums/SampleStatus.java —— 状态枚举，code 与 DB TINYINT 对应
@Getter
@AllArgsConstructor
public enum SampleStatus {
    S10(10, "已登记"), S20(20, "登记确认"), S30(30, "已分解"),
    S40(40, "已安排"), S50(50, "检验中"), S60(60, "检验完成"),
    S70(70, "已审核"), S80(80, "已签发"), S90(90, "已出报告");
    private final int code;
    private final String label;
    public static SampleStatus of(int code) { /* 遍历匹配，非法抛 BizException */ }
}

// common/enums/SampleStatusTransition.java —— 唯一流转白名单（Map.ofEntries 声明式）
public final class SampleStatusTransition {
    private static final Map<SampleStatus, Set<SampleStatus>> VALID = Map.ofEntries(
        entry(SampleStatus.S10, Set.of(SampleStatus.S20)),   // 登记确认
        entry(SampleStatus.S20, Set.of(SampleStatus.S30)),   // 分解确认
        entry(SampleStatus.S30, Set.of(SampleStatus.S40)),   // 安排确认
        entry(SampleStatus.S40, Set.of(SampleStatus.S50)),   // 首次录入
        entry(SampleStatus.S50, Set.of(SampleStatus.S50, SampleStatus.S60)), // 续录/录齐
        entry(SampleStatus.S60, Set.of(SampleStatus.S70)),   // 审核通过（驳回回退态待 T-701 设计时补）
        entry(SampleStatus.S70, Set.of(SampleStatus.S80)),   // 签发
        entry(SampleStatus.S80, Set.of(SampleStatus.S90)),   // 报告生成
        entry(SampleStatus.S90, EnumSet.noneOf(SampleStatus.class)) // 终态
    );
    public static boolean canTransition(SampleStatus from, SampleStatus to) {
        return from != null && to != null
            && VALID.getOrDefault(from, EnumSet.noneOf(SampleStatus.class)).contains(to);
    }
    public static void assertTransition(SampleStatus from, SampleStatus to) {
        if (!canTransition(from, to)) {
            throw new BizException("样品状态不允许从「" + from.getLabel() + "」流转到「" + to.getLabel() + "」");
        }
    }
}
```

## 使用规约

1. **Service 层统一入口**：任何改样品状态的方法第一行调 `SampleStatusTransition.assertTransition(当前, 目标)`；DAO 层禁止直接 set 状态字段。
2. **幂等**：S50→S50（续录）这类自环必须在白名单显式声明，否则重复提交报非法流转。
3. **并发**：状态变更 SQL 用 `UPDATE ... WHERE id=? AND status=旧值` 乐观条件（MP LambdaUpdateWrapper），防并发双击跳态。
4. **前端**：按 /me 返回或详情接口中的当前状态渲染操作按钮（AGENTS 7.2），前端不自算下一态。
5. **退回（逆向）分支**：已由 T-701 落地为**独立 `RETURN` 白名单 + `assertReturn` 专用方法**
   （当前唯一路径 `S60 → S50` 审核退回）。**不要**把退回塞进正向 `VALID` 表——详见下方「落地补充（T-701）」。他人不得私加路径。
6. **单测**：白名单每个 entry 一条断言 + 至少一条非法流转断言（AGENTS 4.3 要求核心业务规则必测）。

## 落地补充（T-301 实测，2026-09-11 GLM）

按本项目技术栈（MyBatis-Plus 3.5.7 + Spring Jackson）落地时，模板另需两处映射注解，否则枚举与 TINYINT/JSON 之间存在歧义：

1. **持久化**：`SampleStatus.code` 上加 `@com.baomidou.mybatisplus.annotation.EnumValue`，
   MP 才会以 code 值读写 TINYINT（否则默认按 `name()` 或 ordinal，与 DB 值 10/20/… 不符）。
   `LambdaQueryWrapper.eq(Sample::getStatus, SampleStatus.S10)` 亦依赖该注解解析为 `status = 10`。
2. **JSON 出网**：`code` 上加 `@JsonValue`，并给枚举加 `@JsonCreator public static SampleStatus fromJson(Integer code)`，
   使接口 `status` 字段是数字 code（而非 `"S10"`）；中文名另以 `@TableField(exist=false)` 之外的
   **派生 getter** `getStatusLabel()` 输出（不落库、无需额外字段），前端直接展示，避免前端维护 code→label 字典。
3. **并发与审计**：乐观条件更新用 `update(patch, new LambdaUpdateWrapper<Sample>().eq(id).eq(status, 旧值))`——
   传 entity 才能触发 `MetaObjectHandler.updateFill`（updatedBy/updatedAt 自动填充）；只用 `.set()` 不传 entity 则不会触发填充。
4. **单测注意**：纯 JUnit（无 Spring）下构造 `LambdaQueryWrapper` 前需手动
   `TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Xxx.class)`，
   否则 MP 无法解析实体列名。

参考实现：`backend/src/main/java/com/lims/common/enums/SampleStatus.java`、
`.../SampleStatusTransition.java`、`.../service/impl/SampleServiceImpl.java`（confirmSamples）。

## 落地补充（T-701 实测，2026-09-12 GLM）：**正向与退回必须是两张独立白名单**

### 问题

AGENTS 7.2 末尾有「（退回）审核退回 → S50」。直觉做法是把 `S60 → S50` 加进 `VALID` 表，
**这是错的**：正向表一旦含该边，`assertTransition(S60, S50)` 就变成**全局合法**——
任何调用方（含未来新写的 Service）都可能把它当成普通推进使用，且单测无法区分「推进」与「打回重做」的意图。

### 定稿

```java
private static final Map<SampleStatus, Set<SampleStatus>> VALID  = ...; // 正向：S60 → {S70}
private static final Map<SampleStatus, Set<SampleStatus>> RETURN = ...; // 退回：S60 → {S50}

public static void assertTransition(SampleStatus from, SampleStatus to) { /* 只认 VALID  */ }
public static void assertReturn(SampleStatus from, SampleStatus to)     { /* 只认 RETURN */ }
```

**两个方法不可互相替代、不得合并。** 退回是比正向更强的约束：必须带原因、必须留痕、必须让检验员看到。

单测固化的不变式（`SampleStatusTransitionTest`）：
- `assertReturn(S60, S50)` 通过；
- `assertTransition(S60, S50)` **必须拒绝**（退回不是正向）；
- `assertReturn(S50, S60)` 拒绝（方向不可反）；
- `returnAllowed(S60) = {S50}` 且 `returnAllowed(S70)` 为空（未定义退回路径的状态不得有出边）。

### 配套：状态变更与审计留痕的分工

| 载体 | 内容 | 语义 |
|---|---|---|
| `sample_audit_log`（**只追加**） | action / from_status / to_status / opinion / abnormal_confirmed / operated_by / operated_at | 事件流水：谁在何时因何把样品从什么状态推到什么状态 |
| 业务主表上的 `audit_by/audit_at/sign_by/sign_at` | 当前有效的审核人/签发人 | 供报告打印直接取用（业务要求「报告无审核、批准人签字无效」） |

两条硬规则：
1. **退回要清空主表上的审核信息但保留流水**——报告上不得出现「未通过的审核人」，而「谁因何退回」是历史事实。
2. ⚠️ **MyBatis-Plus 实体式 `update` 会忽略 null 字段**，所以「清空某列」必须用
   `LambdaUpdateWrapper.set(col, null)` 显式表达；用 `entity.setXxx(null)` 是**无效**的（静默不清空）。

