package com.lims.common.enums;

import com.lims.common.ResultCode;

import java.util.Optional;

/**
 * 回退边策略（**唯一权威**，设计 §5.1 / §9）。
 *
 * <p>本类回答一条回退边（from→to）的全部策略问题：
 * ①分组、②所需权限、③是否二次确认、④失效范围、⑤被拒原因（code+msg）。
 * 任何新增回退边**必须同时**改 {@link SampleStatusTransition#ROLLBACK 白名单} 与本类 + 单测
 * （对齐「增删状态的唯一入口」约定）。</p>
 *
 * <p><b>回退边（逐级，PRD T4）</b>：
 * <pre>
 *   常规（未签发）  S20→S10、S30→S20、S40→S30、S50→S40、S60→S50
 *   敏感（已审核）  S70→S60   ← 需 rollback:sensitive + 二次确认
 *   被拒            S80→S70（4102）、S90→S80（4103）→ 改走「作废 / 召回」
 * </pre>
 * 「跨级」（如 S60→S40）不属任何边，一律 4101。</p>
 */
public final class RollbackEdgePolicy {

    /** 敏感回退所需权限标识 */
    public static final String PERM_SENSITIVE = "rollback:sensitive";
    /** 常规回退所需权限标识 */
    public static final String PERM_EXECUTE = "rollback:execute";

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
    public static java.util.Set<ArchiveTarget> invalidationScope(SampleStatus from, SampleStatus to) {
        if (from == SampleStatus.S20 && to == SampleStatus.S10) {
            return java.util.EnumSet.of(ArchiveTarget.NONE);
        }
        if (from == SampleStatus.S30 && to == SampleStatus.S20) {
            return java.util.EnumSet.of(ArchiveTarget.ITEM, ArchiveTarget.RESULT);
        }
        if (from == SampleStatus.S40 && to == SampleStatus.S30) {
            return java.util.EnumSet.of(ArchiveTarget.ASSIGN_FIELDS);
        }
        if (from == SampleStatus.S50 && to == SampleStatus.S40) {
            return java.util.EnumSet.of(ArchiveTarget.RESULT);
        }
        if (from == SampleStatus.S60 && to == SampleStatus.S50) {
            return java.util.EnumSet.of(ArchiveTarget.NONE);
        }
        if (from == SampleStatus.S70 && to == SampleStatus.S60) {
            return java.util.EnumSet.of(ArchiveTarget.AUDIT_FIELDS);
        }
        return java.util.EnumSet.of(ArchiveTarget.NONE);
    }

    /**
     * 若该边被拒，返回拒绝原因；允许则返回 {@link Optional#empty()}。
     *
     * <p>区分三类：
     * <ul>
     *   <li>S80→S70 → 4102（已签发，走作废/召回）；</li>
     *   <li>S90→S80 → 4103（已出报告，只能更正/作废）；</li>
     *   <li>其余非回退边（含跨级）→ 4101，文案带 from→to 便于定位。</li>
     * </ul>
     */
    public static Optional<RollbackReject> rejectReason(SampleStatus from, SampleStatus to) {
        if (groupOf(from, to) != null) {
            return Optional.empty();
        }
        if (from == SampleStatus.S80 && to == SampleStatus.S70) {
            return Optional.of(new RollbackReject(
                    ResultCode.ROLLBACK_SIGNED.getCode(), ResultCode.ROLLBACK_SIGNED.getMsg()));
        }
        if (from == SampleStatus.S90 && to == SampleStatus.S80) {
            return Optional.of(new RollbackReject(
                    ResultCode.ROLLBACK_REPORTED.getCode(), ResultCode.ROLLBACK_REPORTED.getMsg()));
        }
        String fromLabel = from == null ? "未知" : from.getLabel();
        String toLabel = to == null ? "未知" : to.getLabel();
        return Optional.of(new RollbackReject(
                ResultCode.ROLLBACK_ILLEGAL.getCode(),
                "样品状态不允许从「" + fromLabel + "」回退至「" + toLabel + "」（回退仅支持逐级）"));
    }
}
