package com.lims.common.enums;

import com.lims.common.ResultCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 回退边策略（**唯一权威**，设计 §5.1 / §9）。
 *
 * <p>本类回答一条回退边（from→to）的全部策略问题：
 * ①分组、②所需权限、③是否二次确认、④失效范围、⑤被拒原因（code+msg），
 * 并回答「跨级回退怎么走」：⑥逐级链、⑦可达目标步、⑧链的分组、⑨链的失效范围。
 * 任何新增回退边**必须同时**改 {@link SampleStatusTransition#ROLLBACK 白名单} 与本类 + 单测
 * （对齐「增删状态的唯一入口」约定）。</p>
 *
 * <p><b>回退边（逐级，PRD T4）</b>：
 * <pre>
 *   常规（未签发）  S20→S10、S30→S20、S40→S30、S50→S40、S60→S50
 *   敏感（已审核）  S70→S60   ← 需 rollback:sensitive + 二次确认
 *   被拒            S80/S90 出发的任何回退 → 4102 / 4103，改走「作废 / 召回」
 * </pre>
 *
 * <p><b>跨级回退（2026-09-30 增量）</b>：用户可直接选目标步（如 S40→S10），
 * 但系统**不给状态机新增任何直接边**——跨级一律由 {@link #chain} 沿既有 ROLLBACK 白名单
 * **逐级链式**推导（S40→S30→S20→S10），每一级仍走既有的白名单断言 + 乐观条件 UPDATE +
 * 下游失效处置 + 留档。这样「边少而清晰」的状态机性质不被破坏，且每级的处置语义与逐级回退
 * **完全一致**（不引入第二套语义）。</p>
 */
public final class RollbackEdgePolicy {

    /** 敏感回退所需权限标识 */
    public static final String PERM_SENSITIVE = "rollback:sensitive";
    /** 常规回退所需权限标识 */
    public static final String PERM_EXECUTE = "rollback:execute";

    /** 成环/超长保护上限（状态数上限即天然上界，此处再留一倍余量兜底） */
    private static final int MAX_CHAIN_STEPS = SampleStatus.values().length * 2;

    private RollbackEdgePolicy() {
    }

    /**
     * 回退被拒原因（业务码 + 可展示文案）。
     *
     * @param code 业务码（4101/4102/4103）
     * @param msg  面向用户的文案
     */
    public record RollbackReject(int code, String msg) {
    }

    // =========================================================================
    // ① 单边策略
    // =========================================================================

    /**
     * 该边所属回退分组；非回退边返回 {@code null}。
     */
    public static RollbackGroup groupOf(SampleStatus from, SampleStatus to) {
        if (from == null || to == null) {
            return null;
        }
        if (from == SampleStatus.S70 && to == SampleStatus.S60) {
            return RollbackGroup.SENSITIVE;
        }
        if (from == SampleStatus.S20 && to == SampleStatus.S10) return RollbackGroup.NORMAL;
        if (from == SampleStatus.S30 && to == SampleStatus.S20) return RollbackGroup.NORMAL;
        if (from == SampleStatus.S40 && to == SampleStatus.S30) return RollbackGroup.NORMAL;
        if (from == SampleStatus.S50 && to == SampleStatus.S40) return RollbackGroup.NORMAL;
        if (from == SampleStatus.S60 && to == SampleStatus.S50) return RollbackGroup.NORMAL;
        return null;
    }

    /**
     * 该边所需权限标识（Controller 层已按 rollback:execute 放行，敏感边由服务层二次校验）。
     */
    public static String requiredPermission(SampleStatus from, SampleStatus to) {
        return groupOf(from, to) == RollbackGroup.SENSITIVE ? PERM_SENSITIVE : PERM_EXECUTE;
    }

    /**
     * 该边是否需二次确认（仅敏感边）。
     */
    public static boolean needSecondConfirm(SampleStatus from, SampleStatus to) {
        return groupOf(from, to) == RollbackGroup.SENSITIVE;
    }

    /**
     * 该边需处置的下游数据范围。
     *
     * <p>映射原则：回退 = 撤销「该步骤及其下游」——
     * <ul>
     *   <li>S20→S10：退回已登记，尚未分解，无下游数据 → {@link ArchiveTarget#NONE}；</li>
     *   <li>S30→S20：撤销分解 → 明细失效（{@link ArchiveTarget#ITEM}），并连带失效引用明细的结果
     *       （{@link ArchiveTarget#RESULT}，避免孤儿引用，设计 §2.9 Pit 1）；</li>
     *   <li>S40→S30：取消安排 → 清空指派字段（{@link ArchiveTarget#ASSIGN_FIELDS}），明细保留；</li>
     *   <li>S50→S40：撤销首次录入 → 结果失效（{@link ArchiveTarget#RESULT}）；</li>
     *   <li>S60→S50：退回录制 → 仅状态回退，结果保留 → {@link ArchiveTarget#NONE}；</li>
     *   <li>S70→S60：撤销审核 → 清空审核「当前有效值」（{@link ArchiveTarget#AUDIT_FIELDS}，
     *       完整历史仍在 sample_audit_log）。</li>
     * </ul>
     */
    public static Set<ArchiveTarget> invalidationScope(SampleStatus from, SampleStatus to) {
        if (from == SampleStatus.S20 && to == SampleStatus.S10) {
            return EnumSet.of(ArchiveTarget.NONE);
        }
        if (from == SampleStatus.S30 && to == SampleStatus.S20) {
            return EnumSet.of(ArchiveTarget.ITEM, ArchiveTarget.RESULT);
        }
        if (from == SampleStatus.S40 && to == SampleStatus.S30) {
            return EnumSet.of(ArchiveTarget.ASSIGN_FIELDS);
        }
        if (from == SampleStatus.S50 && to == SampleStatus.S40) {
            return EnumSet.of(ArchiveTarget.RESULT);
        }
        if (from == SampleStatus.S60 && to == SampleStatus.S50) {
            return EnumSet.of(ArchiveTarget.NONE);
        }
        if (from == SampleStatus.S70 && to == SampleStatus.S60) {
            return EnumSet.of(ArchiveTarget.AUDIT_FIELDS);
        }
        return EnumSet.of(ArchiveTarget.NONE);
    }

    // =========================================================================
    // ② 跨级链（沿 ROLLBACK 白名单逐级推导，不新增直接边）
    // =========================================================================

    /**
     * 从 {@code from} 到 {@code to} 的逐级链。
     *
     * <p>返回的是**每一步执行后的状态序列**（不含 `from`，含 `to`）。例如
     * {@code chain(S40, S10)} = {@code [S30, S20, S10]}，表示「退到 S30 → 再退到 S20 → 再退到 S10」，
     * 共 3 级；{@code chain(S40, S30)} = {@code [S30]}，即原有的一步逐级回退。</p>
     *
     * <p>不可达（含 {@code from == to}、逆向上行、S80/S90 出发、非回退状态组合）一律返回**空列表**，
     * 由 {@link #rejectReason} 给出可展示的拒绝原因。</p>
     *
     * <p>实现为沿白名单的**唯一路径遍历**：`ROLLBACK` 每个源状态恰好一条出边，
     * 故「逐级链」在同一状态机版本下是唯一的（不引入路径选择歧义）。</p>
     */
    public static List<SampleStatus> chain(SampleStatus from, SampleStatus to) {
        if (from == null || to == null || from == to) {
            return List.of();
        }
        List<SampleStatus> path = new ArrayList<>();
        Set<SampleStatus> visited = EnumSet.noneOf(SampleStatus.class);
        SampleStatus cursor = from;
        while (cursor != to) {
            if (!visited.add(cursor) || path.size() > MAX_CHAIN_STEPS) {
                // 成环保护：白名单被人为改出环时 fail-loud 返回不可达，绝不静默死循环
                return List.of();
            }
            SampleStatus next = nextRollbackStep(cursor);
            if (next == null) {
                return List.of();
            }
            path.add(next);
            cursor = next;
        }
        return Collections.unmodifiableList(path);
    }

    /**
     * 单步回退的下一个状态；无出边返回 {@code null}。
     *
     * <p>白名单「每个源状态一条出边」是设计约定（逐级），若将来出现多出边，
     * 本方法取白名单中的**唯一**元素；多于一条时 fail-loud 返回 {@code null}
     * （避免静默挑一条走，产生不可预测的回退路径）。</p>
     */
    private static SampleStatus nextRollbackStep(SampleStatus from) {
        Set<SampleStatus> allowed = SampleStatusTransition.rollbackAllowed(from);
        if (allowed.size() != 1) {
            return null;
        }
        return allowed.iterator().next();
    }

    /**
     * {@code from} 是否可沿逐级链到达 {@code to}（不含 {@code from == to}）。
     */
    public static boolean reachable(SampleStatus from, SampleStatus to) {
        return !chain(from, to).isEmpty();
    }

    /**
     * 当前状态沿 ROLLBACK 白名单**可达的全部目标步**（由近及远，逐级链的每个落点）。
     *
     * <p>例：S70 → {@code [S60, S50, S40, S30, S20, S10]}；
     * S80/S90 → 空列表（物理无回退出边）。</p>
     *
     * <p>这是「可选目标步」的**唯一来源**——前端不得自行枚举状态（见 api-spec B7）。</p>
     */
    public static List<SampleStatus> reachableTargets(SampleStatus from) {
        if (from == null) {
            return List.of();
        }
        List<SampleStatus> targets = new ArrayList<>();
        SampleStatus cursor = from;
        Set<SampleStatus> visited = EnumSet.noneOf(SampleStatus.class);
        while (visited.add(cursor) && targets.size() <= MAX_CHAIN_STEPS) {
            SampleStatus next = nextRollbackStep(cursor);
            if (next == null) {
                break;
            }
            targets.add(next);
            cursor = next;
        }
        return Collections.unmodifiableList(targets);
    }

    /**
     * 链的整体分组：链中**任一级**为敏感即整链敏感（S70→S60 的链路同样需要
     * `rollback:sensitive` + 二次确认）。
     */
    public static RollbackGroup chainGroup(SampleStatus from, List<SampleStatus> chain) {
        if (from == null || chain == null || chain.isEmpty()) {
            return null;
        }
        SampleStatus cursor = from;
        for (SampleStatus step : chain) {
            if (groupOf(cursor, step) == RollbackGroup.SENSITIVE) {
                return RollbackGroup.SENSITIVE;
            }
            cursor = step;
        }
        return RollbackGroup.NORMAL;
    }

    /**
     * 链的整体失效范围 = 各步失效范围的**并集**（顺序由调用方按链顺序执行，此处只做集合合并）。
     *
     * <p>例：S40→S10 = ASSIGN_FIELDS（清指派）+ ITEM/RESULT（撤销分解）+ NONE（退回已登记）。</p>
     *
     * <p>{@link ArchiveTarget#NONE} 表示「该步无需处置数据」，只在**整条链都不需要处置**时才有意义；
     * 若并集中出现任何实际处置类型，则剔除 NONE——否则集合会退化成
     * 「四个元素里有一个是『什么都不做』」，既无信息增量，又让下游判断（是否失效明细）多一层噪音。</p>
     */
    public static Set<ArchiveTarget> chainScope(SampleStatus from, List<SampleStatus> chain) {
        Set<ArchiveTarget> scope = EnumSet.noneOf(ArchiveTarget.class);
        if (from == null || chain == null || chain.isEmpty()) {
            scope.add(ArchiveTarget.NONE);
            return scope;
        }
        SampleStatus cursor = from;
        for (SampleStatus step : chain) {
            scope.addAll(invalidationScope(cursor, step));
            cursor = step;
        }
        if (scope.size() > 1) {
            scope.remove(ArchiveTarget.NONE);
        }
        return scope;
    }

    /**
     * 链的中间与终点状态 code 列表（供契约出网，前端渲染链路）。
     */
    public static List<Integer> chainCodes(List<SampleStatus> chain) {
        List<Integer> codes = new ArrayList<>();
        for (SampleStatus s : chain) {
            codes.add(s.getCode());
        }
        return codes;
    }

    /**
     * 链的中间与终点状态中文名列表（与 {@link #chainCodes} 一一对应）。
     */
    public static List<String> chainLabels(List<SampleStatus> chain) {
        List<String> labels = new ArrayList<>();
        for (SampleStatus s : chain) {
            labels.add(s.getLabel());
        }
        return labels;
    }

    /**
     * 链的完整展示文案（**含起点**），如「检验中 → 已安排 → 已分解 → 登记确认 → 已登记」。
     *
     * <p>含起点是刻意的：用户在确认框里需要一眼看出「从现在的状态出发会经过哪几步」，
     * 只给中间与终点会让人怀疑是不是跳过了某一级。</p>
     */
    public static String chainText(SampleStatus from, List<SampleStatus> chain) {
        if (from == null || chain == null || chain.isEmpty()) {
            return "";
        }
        // 用 LinkedHashSet 保证顺序 + 去重（S50→S50 这类自环不参与回退，此处仅为防御）
        List<String> nodes = new ArrayList<>();
        nodes.add(from.getLabel());
        for (SampleStatus s : chain) {
            nodes.add(s.getLabel());
        }
        return String.join(" → ", new LinkedHashSet<>(nodes));
    }

    // =========================================================================
    // ③ 拒绝原因
    // =========================================================================

    /**
     * 「该状态物理上没有任何回退路径」时的**治理指引**（仅 S80/S90）。
     *
     * <p>与 {@link #rejectReason} 的区别：那个回答「from→to 这一条能不能走」，
     * 本方法回答「**这个状态**还有没有回退这条路」——用于前端在无可选目标步时
     * 给出替代动作（作废 / 召回），而不是让用户面对一个空的选择框猜原因。</p>
     *
     * <p>S10（已登记）没有回退路径是**流程使然**（它已是起点），不产生治理指引，
     * 否则用户会被告知一个不存在的「替代动作」。</p>
     */
    public static Optional<RollbackReject> noPathHint(SampleStatus from) {
        if (from == SampleStatus.S80) {
            return Optional.of(new RollbackReject(
                    ResultCode.ROLLBACK_SIGNED.getCode(), ResultCode.ROLLBACK_SIGNED.getMsg()));
        }
        if (from == SampleStatus.S90) {
            return Optional.of(new RollbackReject(
                    ResultCode.ROLLBACK_REPORTED.getCode(), ResultCode.ROLLBACK_REPORTED.getMsg()));
        }
        return Optional.empty();
    }

    /**
     * 若该回退被拒，返回拒绝原因；允许则返回 {@link Optional#empty()}。
     *
     * <p>判定顺序（先「不可逆治理动作」，再「可达性」）：
     * <ol>
     *   <li>{@code from = S80} → 4102（已签发，走作废/召回）；</li>
     *   <li>{@code from = S90} → 4103（已出报告，只能更正/作废）；</li>
     *   <li>其余：沿 ROLLBACK 白名单**可达**（含跨级链）即允许；不可达 → 4101。</li>
     * </ol>
     * ⚠️ 注意：跨级**不再**是拒绝理由（2026-09-30 改造）——S60→S40 合法，
     * 由 {@link #chain} 逐级执行；但「逆向上行」（如 S30→S40）与「同级」仍必然 4101。</p>
     */
    public static Optional<RollbackReject> rejectReason(SampleStatus from, SampleStatus to) {
        if (from == SampleStatus.S80) {
            return Optional.of(new RollbackReject(
                    ResultCode.ROLLBACK_SIGNED.getCode(), ResultCode.ROLLBACK_SIGNED.getMsg()));
        }
        if (from == SampleStatus.S90) {
            return Optional.of(new RollbackReject(
                    ResultCode.ROLLBACK_REPORTED.getCode(), ResultCode.ROLLBACK_REPORTED.getMsg()));
        }
        if (reachable(from, to)) {
            return Optional.empty();
        }
        String fromLabel = from == null ? "未知" : from.getLabel();
        String toLabel = to == null ? "未知" : to.getLabel();
        return Optional.of(new RollbackReject(
                ResultCode.ROLLBACK_ILLEGAL.getCode(),
                "样品状态不允许从「" + fromLabel + "」回退至「" + toLabel + "」（只能沿既有环节逐级向下回退）"));
    }
}
