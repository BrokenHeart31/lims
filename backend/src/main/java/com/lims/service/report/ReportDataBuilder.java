package com.lims.service.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.enums.ReportType;
import com.lims.common.enums.ResultConclusion;
import com.lims.common.exception.BizException;
import com.lims.config.ReportProperties;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.entity.SysUser;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.vo.ReportItemVO;
import com.lims.vo.ReportVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检验报告数据合成器（T-702）。
 *
 * <p><b>职责单一</b>：给定样品与报告类型，一次性聚合
 * {@code sample_info + sample_item + sample_result + sys_user} 并产出
 * {@link ReportVO} 渲染模型。它不负责状态流转、不写库——那些属于
 * {@code ReportGenerateServiceImpl}。如此拆分的好处：重打（只读）与生成（写库）
 * 共用同一套合成逻辑，报告内容永远一致。</p>
 *
 * <p><b>为什么不落报告快照</b>：S80 签发后样品进入只读（S90 为终态），
 * {@code sample_item}（标准值/依据为套库快照）与 {@code sample_result}（结果）
 * 均不再变化，实时聚合即等于事实；落快照会带来「快照与事实两处真相」的一致性风险。</p>
 */
@Component
@RequiredArgsConstructor
public class ReportDataBuilder {

    /** 报告日期统一口径：yyyy.MM.dd（与业务说明书版式一致） */
    private static final DateTimeFormatter DATE_DOT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 检测项目摘要（第 1 页信息表固定文案，明细见第 2 页） */
    private static final String ITEM_SUMMARY = "见第2页";

    /** 参考性限量项标记 */
    private static final int REFERENCE_YES = 1;

    /** 依据去重连接符 */
    private static final String BASIS_JOINER = "、";

    /** 不合格项名连接符 */
    private static final String ITEM_JOINER = "、";

    private final SampleMapper sampleMapper;
    private final SampleItemMapper sampleItemMapper;
    private final SampleResultMapper sampleResultMapper;
    private final SysUserMapper sysUserMapper;
    private final ReportProperties reportProperties;

