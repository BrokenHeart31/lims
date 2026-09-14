package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.OperationLogQueryDTO;
import com.lims.service.SysOperationLogService;
import com.lims.vo.SysOperationLogVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 操作日志接口（api-spec 第 15 章）。
 *
 * <p>只提供查询。写入由 {@code config/OperationLogInterceptor} 在每次写请求完成后自动完成，
 * 因此这里没有「新增日志」接口——审计流水不可由业务代码手工制造。</p>
 *
 * <p><b>权限</b>：接口本身只要求登录（用户菜单里的「我的操作日志」对所有人可见）；
 * 「能否看到别人的日志」由 {@code SysOperationLogServiceImpl} 按 `log:view` 权限收口。
 * 检查逻辑放在服务层而非 {@code @PreAuthorize}，是因为它取决于「请求者是谁」而非「有没有权限」，
 * 用注解表达会产生「无权限即 403」的错误语义（普通检验员应当能看自己的日志）。</p>
 */
@RestController
@RequestMapping("/sys/log")
@RequiredArgsConstructor
@Validated
public class SysLogController {

    private final SysOperationLogService operationLogService;

    /** 分页查询操作日志：GET /api/sys/log/page */
    @GetMapping("/page")
    public R<PageResult<SysOperationLogVO>> page(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime startTime,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
            LocalDateTime endTime) {
        OperationLogQueryDTO query = new OperationLogQueryDTO();
        query.setCurrent(current);
        query.setSize(size);
        query.setModule(module);
        query.setOperator(operator);
        query.setStartTime(startTime);
        query.setEndTime(endTime);
        return R.ok(operationLogService.page(query));
    }
}
