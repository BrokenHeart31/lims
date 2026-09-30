package com.lims.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 「可回退目标步 + 影响预览」聚合响应（api-spec B7，2026-09-30 新增）。
 *
 * <p><b>为什么要有这个接口</b>：改造后回退入口内嵌在各业务页面，用户点一次「回退」就必须
 * 立刻看到「**能退到哪几步、退到每步会动到哪些数据**」。若沿用「先查时间线拿单级目标、
 * 再逐个调 preview」，一次打开要发 N+1 个请求，且前端会出现「目标步清单」与「影响预览」
 * 两个来源，容易漂移。故收敛为**一次请求返回全部可选目标步及其影响摘要**（纯读、不落库）。</p>
 *
 * <p><b>目标步的唯一来源</b>：{@code RollbackEdgePolicy.reachableTargets}（沿 ROLLBACK 白名单逐级推导），
 * 前端**不得自行枚举状态**——否则状态机一改，前端就会给出后端不接受的选项。</p>
 */
@Data
public class RollbackTargetsVO {

    private Long sampleId;

    private String sampleNo;

    private Integer currentStatus;

    private String currentStatusLabel;

    /** 是否存在任何可达目标步（false 时前端不渲染回退入口） */
    private boolean rollbackAvailable;

    /** 可选目标步（由近及远；每步都带「该步将失效的下游数据」摘要） */
    private List<Target> targets = new ArrayList<>();

    /** 被拒说明（S80/S90 走「作废 / 召回」），可直接展示给用户 */
    private List<Rejected> rejected = new ArrayList<>();

    /** 一个可选目标步 */
    @Data
    public static class Target {

        /** 目标状态 code */
        private Integer status;

        private String statusLabel;

        /** 级数（1 = 单级；>1 = 跨级链式） */
        private int stepCount;

        /** 途经与终点状态 code（含终点；长度 = stepCount） */
        private List<Integer> chainCodes = new ArrayList<>();

        /** 与 {@link #chainCodes} 一一对应的中文名 */
        private List<String> chainLabels = new ArrayList<>();

        /** 链路展示文案，如「已安排 → 已分解 → 登记确认 → 已登记」 */
        private String chainText;

        /** 链的整体分组 code（1 常规 / 2 敏感；任一级敏感即整链敏感） */
        private Integer group;

        private String groupLabel;

        private boolean needSensitive;

        private boolean needSecondConfirm;

        /** 该目标步将失效的下游数据（整链口径，不是只算最后一级） */
        private List<RollbackPreviewVO.Invalidation> invalidations = new ArrayList<>();

        /** 将失效的数据总条数（0 = 纯状态回退） */
        private int invalidatedTotal;

        /** 提示文案 */
        private String hint;
    }

    /** 一条被拒的回退（用于引导改走作废 / 召回） */
    @Data
    public static class Rejected {

        private Integer from;

        private Integer to;

        private Integer code;

        private String msg;
    }
}
