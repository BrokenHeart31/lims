package com.lims.service.impl;

import com.lims.common.ResultCode;
import com.lims.common.enums.ResultConclusion;
import com.lims.common.exception.BizException;
import com.lims.dto.excel.MyTaskExportRow;
import com.lims.dto.excel.ProvinceExportRow;
import com.lims.entity.SysRole;
import com.lims.mapper.QueryMapper;
import com.lims.mapper.SysRoleMapper;
import com.lims.security.LoginUser;
import com.lims.security.SecurityUtils;
import com.lims.service.ExportService;
import com.lims.service.excel.ExcelExportUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 导出域服务实现（T-802 + T-603）。
 *
 * <p><b>省平台上报</b>：范围 {@code status >= 80}（含已签发 / 已出报告，已拍板），
 * 一行 = 样品 × 检测单项；「单项评价」中文由 {@link ResultConclusion#getLabel()} 填充，禁止硬编码。</p>
 *
 * <p><b>检验员任务</b>：默认按当前登录人工号过滤 {@code sample_item.tester_no}；
 * R100 综合管理（拥有全部数据权限）导出全部，不加检验员过滤。</p>
 */
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final QueryMapper queryMapper;
    private final SysRoleMapper sysRoleMapper;

    @Override
    public void exportProvince(String taskNo, HttpServletResponse response) {
        String normalizedTaskNo = StringUtils.hasText(taskNo) ? taskNo.trim() : null;
        List<ProvinceExportRow> rows = queryMapper.listProvinceRows(normalizedTaskNo);
        // 结论码 → 中文（统一取枚举，不在 SQL / Java 里硬编码中文）
        for (ProvinceExportRow row : rows) {
            ResultConclusion conclusion = ResultConclusion.ofNullable(row.getConclusion());
            row.setConclusionLabel(conclusion == null ? "" : conclusion.getLabel());
        }
        String fileName = ExcelExportUtil.timestampedFileName("系统导出数据");
        ExcelExportUtil.prepareDownload(response, fileName);
        ExcelExportUtil.writeSheet(response, "系统导出数据", ProvinceExportRow.class, rows);
    }

    @Override
    public void exportMyTasks(HttpServletResponse response) {
        String testerNo = resolveTesterScope();
        List<MyTaskExportRow> rows = queryMapper.listMyTasks(testerNo);
        String fileName = ExcelExportUtil.timestampedFileName("检验任务");
        ExcelExportUtil.prepareDownload(response, fileName);
        ExcelExportUtil.writeSheet(response, "检验任务", MyTaskExportRow.class, rows);
    }

    /**
     * 数据范围解析：
     * <ul>
     *   <li>R100 综合管理 → 返回 {@code null}（不加 tester_no 过滤，导出全部）</li>
     *   <li>普通检验员 → 返回本人工号（仅导出指派给自己的任务）</li>
     * </ul>
     */
    private String resolveTesterScope() {
        LoginUser loginUser = SecurityUtils.getLoginUser()
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED));
        String username = loginUser.getUsername();
        if (!StringUtils.hasText(username)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        List<String> roleCodes = loginUser.getId() == null
                ? List.of()
                : sysRoleMapper.selectRoleCodesByUserId(loginUser.getId());
        if (roleCodes.contains(SysRole.ADMIN_ROLE_CODE)) {
            return null;
        }
        return username;
    }
}