    /**
     * 合成报告渲染模型。
     *
     * @param sampleId   样品ID
     * @param reportType 报告类型（可空 → 默认 CMA）
     * @return 完整报告 VO
     */
    public ReportVO build(Long sampleId, ReportType reportType) {
        Sample sample = sampleMapper.selectById(sampleId);
        if (sample == null) {
            throw new BizException(400, "样品不存在或已删除: id=" + sampleId);
        }
        ReportType type = reportType == null ? ReportType.CMA : reportType;

        List<SampleItem> items = sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, sampleId)
                .orderByAsc(SampleItem::getItemOrder));
        Map<Long, SampleResult> results = resultsByItemId(sampleId);

        // 编制人 = 该样品最早的录入人（sample_result 中 entered_at 最早者）
        SampleResult earliest = earliestResult(sampleId);
        String editBy = earliest == null ? null : earliest.getEnteredBy();

        // 一次性批量取三位签署人（避免逐人查库）
        Map<String, SysUser> users = loadUsers(sample.getSignBy(), sample.getAuditBy(), editBy);

        String basisText = buildBasisText(items);

        ReportVO vo = new ReportVO();
        // 报告标识
        vo.setReportType(type.getCode());
        vo.setReportTypeLabel(type.getLabel());
        vo.setReportNo(sample.getSampleNo());
        vo.setQualificationLines(buildQualificationLines(type));

        // 机构信息
        vo.setOrgName(reportProperties.getOrgName());
        vo.setAddress(reportProperties.getAddress());
        vo.setPhone(reportProperties.getPhone());
        vo.setPostcode(reportProperties.getPostcode());
        vo.setFax(reportProperties.getFax());
        vo.setNotes(new ArrayList<>(reportProperties.getNotes()));

        // 封面三要素
        vo.setProductName(sample.getSampleName());
        vo.setClientName(sample.getClientName());
        vo.setInspectType(sample.getInspectType());

        // 第 1 页信息表
        vo.setSpec(sample.getSpec());
        vo.setBrand(sample.getBrand());
        vo.setManufacturer(sample.getManufacturer());
        vo.setGrade(sample.getGrade());
        vo.setSamplingAddress(sample.getSamplingAddress());
        vo.setSamplingDate(formatDot(sample.getSamplingDate() == null ? null : sample.getSamplingDate().atStartOfDay()));
        vo.setSampleQuantity(sample.getSampleQuantity());
        vo.setSampler(sample.getSampler());
        vo.setSamplingBase(sample.getSamplingBase());
        vo.setOriginalNo(sample.getOriginalNo());
        vo.setSampleState(sample.getSampleState());
        vo.setItemSummary(ITEM_SUMMARY);
        vo.setBasisText(basisText);
        vo.setInspectDate(formatDot(inspectDate(sampleId)));
        vo.setConclusionText(buildConclusionText(items, results, basisText));
        vo.setInstrument(null);
        vo.setEnvironment(reportProperties.getEnvironment());
        vo.setRemark(sample.getRemark());

        // 签署
        vo.setSignAt(formatDot(sample.getSignAt()));
        SysUser approver = users.get(sample.getSignBy());
        SysUser auditor = users.get(sample.getAuditBy());
        SysUser editor = users.get(editBy);
        vo.setApproveName(nicknameOf(approver));
        vo.setAuditName(nicknameOf(auditor));
        vo.setEditName(nicknameOf(editor));
        vo.setApproveSignatureUrl(signatureOf(approver));
        vo.setAuditSignatureUrl(signatureOf(auditor));
        vo.setEditSignatureUrl(signatureOf(editor));

        // 第 2 页明细
        vo.setItems(items.stream().map(item -> toItemVO(item, results.get(item.getId()))).toList());
        return vo;
    }

    // =========================================================================
    // 明细合成
    // =========================================================================

    private ReportItemVO toItemVO(SampleItem item, SampleResult result) {
        ReportItemVO vo = new ReportItemVO();
        vo.setItemOrder(item.getItemOrder());
        boolean reference = isReference(item);
        vo.setItemName(reference ? "*" + item.getItemName() : item.getItemName());
        vo.setTestValue(result == null ? null : result.getTestValue());
        vo.setBasisCode(item.getBasisCode());
        vo.setStdValue(item.getStdValue());
        vo.setUnit(item.getUnit());
        vo.setLowerLimit(item.getLowerLimit());
        vo.setIsReference(item.getIsReference());

        ResultConclusion conclusion = result == null ? null : result.getConclusion();
        vo.setConclusionText(conclusion == null ? null : conclusion.getLabel());
        vo.setConclusionCode(conclusion == null ? null : conclusion.getCode());
        return vo;
    }

    /**
     * 生成检验结论整句（业务说明书句式）。
     *
     * <p><b>判定优先级（禁止静默判合格）</b>：
     * <ol>
     *   <li>存在非参考项不合格 → 不合格句式，并列出超标项名（顿号连接）；</li>
     *   <li>否则若存在非参考项「待判定」或未有结论（null）→ 待判定句式，提示需人工确认；</li>
     *   <li>否则（非参考项全部合格）→ 合格句式。</li>
     * </ol>
     * 第 2 条优先于「含待判定判合格」的宽松口径——食品检验场景下把「待判定」写成
     * 合格是最危险的失效模式，故存在任何不可判定项一律如实说明、绝不判合格。</p>
     *
     * <p>结论中文一律取自 {@link ResultConclusion#getLabel()}，不硬编码。</p>
     */
    private String buildConclusionText(List<SampleItem> items, Map<Long, SampleResult> results, String basisText) {
        String basis = StringUtils.hasText(basisText) ? basisText : "相关标准";
        String prefix = "本样品所检项目按" + basis + "规定的要求进行判定，";

        List<String> unqualifiedNames = new ArrayList<>();
        boolean indeterminate = false;
        for (SampleItem item : items) {
            if (isReference(item)) {
                continue;
            }
            SampleResult result = results.get(item.getId());
            ResultConclusion conclusion = result == null ? null : result.getConclusion();
            if (conclusion == ResultConclusion.UNQUALIFIED) {
                unqualifiedNames.add(item.getItemName());
            } else if (conclusion != ResultConclusion.QUALIFIED) {
                // 待判定（PENDING）或未出结论（null）——均不可判合格
                indeterminate = true;
            }
        }

        if (!unqualifiedNames.isEmpty()) {
            return prefix + "其中" + String.join(ITEM_JOINER, unqualifiedNames) + "超标，判定为"
                    + ResultConclusion.UNQUALIFIED.getLabel() + "。";
        }
        if (indeterminate) {
            return prefix + "判定为" + ResultConclusion.PENDING.getLabel() + "，需人工确认。";
        }
        return prefix + "判定为" + ResultConclusion.QUALIFIED.getLabel() + "。";
    }

    /** 检测依据：该样品全部 basis_code 去重（保持项次顺序）、顿号连接 */
    private String buildBasisText(List<SampleItem> items) {
        return items.stream()
                .map(SampleItem::getBasisCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.joining(BASIS_JOINER));
    }

    /** 封面资质行：CMA 1 行、CMA-CATL 2 行（文案取自 ReportProperties 证书编号） */
    private List<String> buildQualificationLines(ReportType type) {
        List<String> lines = new ArrayList<>(2);
        lines.add("资质认定证书编号：" + reportProperties.getCmaNo());
        if (type == ReportType.CMA_CATL) {
            lines.add("农产品质量安全检测机构考核合格证书编号：" + reportProperties.getCatlNo());
        }
        return lines;
    }

    // =========================================================================
    // 查询辅助
    // =========================================================================

    private Map<Long, SampleResult> resultsByItemId(Long sampleId) {
        return sampleResultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                        .eq(SampleResult::getSampleId, sampleId))
                .stream()
                .filter(r -> r.getSampleItemId() != null)
                .collect(Collectors.toMap(SampleResult::getSampleItemId, Function.identity(), (a, b) -> a));
    }

    /** 检验日期：全部结果行中最晚的录入时间（即检测完成时点）；无结果时返回 null */
    private LocalDateTime inspectDate(Long sampleId) {
        return sampleResultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                        .eq(SampleResult::getSampleId, sampleId))
                .stream()
                .map(SampleResult::getEnteredAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    /** 最早录入的结果行（用于定位编制人）；无有效录入人时返回 null */
    private SampleResult earliestResult(Long sampleId) {
        return sampleResultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                        .eq(SampleResult::getSampleId, sampleId))
                .stream()
                .filter(r -> StringUtils.hasText(r.getEnteredBy()))
                .min(Comparator.comparing(SampleResult::getEnteredAt,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SampleResult::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private Map<String, SysUser> loadUsers(String... usernames) {
        Set<String> set = Arrays.stream(usernames)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (set.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, SysUser> map = new LinkedHashMap<>();
        sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>().in(SysUser::getUsername, set))
                .forEach(u -> map.putIfAbsent(u.getUsername(), u));
        return map;
    }

    private String nicknameOf(SysUser user) {
        return user == null ? null : user.getNickname();
    }

    private String signatureOf(SysUser user) {
        return user == null ? null : user.getSignatureUrl();
    }

    private boolean isReference(SampleItem item) {
        return item.getIsReference() != null && item.getIsReference() == REFERENCE_YES;
    }

    private String formatDot(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.format(DATE_DOT);
    }
}
