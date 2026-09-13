package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.common.PageResult;
import com.lims.common.enums.ReportType;
import com.lims.common.enums.ResultConclusion;
import com.lims.common.enums.SampleStatus;
import com.lims.common.exception.BizException;
import com.lims.dto.QueryLibraryDTO;
import com.lims.dto.QuerySampleDTO;
import com.lims.entity.ProductLib;
import com.lims.entity.ProductLibItem;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.entity.SysUser;
import com.lims.mapper.ProductLibItemMapper;
import com.lims.mapper.ProductLibMapper;
import com.lims.mapper.QueryMapper;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.service.QueryService;
import com.lims.service.result.ResultEntryPolicy;
import com.lims.vo.HistoryQueryVO;
import com.lims.vo.LibraryItemVO;
import com.lims.vo.LibraryQueryVO;
import com.lims.vo.TestingQueryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 查询域服务实现（T-801）。
 *
 * <p><b>分页骨架在 SQL、派生语义在 Java</b>：{@link QueryMapper} 只负责「按条件分页取样品头 + 原始时间字段」，
 * 明细进度等派生列由本类用「页内样品 id 批量查 items/results」补齐——批量为一条 IN 查询，不产生 N+1。
 * 之所以不在 SQL 里算进度，是因为「有效录入」口径必须唯一复用 {@link ResultEntryPolicy#isEntered}，
 * 在 SQL 里复刻会造成两套真相。</p>
 *
 * <p><b>停留时长近似口径</b>（已拍板，不新建状态流水表）：各阶段起始时间用既有字段近似——
 * S10=created_at、S20=confirmed_at、S30=updated_at、S40=MAX(sample_item.assigned_at)、
 * S50/S60=MAX(sample_result.updated_at)、S70=audit_at。</p>
 */
@Service
@RequiredArgsConstructor
public class QueryServiceImpl implements QueryService {

    /** 参考项标记（is_reference=1） */
    private static final int REFERENCE_YES = 1;

    private final QueryMapper queryMapper;
    private final SampleItemMapper sampleItemMapper;
    private final SampleResultMapper sampleResultMapper;
    private final SysUserMapper sysUserMapper;
    private final ProductLibMapper productLibMapper;
    private final ProductLibItemMapper productLibItemMapper;

    // =========================================================================
    // A1 在检样品
    // =========================================================================

    @Override
    public PageResult<TestingQueryVO> pageTesting(long current, long size, QuerySampleDTO q) {
        validateSampleQuery(q);
        IPage<TestingQueryVO> page = queryMapper.pageTesting(new Page<>(current, size), q);
        enrichTesting(page.getRecords());
        return PageResult.of(page);
    }

    /** 补齐在检样品的派生列：进度 / 中文标签 / 当前处理人 / 阶段时间与停留时长 */
    private void enrichTesting(List<TestingQueryVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(TestingQueryVO::getId).toList();
        Map<Long, List<SampleItem>> itemsBySample = loadItemsBySample(ids);
        Map<Long, Map<Long, SampleResult>> resultsBySample = loadResultsBySample(ids);

        // 处理人多为工号 → 姓名：批量解析 confirmed_by / audit_by
        Set<String> nos = new HashSet<>();
        for (TestingQueryVO r : rows) {
            if (StringUtils.hasText(r.getConfirmedBy())) {
                nos.add(r.getConfirmedBy());
            }
            if (StringUtils.hasText(r.getAuditBy())) {
                nos.add(r.getAuditBy());
            }
        }
        Map<String, String> nameByNo = resolveNicknames(nos);

        LocalDateTime now = LocalDateTime.now();
        for (TestingQueryVO r : rows) {
            List<SampleItem> items = itemsBySample.getOrDefault(r.getId(), List.of());
            Map<Long, SampleResult> results = resultsBySample.getOrDefault(r.getId(), Map.of());
            int total = items.size();
            int entered = countEntered(items, results);
            int blank = total - entered;
            int pending = countPending(items, results);

            r.setItemTotal(total);
            r.setEnteredCount(entered);
            r.setBlankCount(blank);
            r.setPendingCount(pending);
            r.setAbnormalCount(blank + pending);
            r.setStatusLabel(labelOfStatus(r.getStatus()));
            r.setConclusionLabel(labelOfConclusion(r.getConclusion()));
            r.setCurrentHandler(currentHandler(r, items, nameByNo));

            LocalDateTime stageAt = stageEnteredAt(r, items, results);
            r.setStageEnteredAt(stageAt);
            r.setStageStayHours(stayHours(stageAt, now));
        }
    }

    /**
     * 当前处理人推导（按状态取最贴切的既有字段，不新增表）：
     * <ul>
     *   <li>S10/S20 → confirmed_by 姓名；为空显示「待登记确认」</li>
     *   <li>S30 → 常量「待项目分解」</li>
     *   <li>S40/S50 → sample_item.tester_name（去重顿号连接）；全空显示「待指派」</li>
     *   <li>S60 → 常量「待审核」</li>
     *   <li>S70 → audit_by 姓名</li>
     * </ul>
     */
    private String currentHandler(TestingQueryVO r, List<SampleItem> items, Map<String, String> nameByNo) {
        SampleStatus status = SampleStatus.ofNullable(r.getStatus());
        if (status == null) {
            return null;
        }
        return switch (status) {
            case S10, S20 -> {
                String by = r.getConfirmedBy();
                if (!StringUtils.hasText(by)) {
                    yield "待登记确认";
                }
                yield nameByNo.getOrDefault(by, by);
            }
            case S30 -> "待项目分解";
            case S40, S50 -> {
                String names = items.stream()
                        .map(SampleItem::getTesterName)
                        .filter(StringUtils::hasText)
                        .distinct()
                        .collect(Collectors.joining("、"));
                yield names.isEmpty() ? "待指派" : names;
            }
            case S60 -> "待审核";
            case S70 -> {
                String by = r.getAuditBy();
                yield StringUtils.hasText(by) ? nameByNo.getOrDefault(by, by) : null;
            }
            default -> null;
        };
    }

    /** 进入当前阶段的时间（既有字段近似推导） */
    private LocalDateTime stageEnteredAt(TestingQueryVO r, List<SampleItem> items,
                                         Map<Long, SampleResult> results) {
        SampleStatus status = SampleStatus.ofNullable(r.getStatus());
        if (status == null) {
            return null;
        }
        return switch (status) {
            case S10 -> r.getCreatedAt();
            case S20 -> r.getConfirmedAt();
            case S30 -> r.getUpdatedAt();
            case S40 -> maxOf(items.stream().map(SampleItem::getAssignedAt).toList());
            case S50, S60 -> maxOf(results.values().stream().map(SampleResult::getUpdatedAt).toList());
            case S70 -> r.getAuditAt();
            default -> null;
        };
    }

    // =========================================================================
    // A2 历史样品
    // =========================================================================

    @Override
    public PageResult<HistoryQueryVO> pageHistory(long current, long size, QuerySampleDTO q) {
        validateSampleQuery(q);
        IPage<HistoryQueryVO> page = queryMapper.pageHistory(new Page<>(current, size), q);
        enrichHistory(page.getRecords());
        return PageResult.of(page);
    }

    private void enrichHistory(List<HistoryQueryVO> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> ids = rows.stream().map(HistoryQueryVO::getId).toList();
        Map<Long, List<SampleItem>> itemsBySample = loadItemsBySample(ids);
        Map<Long, Map<Long, SampleResult>> resultsBySample = loadResultsBySample(ids);

        Set<String> nos = new HashSet<>();
        for (HistoryQueryVO r : rows) {
            if (StringUtils.hasText(r.getAuditByNo())) {
                nos.add(r.getAuditByNo());
            }
            if (StringUtils.hasText(r.getSignByNo())) {
                nos.add(r.getSignByNo());
            }
        }
        Map<String, String> nameByNo = resolveNicknames(nos);

        for (HistoryQueryVO r : rows) {
            List<SampleItem> items = itemsBySample.getOrDefault(r.getId(), List.of());
            Map<Long, SampleResult> results = resultsBySample.getOrDefault(r.getId(), Map.of());
            int total = items.size();
            int blank = total - countEntered(items, results);
            int pending = countPending(items, results);
            r.setItemTotal(total);
            r.setBlankCount(blank);
            r.setPendingCount(pending);
            r.setAbnormalCount(blank + pending);
            r.setStatusLabel(labelOfStatus(r.getStatus()));
            r.setConclusionLabel(labelOfConclusion(r.getConclusion()));
            r.setAuditBy(resolveName(r.getAuditByNo(), nameByNo));
            r.setSignBy(resolveName(r.getSignByNo(), nameByNo));
            ReportType reportType = ReportType.ofNullable(r.getReportType());
            r.setReportTypeLabel(reportType == null ? null : reportType.getLabel());
        }
    }

    // =========================================================================
    // A3 项目库
    // =========================================================================

    @Override
    public PageResult<LibraryQueryVO> pageLibrary(long current, long size, QueryLibraryDTO q) {
        QueryLibraryDTO query = q == null ? new QueryLibraryDTO() : q;
        IPage<LibraryQueryVO> page = queryMapper.pageLibrary(new Page<>(current, size), query);
        return PageResult.of(page);
    }

    @Override
    public List<LibraryItemVO> listLibraryItems(Long productLibId) {
        if (productLibId == null) {
            throw new BizException(400, "产品库ID不能为空");
        }
        ProductLib lib = productLibMapper.selectById(productLibId);
        if (lib == null) {
            throw new BizException(400, "产品不存在或已删除: id=" + productLibId);
        }
        return productLibItemMapper.selectList(new LambdaQueryWrapper<ProductLibItem>()
                        .eq(ProductLibItem::getProductLibId, productLibId)
                        .orderByAsc(ProductLibItem::getItemOrder))
                .stream()
                .map(this::toLibraryItemVO)
                .toList();
    }

    private LibraryItemVO toLibraryItemVO(ProductLibItem src) {
        LibraryItemVO vo = new LibraryItemVO();
        vo.setId(src.getId());
        vo.setItemOrder(src.getItemOrder());
        vo.setItemName(src.getItemName());
        vo.setBasisCode(src.getBasisCode());
        vo.setMethods(src.getMethods());
        vo.setStdValue(src.getStdValue());
        vo.setJudgeType(src.getJudgeType());
        vo.setJudgeTypeLabel(ResultEntryPolicy.judgeTypeLabel(src.getJudgeType()));
        vo.setIsReference(src.getIsReference());
        vo.setLowerLimit(src.getLowerLimit());
        vo.setUnit(src.getUnit());
        return vo;
    }

    // =========================================================================
    // 内部工具（复用既有口径）
    // =========================================================================

    /** 有效录入项数（唯一口径：ResultEntryPolicy） */
    private int countEntered(List<SampleItem> items, Map<Long, SampleResult> results) {
        return (int) items.stream()
                .filter(i -> ResultEntryPolicy.isEntered(i.getJudgeType(), results.get(i.getId())))
                .count();
    }

    /**
     * 待判定项数：<b>已有效录入</b>、但结论为空或为「待判定」的项。
     *
     * <p>同 T-701 {@code AuditDetailVO.pendingCount} 口径；未录入项不计入此处（由 blankCount 表达），
     * 二者之和即 abnormalCount。</p>
     */
    private int countPending(List<SampleItem> items, Map<Long, SampleResult> results) {
        int count = 0;
        for (SampleItem item : items) {
            SampleResult r = results.get(item.getId());
            if (ResultEntryPolicy.isEntered(item.getJudgeType(), r)
                    && (r.getConclusion() == null || r.getConclusion() == ResultConclusion.PENDING)) {
                count++;
            }
        }
        return count;
    }

    private Map<Long, List<SampleItem>> loadItemsBySample(List<Long> sampleIds) {
        if (sampleIds == null || sampleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SampleItem>> out = new LinkedHashMap<>();
        sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                        .in(SampleItem::getSampleId, sampleIds)
                        .orderByAsc(SampleItem::getItemOrder))
                .forEach(i -> out.computeIfAbsent(i.getSampleId(), k -> new ArrayList<>()).add(i));
        return out;
    }

    private Map<Long, Map<Long, SampleResult>> loadResultsBySample(List<Long> sampleIds) {
        if (sampleIds == null || sampleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<Long, SampleResult>> out = new LinkedHashMap<>();
        sampleResultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                        .in(SampleResult::getSampleId, sampleIds))
                .forEach(r -> {
                    if (r.getSampleItemId() != null) {
                        out.computeIfAbsent(r.getSampleId(), k -> new LinkedHashMap<>())
                                .put(r.getSampleItemId(), r);
                    }
                });
        return out;
    }

    /** 工号 → 姓名（sys_user.nickname），取不到则该工号不出现在结果中 */
    private Map<String, String> resolveNicknames(Set<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getUsername, SysUser::getNickname)
                        .in(SysUser::getUsername, usernames))
                .stream()
                .filter(u -> StringUtils.hasText(u.getUsername()) && StringUtils.hasText(u.getNickname()))
                .collect(Collectors.toMap(SysUser::getUsername, SysUser::getNickname, (a, b) -> a));
    }

    private static String resolveName(String no, Map<String, String> nameByNo) {
        if (!StringUtils.hasText(no)) {
            return null;
        }
        return nameByNo.getOrDefault(no, no);
    }

    private static LocalDateTime maxOf(List<LocalDateTime> times) {
        return times.stream()
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /** 停留小时数（一位小数）；起始时间为空返回 null，未来时间按 0 处理 */
    private static Double stayHours(LocalDateTime from, LocalDateTime now) {
        if (from == null) {
            return null;
        }
        long minutes = Duration.between(from, now).toMinutes();
        if (minutes < 0) {
            minutes = 0;
        }
        // 分钟 → 小时并保留一位小数：round(minutes / 6) / 10
        return Math.round(minutes / 6.0) / 10.0;
    }

    private static String labelOfStatus(Integer code) {
        SampleStatus s = SampleStatus.ofNullable(code);
        return s == null ? null : s.getLabel();
    }

    private static String labelOfConclusion(Integer code) {
        ResultConclusion c = ResultConclusion.ofNullable(code);
        return c == null ? null : c.getLabel();
    }

    /** 参数合法性校验：非法状态 / 结论码 fail-loud（防止数据库脏值或误传静默通过） */
    private void validateSampleQuery(QuerySampleDTO q) {
        if (q == null) {
            return;
        }
        if (q.getStatus() != null && SampleStatus.ofNullable(q.getStatus()) == null) {
            throw new BizException(400, "非法的样品状态编码: " + q.getStatus());
        }
        if (q.getConclusion() != null && ResultConclusion.ofNullable(q.getConclusion()) == null) {
            throw new BizException(400, "非法的检验结论编码: " + q.getConclusion());
        }
    }
}
