package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 流程回溯时间线（api-spec B1，feature B，PRD §6）。
 *
 * <p>以时间线呈现该样品的**全部正向与逆向事件**（语义与追加型审计日志一致），
 * 并显式给出「可回退到什么状态 / 哪些边被拒 / 每条回退是否可再撤销」。</p>
 */
@Data
public class RollbackTimelineVO {

    private Long sampleId;

    private String sampleNo;

    private Integer currentStatus;

    private String currentStatusLabel;

    /** 当前状态沿 ROLLBACK 白名单**可达的全部目标步**（由近及远，含跨级；空 = 无可回退路径） */
    private List<Integer> canRollbackTo = new ArrayList<>();

    /** 允许的回退目标（供前端渲染「回退」目标步选择） */
    private List<Edge> rollbackEdges = new ArrayList<>();

    /** 被拒的回退说明（S80/S90 → 改走作废/召回），带业务码与说明 */
    private List<Rejected> rejectedEdges = new ArrayList<>();

    /** 全链路事件（按 id 升序 = 发生顺序） */
    private List<Event> events = new ArrayList<>();

    /** 一条允许的回退目标（from → to；跨级时 to 为链终点） */
    @Data
    public static class Edge {

        private Integer from;

        private Integer to;

        /** 级数（1 = 单级；>1 = 跨级链式） */
        private int stepCount;

        /** 链的整体分组（任一级敏感即整链敏感） */
        private Integer group;

        private String groupLabel;

        private boolean reasonRequired;
    }

    /** 一条被拒的回退边 */
    @Data
    public static class Rejected {

        private Integer from;

        private Integer to;

        private Integer code;

        private String msg;
    }

    /** 一条状态流水事件 */
    @Data
    public static class Event {

        private Long id;

        private Integer eventType;

        private String eventTypeLabel;

        private Integer fromStatus;

        private String fromStatusLabel;

        private Integer toStatus;

        private String toStatusLabel;

        private String actionLabel;

        private String reason;

        private String dataDisposition;

        private Long rollbackId;

        /** 回退批次号（跨级回退的各级流水共用；一次用户操作一个批次） */
        private String batchNo;

        /** 回退事件专用：是否可再撤销 */
        private Boolean canRecover;

        /** 回退事件专用：是否已被恢复 */
        private Boolean recovered;

        private String source;

        private String operatedBy;

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime operatedAt;
    }
}
