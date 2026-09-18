package com.lims.dto;

import lombok.Data;

/**
 * 回退记录分页查询条件（api-spec B5，跨样品）。
 *
 * <p>分页参数 current/size 由 Controller 以 {@code @RequestParam} 传入，本 DTO 只承载查询条件。</p>
 */
@Data
public class RollbackHistoryQueryDTO {

    private String sampleNo;

    private Integer fromStatus;

    private Integer toStatus;
}
