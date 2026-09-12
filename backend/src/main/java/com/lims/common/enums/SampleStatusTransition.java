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
 * 正向 {@link #assertTransition(SampleStatus, SampleStatus)} 或退回
 * {@link #assertReturn(SampleStatus, SampleStatus)}；DAO 层禁止直接 set 状态字段。</p>
 *
 * <p><b>为什么「退回」是独立白名单（而不是塞进 {@link #VALID}）</b>：
 * AGENTS 7.2 的「（退回）审核退回 → S50」是**逆向**动作，语义上与正向推进完全不同——
 * 正向流转代表「业务向前走」，退回代表「上一环节否定、打回重做」。
 * 若把它并入正向表，`assertTransition(S60, S50)` 会变成**全局合法**，
 * 任何调用方（含未来新写的 Service）都可能把它当成普通推进误用，且单测无法区分意图；
 * 而实际上退回必须伴随**原因留痕 + 通知检验员**，是比正向更强的约束。
 * 故：正向与退回各自一张表、各自一个断言方法，**不得互相调用、不得合并**。</p>
 *
 * <p>性能：EnumMap 数组索引 O(1)，零外部依赖（选型依据见
 * docs/knowledge/2026-09-11-sample-statemachine-research.md）。</p>
 *
 * <p><b>增删状态/流转的唯一入口</b>：先改 AGENTS 7.2，再改本类并同步单测，
 * 其他 Agent 不得私加（AGENTS 2.6 自裁亦需落档 DECISIONS）。</p>
 */
public final class SampleStatusTransition {

    /** 正向流转白名单：key=当前状态，value=允许推进到的状态集合（自环须显式声明） */
    private static final Map<SampleStatus, Set<SampleStatus>> VALID = new EnumMap<>(SampleStatus.class);

    /**
     * 退回白名单（逆向，独立于 {@link #VALID}）。
     *
     * <p>当前唯一合法退回路径：**S60（检验完成）→ S50（检验中）**，即「审核退回」——
     * 审核人认为检验数据有误/有疑，打回给检验员重录（AGENTS 7.2 表格末行）。</p>
     */
    private static final Map<SampleStatus, Set<SampleStatus>> RETURN = new EnumMap<>(SampleStatus.class);

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

        RETURN.put(SampleStatus.S60, EnumSet.of(SampleStatus.S50));                       // 审核退回
    }

    private SampleStatusTransition() {
    }

    // =========================================================================
    // 正向流转
    // =========================================================================

    /**
     * 判断是否允许由 {@code from} 正向流转到 {@code to}。
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
     * 断言正向流转合法，非法即抛 {@link BizException}(业务码 400)。
     *
     * <p><b>注意：本方法不承认退回路径</b>。退回必须走 {@link #assertReturn}，
     * 两者不可互相替代（见类注释）。</p>
     */
    public static void assertTransition(SampleStatus from, SampleStatus to) {
        if (!canTransition(from, to)) {
            throw new BizException(400, "样品状态不允许从「" + label(from) + "」流转到「" + label(to) + "」");
        }
    }

    /**
     * 断言正向流转合法（按 code，供实体/前端传入的 TINYINT 值直接校验）。
     */
    public static void assertTransition(int fromCode, int toCode) {
        assertTransition(SampleStatus.of(fromCode), SampleStatus.of(toCode));
    }

    /**
     * 当前状态允许正向流转到的下一状态集合（只读），供前端渲染操作按钮 / 后端自查。
     */
    public static Set<SampleStatus> nextAllowed(SampleStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(VALID.getOrDefault(from, Collections.emptySet()));
    }

    // =========================================================================
    // 退回流转（逆向，独立白名单）
    // =========================================================================

    /**
     * 判断是否允许由 {@code from} **退回**到 {@code to}。
     *
     * @return 任一侧为 null 时返回 false
     */
    public static boolean canReturn(SampleStatus from, SampleStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return RETURN.getOrDefault(from, Collections.emptySet()).contains(to);
    }

    /**
     * 断言退回合法，非法即抛 {@link BizException}(业务码 400)。
     *
     * <p>消息明确带「退回」字样，便于前端与日志区分于正向流转失败。</p>
     */
    public static void assertReturn(SampleStatus from, SampleStatus to) {
        if (!canReturn(from, to)) {
            throw new BizException(400, "样品状态不允许从「" + label(from) + "」退回至「" + label(to) + "」");
        }
    }

    /**
     * 断言退回合法（按 code）。
     */
    public static void assertReturn(int fromCode, int toCode) {
        assertReturn(SampleStatus.of(fromCode), SampleStatus.of(toCode));
    }

    /**
     * 当前状态允许退回的状态集合（只读），供前端决定是否渲染「退回」按钮。
     */
    public static Set<SampleStatus> returnAllowed(SampleStatus from) {
        if (from == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(RETURN.getOrDefault(from, Collections.emptySet()));
    }

    private static String label(SampleStatus status) {
        return status == null ? "未知" : status.getLabel();
    }
}
