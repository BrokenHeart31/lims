package com.lims.vo;

import com.lims.dto.RollbackExecuteDTO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量回退结果（api-spec B3，2026-09-30 新增）。
 *
 * <p><b>为什么必须返回逐条明细</b>：批量回退采用「逐条独立事务」——单条失败不影响其他，
 * 因此一次调用必然可能出现「部分成功」。此时只返回一个总体成功/失败是不够的：
 * 用户需要知道**具体哪几个样品没退成、为什么**（PRD「失败项明确告知，不得静默跳过」）。
 * 故响应体固定携带 {@link Item#getReason()} 与业务码。</p>
 */
@Data
public class RollbackBatchResultVO {

    /** 请求的目标步 code（回显，便于前端在部分失败时展示上下文） */
    private Integer targetStatus;

    private String targetStatusLabel;

    /** 请求条数 */
    private int total;

    /** 成功条数 */
    private int successCount;

    /** 失败条数 */
    private int failCount;

    /** 逐条明细（顺序与请求 ids 一致） */
    private List<Item> items = new ArrayList<>();

    /** 单条样品的回退结果（成功携带执行结果，失败携带业务码 + 原因） */
    @Data
    public static class Item {

        private Long sampleId;

        private String sampleNo;

        /** 是否成功 */
        private boolean success;

        /** 失败时的业务码（4101~4108 / 400 / 500）；成功时为 null */
        private Integer code;

        /** 失败原因（可直接展示给用户）；成功时为 null */
        private String reason;

        /** 成功时的执行结果 */
        private RollbackActionResultVO result;

        public static Item ok(Long sampleId, String sampleNo, RollbackActionResultVO result) {
            Item item = new Item();
            item.setSampleId(sampleId);
            item.setSampleNo(sampleNo);
            item.setSuccess(true);
            item.setResult(result);
            return item;
        }

        public static Item fail(Long sampleId, String sampleNo, Integer code, String reason) {
            Item item = new Item();
            item.setSampleId(sampleId);
            item.setSampleNo(sampleNo);
            item.setSuccess(false);
            item.setCode(code);
            item.setReason(reason);
            return item;
        }
    }

    /** 便于 Service 组装的空结果（不暴露给外部使用） */
    public static RollbackBatchResultVO empty(RollbackExecuteDTO dto) {
        RollbackBatchResultVO vo = new RollbackBatchResultVO();
        vo.setTargetStatus(dto.getTargetStatus());
        return vo;
    }
}
