package com.lims.common.enums;

import com.lims.common.exception.BizException;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 样品状态流转白名单（唯一权威，AGENTS 7.2 / 0.2）。
 *
 * <p><b>规约</b>：Service 层任何改样品状态的方法，第一行必须调用
 * {@link #assertTransition(SampleStatus, SampleStatus)}；DAO 层禁止直接 set 状态字段。</p>
 *
 * <p>性能：EnumMap 数组索引 O(1)，零外部依赖（选型依据见
 * docs/knowledge/2026-09-11-sample-statemachine-research.md）。</p>
 *
 * <p><b>待补</b>：审核驳回/退回分支（AGENTS 7.2 「审核退回 → S50」）由 T-701 设计时
 * 由首席架构师补入白名单并同步 api-spec，其他 Agent 不得私加。</p>
 */
public final class SampleStatusTransition {

    /** 流转白名单：key=当前状态，value=允许流转到的状态集合（自环须显式声明） */
    private static final Map<SampleStatus, Set<SampleStatus>> VALID = new EnumMap<>(SampleStatus.class);

    static {
        VALID.put(SampleStatus.S10, EnumSet.of(SampleStatus.S20));                        // 登记确认
        VALID.put(SampleStatus.S20, EnumSet.of(SampleStatus.S30));                        // 项目分解确认
        VALID.put(SampleStatus.S30, EnumSet.of(SampleStatus.S40));                        // 任务安排确认
        VALID.put(SampleStatus.S40, EnumSet.of(SampleStatus.S50));                        // 检验员首次录入
        VALID.put(SampleStatus.S50, EnumSet.of(SampleStatus.S50, SampleStatus.S60));      // 续录（自环）/录齐
        VALID.put(SampleStatus.S60, EnumSet.of(SampleStatus.S70));                        // 审核通过
        VALID.put(SampleStatus.S70, EnumSet.of(SampleStatus.S80));                        // 签发
        VALID.put(SampleStatus.S80, EnumSet.of(SampleStatus.S90));                        // 报告生成完成
        VALID.put(SampleStatus.S90, EnumSet.noneOf(SampleStatus.class));                  // 终态
    }

    private SampleStatusTransition() {
    }

    /**
     * 判断是否允许由 {@code from} 流转到 {@code to}。
     *
     * @return 任一侧为 null 时返回 false
     */
    public static boolean canTransition(SampleStatus from, SampleStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return VALID.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    /**
     * 断言流转合法，非法即抛 {@link BizException}(业务码 400)。
     */
    public static void assertTransition(SampleStatus from, SampleStatus to) {
        if (!canTransition(from, to)) {
            throw new BizException(400, "样品状态不允许从「" + label(from) + "」流转到「" + label(to) + "」");
        }
    }

    /**
     * 断言流转合法（按 code，供实体/前端传入的 TINYINT 值直接校验）。
     */
    public static void assertTransition(int fromCode, int toCode) {
        assertTransition(SampleStatus.of(fromCode), SampleStatus.of(toCode));
    }

    /**
     * 当前状态允许流转到的下一状态集合（只读），供前端渲染操作按钮 / 后端自查。
     */
    public static Set<SampleStatus> nextAllowed(SampleStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(VALID.getOrDefault(from, Collections.emptySet()));
    }

    private static String label(SampleStatus status) {
        return status == null ? "未知" : status.getLabel();
    }
}
