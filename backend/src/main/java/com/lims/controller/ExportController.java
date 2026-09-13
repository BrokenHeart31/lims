package com.lims.controller;

import com.lims.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 导出接口（api-spec 第 8 章 /api/export/*，T-802 + T-603）。
 *
 * <p>权限标识与 AGENTS 8.2 / seed 一致：{@code export:province}（省平台上报）、
 * {@code result:export-excel}（检验员任务导出）。两个接口响应均为二进制流，
 * 故<b>不</b>返回统一 {@code R} 结构，直接写 {@link HttpServletResponse}。</p>
 */
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    /** 省平台上报导出：GET /api/export/province?taskNo=（taskNo 可选） */
    @GetMapping("/province")
    @PreAuthorize("hasAuthority('export:province')")
    public void exportProvince(@RequestParam(required = false) String taskNo,
                               HttpServletResponse response) {
        exportService.exportProvince(taskNo, response);
    }

    /** 检验员任务导出：GET /api/export/my-tasks（R100 导出全部，其余仅本人） */
    @GetMapping("/my-tasks")
    @PreAuthorize("hasAuthority('result:export-excel')")
    public void exportMyTasks(HttpServletResponse response) {
        exportService.exportMyTasks(response);
    }
}
