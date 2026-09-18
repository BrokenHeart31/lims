package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.common.PageResult;
import com.lims.common.enums.ReportType;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.dto.ReportGenerateDTO;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SysUser;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.ReportGenerateService;
import com.lims.service.SampleStatusLogService;
import com.lims.service.report.ReportDataBuilder;
import com.lims.vo.ReportPendingVO;
import com.lims.vo.ReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 检验报告生成服务实现（T-702）。
 *
 * <p><b>不变式</b>：生成动作是一次 **S80→S90 的状态流转**，必须
 * ①经 {@link SampleStatusTransition#assertTransition} 白名单校验、
 * ②用乐观条件 UPDATE（{@code WHERE id=? AND status=80}）落库，
 * 保证并发下同一份样品不会被生成两次报告（后到者 {@code updated==0} → 400）。</p>
 *
 * <p>重打（{@link #detail}）是<b>只读</b>路径：不校验状态、不改库，仅聚合渲染模型，
 * 因此对 S90 历史样品与已生成报告可反复打印。</p>
 */
@Service
@RequiredArgsConstructor
public class ReportGenerateServiceImpl implements ReportGenerateService {

    /** 状态流水来源：报告域 */
    private static final String SOURCE_REPORT = "REPORT";

    private final SampleMapper sampleMapper;
    private final SampleItemMapper sampleItemMapper;
    private final SysUserMapper sysUserMapper;
    private final ReportDataBuilder reportDataBuilder;
    /** 统一状态流水写入口（feature B）：报告生成 S80→S90 埋点 */
    private final SampleStatusLogService statusLogService;

    // =========================================================================
    // 8.2 可生成 / 可重打列表
    // =========================================================================

    @Override
    public PageResult<ReportPendingVO> pagePendingGenerate(long pageNum, long pageSize,
                                                           String sampleNo, String sampleName, String taskNo) {
        Page<Sample> page = new Page<>(pageNum, pageSize);
        Page<Sample> result = sampleMapper.selectPage(page, new LambdaQueryWrapper<Sample>()
                // S80 待生成 + S90 已生成（可重打印）
                .in(Sample::getStatus, SampleStatus.S80, SampleStatus.S90)
                .like(StringUtils.hasText(sampleNo), Sample::getSampleNo, sampleNo)
                .like(StringUtils.hasText(sampleName), Sample::getSampleName, sampleName)
                .eq(StringUtils.hasText(taskNo), Sample::getTaskNo, taskNo)
                .orderByDesc(Sample::getId));

        // 批量统计检测单项数（避免逐行 count 查询）
        Map<Long, Long> itemCounts = itemCountsBySample(
                result.getRecords().stream().map(Sample::getId).toList());

        List<ReportPendingVO> rows = result.getRecords().stream().map(s -> {
            ReportPendingVO vo = new ReportPendingVO();
            vo.setId(s.getId());
            vo.setSampleNo(s.getSampleNo());
            vo.setSampleName(s.getSampleName());
            vo.setClientName(s.getClientName());
            vo.setTaskNo(s.getTaskNo());
            vo.setInspectType(s.getInspectType());
            vo.setSamplingDate(s.getSamplingDate());
            vo.setStatus(s.getStatus() == null ? null : s.getStatus().getCode());
            vo.setStatusLabel(s.getStatusLabel());
            vo.setConclusion(s.getConclusion() == null ? null : s.getConclusion().getCode());
            vo.setConclusionLabel(s.getConclusionLabel());
            vo.setItemTotal(itemCounts.getOrDefault(s.getId(), 0L).intValue());
            vo.setAuditBy(s.getAuditBy());
            vo.setSignBy(s.getSignBy());
            vo.setSignAt(s.getSignAt());
            vo.setReportType(s.getReportType() == null ? null : s.getReportType().getCode());
            vo.setReportTypeLabel(s.getReportType() == null ? null : s.getReportType().getLabel());
            vo.setReportGeneratedAt(s.getReportGeneratedAt());
            return vo;
        }).toList();

        PageResult<ReportPendingVO> out = new PageResult<>();
        out.setRecords(rows);
        out.setTotal(result.getTotal());
        out.setCurrent(result.getCurrent());
        out.setSize(result.getSize());
        return out;
    }

    private Map<Long, Long> itemCountsBySample(List<Long> sampleIds) {
        if (sampleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                        .in(SampleItem::getSampleId, sampleIds))
                .stream()
                .collect(Collectors.groupingBy(SampleItem::getSampleId, Collectors.counting()));
    }

    // =========================================================================
    // 生成（S80 → S90）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVO generate(ReportGenerateDTO dto) {
        Sample sample = requireByNo(dto.getSampleNo());
        // 报告类型：null 缺省按 CMA(1)；非法 code 由 ReportType.of 抛 400
        int code = dto.getReportType() == null ? ReportType.CMA.getCode() : dto.getReportType();
        ReportType type = ReportType.of(code);

        // 只有「已签发」样品才能出报告（说明书的签发前置不可跳过）
        if (sample.getStatus() != SampleStatus.S80) {
            throw new BizException(400, "仅已签发样品可生成报告");
        }

        SampleStatusTransition.assertTransition(SampleStatus.S80, SampleStatus.S90);

        String operator = SecurityUtils.getUsername().orElse("system");
        Long operatorId = resolveUserId(operator);
        LocalDateTime now = LocalDateTime.now();

        Sample upd = new Sample();
        upd.setStatus(SampleStatus.S90);
        upd.setReportType(type);
        upd.setReportGeneratedAt(now);
        upd.setReportGeneratedBy(operatorId);
        int updated = sampleMapper.update(upd, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId())
                .eq(Sample::getStatus, SampleStatus.S80));
        if (updated == 0) {
            throw new BizException(400, "样品状态已变更，请刷新后重试");
        }

        // 状态流水埋点（feature B）：报告生成 S80→S90（event_type=7 报告生成）
        statusLogService.append(sample, StatusEventType.REPORT, SampleStatus.S80, SampleStatus.S90,
                "报告生成", null, SOURCE_REPORT, null, null);

        return reportDataBuilder.build(sample.getId(), type);
    }

    // =========================================================================
    // 详情 / 重打（只读）
    // =========================================================================

    @Override
    public ReportVO detail(String sampleNo, Integer reportType) {
        Sample sample = requireByNo(sampleNo);
        // 类型解析优先级：显式入参 → 样品已存类型 → CMA
        ReportType type = reportType == null ? null : ReportType.of(reportType);
        if (type == null) {
            type = sample.getReportType();
        }
        if (type == null) {
            type = ReportType.CMA;
        }
        return reportDataBuilder.build(sample.getId(), type);
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    /** 按编号取样品；编号为空或不存在均抛 400（业务失败走 HTTP 200 + body.code） */
    private Sample requireByNo(String sampleNo) {
        if (!StringUtils.hasText(sampleNo)) {
            throw new BizException(400, "样品编号不能为空");
        }
        Sample sample = sampleMapper.selectOne(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSampleNo, sampleNo.trim()));
        if (sample == null) {
            throw new BizException(400, "样品不存在: " + sampleNo);
        }
        return sample;
    }

    /** 将登录工号映射为 sys_user.id（report_generated_by 存 id）；查不到返回 null */
    private Long resolveUserId(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        return user == null ? null : user.getId();
    }
}
