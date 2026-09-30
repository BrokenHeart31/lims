package com.lims.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 执行回退请求（api-spec B3，2026-09-30 改造为**批量 + 可选目标步**）。
 *
 * <p><b>为什么是 `ids` 而不是 `sampleId`</b>：同一环节的列表页需要勾选多个同状态样品
 * 批量回退到同一步；单样品回退就是长度为 1 的批量。保留两个字段会产生
 * 「到底以哪个为准」的二义，故契约收敛为唯一入口 `ids`。</p>
 *
 * <p><b>targetStatus 允许跨级</b>：服务端沿 `ROLLBACK` 白名单逐级链式执行
 * （如 S40→S10 = S40→S30→S20→S10），状态机边集合不变。</p>
 */
@Data
public class RollbackExecuteDTO {

    /** 待回退样品 id 列表（单个 = 长度 1；批量 = 多个同状态样品） */
    @NotEmpty(message = "请至少选择一个样品")
    private List<Long> ids;

    /** 目标状态 code（可以是当前状态沿 ROLLBACK 白名单可达的任意步，含跨级） */
    @NotNull(message = "目标状态不能为空")
    private Integer targetStatus;

    /** 回退原因（必填，业务校验） */
    @Size(max = 500, message = "回退原因不能超过 500 字")
    private String reason;

    /** 敏感链路（含 S70→S60 任一级）需二次确认 */
    private Boolean secondConfirmed;
}
