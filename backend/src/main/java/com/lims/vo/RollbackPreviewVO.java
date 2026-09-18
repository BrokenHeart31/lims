package com.lims.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 回退前「下游影响预览」（api-spec B2，feature B）。
 *
 * <p>先 `preview` 再 `execute`：让用户在确认框中看到**将被失效的下游数据（计数 + 清单）**，
 * 满足 PRD B-05。被拒边不抛 HTTP 错误，而是 `allowed=false + code + msg`（前端据此提示改走作废/召回）。</p>
 */
@Data
public class RollbackPreviewVO {

    private Long sampleId;

    private String sampleNo;

    private Integer fromStatus;

    private String fromStatusLabel;

    private Integer toStatus;

    private String toStatusLabel;

    /** 是否允许该回退 */
    private boolean allowed;

    /** 被拒时的业务码（4101/4102/4103…）；允许时为 null */
    private Integer code;

    /** 被拒时的可展示文案；允许时为 null */
    private String msg;

    /** 回退分组 code（1 常规 / 2 敏感） */
    private Integer group;

    private String groupLabel;

    /** 原因是否必填（回退一律必填） */
    private boolean reasonRequired;

    /** 是否需敏感权限（rollback:sensitive） */
    private boolean needSensitive;

    /** 是否需二次确认 */
    private boolean needSecondConfirm;

    /** 是否不可逆（涉及 S80/S90 时重点提示；允许边一般为 false） */
    private boolean irreversible;

    /** 下游失效清单（计数 + 明细） */
    private List<Invalidation> invalidations = new ArrayList<>();

    /** 提示文案 */
    private String hint;

    /** 一类下游数据的失效预览 */
    @Data
    public static class Invalidation {

        /** 数据类别：sample_item / sample_result / assign_fields */
        private String type;

        private String typeLabel;

        private int count;

        private List<Item> items = new ArrayList<>();
    }

    /** 单条待失效数据（用于确认框清单） */
    @Data
    public static class Item {

        private Long id;

        private String label;

        public Item() {
        }

        public Item(Long id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}
