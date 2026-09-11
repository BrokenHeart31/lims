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
5. **驳回/回退分支**（如审核不通过 S70→S50）在 T-701 设计时由 Copilot 补充进白名单并同步 api-spec，他人不得私加。
6. **单测**：白名单每个 entry 一条断言 + 至少一条非法流转断言（AGENTS 4.3 要求核心业务规则必测）。
