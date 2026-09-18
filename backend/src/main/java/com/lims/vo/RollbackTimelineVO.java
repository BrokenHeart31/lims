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

    /** 当前状态允许回退到的状态 code 列表（逐级） */
    private List<Integer> canRollbackTo = new ArrayList<>();

    /** 允许的回退边（供前端渲染「回退」目标） */
    private List<Edge> rollbackEdges = new ArrayList<>();

    /** 被拒的回退边（如 S80→S70、S90→S80），带业务码与说明 */
    private List<Rejected> rejectedEdges = new ArrayList<>();

    /** 全链路事件（按 id 升序 = 发生顺序） */
    private List<Event> events = new ArrayList<>();

    /** 一条允许的回退边 */
    @Data
    public static class Edge {

        private Integer from;

        private Integer to;

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
