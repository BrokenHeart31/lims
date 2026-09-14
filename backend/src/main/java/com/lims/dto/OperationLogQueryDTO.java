package com.lims.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志查询条件（api-spec 第 15 章）。
 *
 * <p><b>刻意不提供 operator 的「数据范围绕过」能力</b>：本 DTO 虽有 operator 字段，
 * 但服务层会先用当前登录人的 `log:view` 权限判定——
 * 无该权限者一律被强制附加 `operator = 本人工号`，传参无效
 * （与 T-603 `MyTaskQueryDTO.testerScope` 同一原则：数据权限必须在服务端闭环）。</p>
 *
 * <p>时间字段采用 {@code @DateTimeFormat} 接收（与 T-801 查询域同一写法），
 * 格式固定 `yyyy-MM-dd HH:mm:ss`，非法格式由 Spring 直接 400 拒绝。</p>
 */
@Data
public class OperationLogQueryDTO {

    /** 页码（新域统一 current/size，api-spec 0.3） */
    private long current = 1;

    /** 每页条数 */
    private long size = 20;

    /** 模块精确筛选（可选） */
    private String module;

    /** 操作人工号（仅对拥有 log:view 的用户生效，可选） */
    private String operator;

    /** 起始时间（含），yyyy-MM-dd HH:mm:ss */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /** 结束时间（含），yyyy-MM-dd HH:mm:ss */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
