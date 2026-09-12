package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lims.common.PageResult;
import com.lims.common.enums.ResultConclusion;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;
import com.lims.common.exception.BizException;
import com.lims.dto.ResultSaveDTO;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.ResultService;
import com.lims.service.judge.JudgeEngine;
import com.lims.service.judge.JudgeInput;
import com.lims.service.judge.JudgeOutcome;
import com.lims.service.result.ResultEntryPolicy;
import com.lims.vo.ResultDetailVO;
import com.lims.vo.ResultJudgeVO;
import com.lims.vo.ResultPendingVO;
import com.lims.vo.ResultSaveVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检验数据录入 + 自动判定服务实现（T-601）。
 *
 * <p><b>职责边界（重要）</b>：本类只负责「编排」——读快照、调引擎、落结果、流转状态；
 * <b>判定语义全部在 {@link JudgeEngine}</b>（纯函数，无 IO）。二者分离使判定矩阵可被单测
 * 用构造数据穷举覆盖，而编排逻辑可用 Mock 验证。</p>
 *
 * <p><b>原始值 / 派生值分层</b>（ALCOA+ 落地，见 docs/knowledge/2026-09-12-judge-engine-research.md）：
 * {@code test_value} 是检验员录入的原始值，{@code conclusion} / {@code judge_basis} 是引擎派生值。
 * 保存是「覆盖式 upsert」——一个检测单项恒对应一行结果（唯一键 sample_item_id），
 * 修订由审计字段（updated_by/updated_at）留痕，不产生第二行。</p>
 *
 * <p><b>状态流转</b>：进入本域要求 S40（已安排）；首次保存流转 S40→S50（检验中）；
 * 提交（全部录齐）流转 S50→S60（检验完成）。全程经
 * {@link SampleStatusTransition#assertTransition} 白名单 + 乐观条件 UPDATE 双保险。</p>
 */
@Service
@RequiredArgsConstructor
public class ResultServiceImpl extends ServiceImpl<SampleResultMapper, SampleResult> implements ResultService {

    private final SampleMapper sampleMapper;
    private final SampleItemMapper sampleItemMapper;
    private final JudgeEngine judgeEngine;

    /** 允许录入的状态集合：已安排（尚未录）/ 检验中（录入未齐） */
    private static final Set<SampleStatus> ENTRY_STATUSES = Set.of(SampleStatus.S40, SampleStatus.S50);

    private static final int REFERENCE_YES = 1;

    // =========================================================================
    // 6.2 待录入列表 / 录入明细
    // =========================================================================

    @Override
    public PageResult<ResultPendingVO> pagePending(long pageNum, long pageSize, String sampleNo, String sampleName) {
        Page<Sample> page = new Page<>(pageNum, pageSize);
        Page<Sample> result = sampleMapper.selectPage(page, new LambdaQueryWrapper<Sample>()
                .in(Sample::getStatus, ENTRY_STATUSES)
                // 只列出「有检测单项」的样品（无明细的样品无从录入）
                .inSql(Sample::getId, "SELECT DISTINCT sample_id FROM sample_item WHERE deleted = 0")
                .like(StringUtils.hasText(sampleNo), Sample::getSampleNo, sampleNo)
                .like(StringUtils.hasText(sampleName), Sample::getSampleName, sampleName)
                .orderByDesc(Sample::getId));

        List<Long> ids = result.getRecords().stream().map(Sample::getId).toList();
        Map<Long, List<SampleItem>> itemsBySample = itemsBySample(ids);
        Map<Long, Map<Long, SampleResult>> resultsBySample = resultsBySample(ids);

        List<ResultPendingVO> rows = result.getRecords().stream().map(s -> {
            List<SampleItem> items = itemsBySample.getOrDefault(s.getId(), Collections.emptyList());
            Map<Long, SampleResult> results = resultsBySample.getOrDefault(s.getId(), Collections.emptyMap());
            ResultPendingVO vo = new ResultPendingVO();
            vo.setId(s.getId());
            vo.setSampleNo(s.getSampleNo());
            vo.setSampleName(s.getSampleName());
            vo.setClientName(s.getClientName());
            vo.setTaskNo(s.getTaskNo());
            vo.setTaskBatchNo(s.getTaskBatchNo());
            vo.setInspectType(s.getInspectType());
            vo.setSamplingDate(s.getSamplingDate());
            vo.setStatus(s.getStatus() == null ? null : s.getStatus().getCode());
            vo.setStatusLabel(s.getStatusLabel());
            vo.setItemTotal(items.size());
            vo.setEnteredCount(countEntered(items, results));
            vo.setAbnormalCount(countAbnormal(items, results));
            ResultConclusion conclusion = s.getConclusion();
            vo.setConclusion(conclusion == null ? null : conclusion.getCode());
            vo.setConclusionLabel(s.getConclusionLabel());
            return vo;
        }).toList();

        PageResult<ResultPendingVO> out = new PageResult<>();
        out.setRecords(rows);
        out.setTotal(result.getTotal());
        out.setCurrent(result.getCurrent());
        out.setSize(result.getSize());
        return out;
    }

    @Override
    public ResultDetailVO detail(Long sampleId) {
        Sample sample = requireSample(sampleId);
        List<SampleItem> items = listItems(sampleId);
        Map<Long, SampleResult> results = resultsByItem(sampleId);

        ResultDetailVO vo = new ResultDetailVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setSampleName(sample.getSampleName());
        vo.setClientName(sample.getClientName());
        vo.setStatus(sample.getStatus() == null ? null : sample.getStatus().getCode());
        vo.setStatusLabel(sample.getStatusLabel());
        vo.setItemTotal(items.size());
        vo.setEnteredCount(countEntered(items, results));
        // 整体结论为派生值：GET 时按当前结果实时重算，不写库（保持读接口无副作用）
        ResultConclusion overall = computeOverall(items, results);
        vo.setConclusion(overall.getCode());
        vo.setConclusionLabel(overall.getLabel());
        vo.setAllowEdit(sample.getStatus() != null && ENTRY_STATUSES.contains(sample.getStatus()));
        vo.setItems(items.stream().map(i -> toDetailItem(i, results.get(i.getId()))).toList());
        return vo;
    }

    // =========================================================================
    // 6.3 实时判定预览
    // =========================================================================

    @Override
    public ResultJudgeVO judgePreview(Long itemId, String testValue, Integer manualConclusion) {
        SampleItem item = requireItem(itemId);
        JudgeOutcome outcome = judgeEngine.judge(new JudgeInput(
                item.getJudgeType(), item.getStdValue(), item.getLowerLimit(), testValue, manualConclusion));

        ResultJudgeVO vo = new ResultJudgeVO();
        vo.setItemId(item.getId());
        vo.setItemName(item.getItemName());
        vo.setUnit(item.getUnit());
        vo.setStdValue(item.getStdValue());
        vo.setLowerLimit(item.getLowerLimit());
        vo.setJudgeType(item.getJudgeType());
        vo.setTestValue(testValue);
        vo.setConclusion(outcome.conclusion().getCode());
        vo.setConclusionLabel(outcome.conclusion().getLabel());
        vo.setConclusionSource(outcome.source().getCode());
        vo.setConclusionSourceLabel(outcome.source().getLabel());
        vo.setJudgeBasis(outcome.basis());
        return vo;
    }

    // =========================================================================
    // 6.4 保存录入
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultSaveVO save(ResultSaveDTO dto) {
        Sample sample = requireSample(dto.getSampleId());
        requireEntryStatus(sample, "录入检验结果");

        List<SampleItem> items = listItems(sample.getId());
        if (items.isEmpty()) {
            throw new BizException(400, "该样品尚未完成项目分解，无检测单项可录入");
        }
        Map<Long, SampleItem> itemMap = items.stream()
                .collect(Collectors.toMap(SampleItem::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        for (ResultSaveDTO.Item in : dto.getItems()) {
            if (!itemMap.containsKey(in.getItemId())) {
                throw new BizException(400, "检测单项不属于该样品: itemId=" + in.getItemId());
            }
        }

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();

        List<ResultSaveVO.Item> savedItems = new ArrayList<>();
        for (ResultSaveDTO.Item in : dto.getItems()) {
            SampleItem item = itemMap.get(in.getItemId());
            JudgeOutcome outcome = judgeEngine.judge(new JudgeInput(
                    item.getJudgeType(), item.getStdValue(), item.getLowerLimit(),
                    in.getTestValue(), in.getManualConclusion()));
            upsertResult(sample, item, in, outcome, operator, now);
            savedItems.add(toSaveItem(item, in.getTestValue(), outcome));
        }

        // 首次录入：S40 → S50（校验 + 乐观 UPDATE）
        int status = advanceToInputting(sample);
        ResultConclusion overall = recomputeAndPersistConclusion(sample);
        return buildSaveVO(sample, status, overall, savedItems);
    }

    // =========================================================================
    // 6.5 提交（全部录齐 → S60）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResultSaveVO submit(Long sampleId) {
        Sample sample = requireSample(sampleId);
        requireEntryStatus(sample, "提交检验结果");

        List<SampleItem> items = listItems(sampleId);
        if (items.isEmpty()) {
            throw new BizException(400, "该样品尚无检测单项，无法提交");
        }
        Map<Long, SampleResult> results = resultsByItem(sampleId);
        // T-912 定稿口径：空值行不算「已录入」（是操作缺漏，必须补录）；与「待判定」严格区分
        long missing = items.stream()
                .filter(i -> !ResultEntryPolicy.isEntered(i.getJudgeType(), results.get(i.getId())))
                .count();
        if (missing > 0) {
            throw new BizException(400, "仍有 " + missing + " 个检测单项未录入结果，请先完成录入");
        }

        ResultConclusion overall = computeOverall(items, results);

        // S40（尚未首次保存，直接一次性录齐）→ 先补 S50，再 S50→S60，两步都走白名单
        advanceToInputting(sample);

        SampleStatusTransition.assertTransition(SampleStatus.S50, SampleStatus.S60);
        Sample upd = new Sample();
        upd.setStatus(SampleStatus.S60);
        upd.setConclusion(overall);
        int updated = sampleMapper.update(upd, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sampleId)
                .eq(Sample::getStatus, SampleStatus.S50));
        if (updated == 0) {
            throw new BizException(400, "样品状态已变更，请刷新后重试");
        }

        List<ResultSaveVO.Item> itemVOs = items.stream()
                .map(i -> toSaveItem(i, results.get(i.getId()).getTestValue(),
                        new JudgeOutcome(results.get(i.getId()).getConclusion(),
                                results.get(i.getId()).getConclusionSource(),
                                results.get(i.getId()).getJudgeBasis(), false)))
                .toList();
        return buildSaveVO(sample, SampleStatus.S60.getCode(), overall, itemVOs);
    }

    // =========================================================================
    // 状态流转
    // =========================================================================

    /**
     * 首次录入时把样品从 S40（已安排）推进到 S50（检验中）。
     *
     * <p>若已是 S50 则原样返回；乐观条件 UPDATE 保证并发下不会跳态。</p>
     *
     * @return 推进后的状态 code
     */
    private int advanceToInputting(Sample sample) {
        if (sample.getStatus() == SampleStatus.S50) {
            return SampleStatus.S50.getCode();
        }
        SampleStatusTransition.assertTransition(SampleStatus.S40, SampleStatus.S50);

        Sample upd = new Sample();
        upd.setStatus(SampleStatus.S50);
        int updated = sampleMapper.update(upd, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId())
                .eq(Sample::getStatus, SampleStatus.S40));
        if (updated == 0) {
            // 并发：他人已推进 → 重新读取确认；仍非 S50 则视为冲突
            Sample fresh = sampleMapper.selectById(sample.getId());
            if (fresh == null || fresh.getStatus() != SampleStatus.S50) {
                throw new BizException(400, "样品状态已变更，请刷新后重试");
            }
        }
        sample.setStatus(SampleStatus.S50);
        return SampleStatus.S50.getCode();
    }

    // =========================================================================
    // 结果读写
    // =========================================================================

    /**
     * 覆盖式 upsert 单项结果：一个检测单项恒对应一行（唯一键 sample_item_id）。
     *
     * <p>{@code enteredBy/enteredAt} 记为「当前值的录入人与时间」（原始值的归属）；
     * 修订历史由审计字段 {@code updatedBy/updatedAt}（由 AuditMetaObjectHandler 自动填充）表达。</p>
     */
    private void upsertResult(Sample sample, SampleItem item, ResultSaveDTO.Item in,
                              JudgeOutcome outcome, String operator, LocalDateTime now) {
        SampleResult existing = baseMapper.selectOne(new LambdaQueryWrapper<SampleResult>()
                .eq(SampleResult::getSampleItemId, item.getId())
                .last("LIMIT 1"));

        if (existing == null) {
            SampleResult r = new SampleResult();
            r.setSampleId(sample.getId());
            r.setSampleItemId(item.getId());
            r.setSampleNo(sample.getSampleNo());
            r.setItemOrder(item.getItemOrder());
            r.setItemName(item.getItemName());
            r.setTestValue(in.getTestValue());
            r.setConclusion(outcome.conclusion());
            r.setConclusionSource(outcome.source());
            r.setJudgeBasis(outcome.basis());
            r.setEnteredBy(operator);
            r.setEnteredAt(now);
            r.setRemark(in.getRemark());
            baseMapper.insert(r);
            return;
        }

        SampleResult upd = new SampleResult();
        upd.setId(existing.getId());
        upd.setTestValue(in.getTestValue());
        upd.setConclusion(outcome.conclusion());
        upd.setConclusionSource(outcome.source());
        upd.setJudgeBasis(outcome.basis());
        upd.setEnteredBy(operator);
        upd.setEnteredAt(now);
        upd.setRemark(in.getRemark());
        // updatedBy / updatedAt 由 AuditMetaObjectHandler 填充（实体式 update 会触发 updateFill）
        baseMapper.update(upd, new LambdaUpdateWrapper<SampleResult>()
                .eq(SampleResult::getId, existing.getId()));
    }

    /**
     * 重算并持久化样品整体结论。
     *
     * <p>落实 AGENTS 7.3 规则 6 + 白名单 D3：参考项单项结论照常计算但不计入整体；
     * 全参考项样品整体 = 待判定（禁止自动判合格）。</p>
     */
    private ResultConclusion recomputeAndPersistConclusion(Sample sample) {
        List<SampleItem> items = listItems(sample.getId());
        ResultConclusion overall = computeOverall(items, resultsByItem(sample.getId()));

        Sample upd = new Sample();
        upd.setConclusion(overall);
        sampleMapper.update(upd, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sample.getId()));
        sample.setConclusion(overall);
        return overall;
    }

    /**
     * 整体结论聚合（纯函数）。
     *
     * <pre>
     * 存在未录入项                      → 待判定
     * 无非参考项（全部是参考项）          → 待判定（D3 补充，禁自动合格）
     * 存在非参考项不合格                → 不合格
     * 存在非参考项待判定                → 待判定
     * 其余（非参考项全部合格）           → 合格
     * </pre>
     */
    private ResultConclusion computeOverall(List<SampleItem> items, Map<Long, SampleResult> results) {
        List<SampleItem> nonReference = items.stream().filter(i -> !isReference(i)).toList();
        if (nonReference.isEmpty()) {
            // 全为参考项：整体结论交给人工（参考项不作放行依据）
            return ResultConclusion.PENDING;
        }
        boolean anyUnqualified = false;
        boolean anyPending = false;
        for (SampleItem item : nonReference) {
            SampleResult r = results.get(item.getId());
            // 未有效录入（无结果行 / 空值行）→ 未录齐 → 待判定
            if (!ResultEntryPolicy.isEntered(item.getJudgeType(), r)) {
                return ResultConclusion.PENDING;
            }
            ResultConclusion c = r.getConclusion();
            if (c == null || c == ResultConclusion.PENDING) {
                anyPending = true;
            } else if (c == ResultConclusion.UNQUALIFIED) {
                anyUnqualified = true;
            }
        }
        if (anyUnqualified) {
            return ResultConclusion.UNQUALIFIED;
        }
        return anyPending ? ResultConclusion.PENDING : ResultConclusion.QUALIFIED;
    }

    private boolean isReference(SampleItem item) {
        return Objects.equals(item.getIsReference(), REFERENCE_YES);
    }

    /** 已**有效录入**项数（T-912 口径：空值行不计入） */
    private int countEntered(List<SampleItem> items, Map<Long, SampleResult> results) {
        return (int) items.stream()
                .filter(i -> ResultEntryPolicy.isEntered(i.getJudgeType(), results.get(i.getId())))
                .count();
    }

    /** 异常项数 = 未有效录入 + 已录入但结论为待判定（供审核页展示与放行红线） */
    private int countAbnormal(List<SampleItem> items, Map<Long, SampleResult> results) {
        int count = 0;
        for (SampleItem item : items) {
            SampleResult r = results.get(item.getId());
            if (!ResultEntryPolicy.isEntered(item.getJudgeType(), r)) {
                count++;
            } else if (r.getConclusion() == null || r.getConclusion() == ResultConclusion.PENDING) {
                count++;
            }
        }
        return count;
    }

    private Map<Long, SampleResult> resultsByItem(Long sampleId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                        .eq(SampleResult::getSampleId, sampleId))
                .stream()
                .filter(r -> r.getSampleItemId() != null)
                .collect(Collectors.toMap(SampleResult::getSampleItemId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, List<SampleItem>> itemsBySample(List<Long> sampleIds) {
        if (sampleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                        .in(SampleItem::getSampleId, sampleIds))
                .stream()
                .collect(Collectors.groupingBy(SampleItem::getSampleId));
    }

    private Map<Long, Map<Long, SampleResult>> resultsBySample(List<Long> sampleIds) {
        if (sampleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Map<Long, SampleResult>> out = new LinkedHashMap<>();
        baseMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                        .in(SampleResult::getSampleId, sampleIds))
                .forEach(r -> out.computeIfAbsent(r.getSampleId(), k -> new LinkedHashMap<>())
                        .put(r.getSampleItemId(), r));
        return out;
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    private List<SampleItem> listItems(Long sampleId) {
        return sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, sampleId)
                .orderByAsc(SampleItem::getItemOrder));
    }

    private Sample requireSample(Long sampleId) {
        if (sampleId == null) {
            throw new BizException(400, "样品ID不能为空");
        }
        Sample sample = sampleMapper.selectById(sampleId);
        if (sample == null) {
            throw new BizException(400, "样品不存在或已删除: id=" + sampleId);
        }
        return sample;
    }

    private SampleItem requireItem(Long itemId) {
        if (itemId == null) {
            throw new BizException(400, "检测单项ID不能为空");
        }
        SampleItem item = sampleItemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(400, "检测单项不存在或已删除: id=" + itemId);
        }
        return item;
    }

    private void requireEntryStatus(Sample sample, String action) {
        if (sample.getStatus() == null || !ENTRY_STATUSES.contains(sample.getStatus())) {
            throw new BizException(400, "样品当前状态为「" + sample.getStatusLabel()
                    + "」，不允许" + action + "（要求「已安排」或「检验中」）");
        }
    }

    private ResultDetailVO.Item toDetailItem(SampleItem item, SampleResult result) {
        ResultDetailVO.Item vo = new ResultDetailVO.Item();
        vo.setId(item.getId());
        vo.setItemOrder(item.getItemOrder());
        vo.setItemName(item.getItemName());
        vo.setUnit(item.getUnit());
        vo.setBasisCode(item.getBasisCode());
        vo.setMethods(item.getMethods());
        vo.setStdValue(item.getStdValue());
        vo.setJudgeType(item.getJudgeType());
        vo.setJudgeTypeLabel(ResultEntryPolicy.judgeTypeLabel(item.getJudgeType()));
        vo.setIsReference(item.getIsReference());
        vo.setLowerLimit(item.getLowerLimit());
        vo.setTesterNo(item.getTesterNo());
        vo.setTesterName(item.getTesterName());

        boolean entered = ResultEntryPolicy.isEntered(item.getJudgeType(), result);
        vo.setEntered(entered);
        if (result != null) {
            vo.setTestValue(result.getTestValue());
            vo.setEnteredBy(result.getEnteredBy());
            vo.setEnteredAt(result.getEnteredAt());
            vo.setRemark(result.getRemark());
        }
        if (entered) {
            vo.setConclusion(result.getConclusion() == null ? null : result.getConclusion().getCode());
            vo.setConclusionLabel(result.getConclusionLabel());
            vo.setConclusionSource(result.getConclusionSource() == null
                    ? null : result.getConclusionSource().getCode());
            vo.setConclusionSourceLabel(result.getConclusionSourceLabel());
            vo.setJudgeBasis(result.getJudgeBasis());
        }
        // 未有效录入（无结果行 / 空值行）→ 不出网历史结论，前端统一显示「未录入」
        // （T-912：空值行遗留的 conclusion=3 不得伪装成「待判定」）
        return vo;
    }

    private ResultSaveVO.Item toSaveItem(SampleItem item, String testValue, JudgeOutcome outcome) {
        ResultSaveVO.Item vo = new ResultSaveVO.Item();
        vo.setItemId(item.getId());
        vo.setItemOrder(item.getItemOrder());
        vo.setItemName(item.getItemName());
        vo.setTestValue(testValue);
        vo.setConclusion(outcome.conclusion().getCode());
        vo.setConclusionLabel(outcome.conclusion().getLabel());
        vo.setConclusionSource(outcome.source().getCode());
        vo.setConclusionSourceLabel(outcome.source().getLabel());
        vo.setJudgeBasis(outcome.basis());
        return vo;
    }

    private ResultSaveVO buildSaveVO(Sample sample, int status, ResultConclusion overall,
                                     List<ResultSaveVO.Item> items) {
        ResultSaveVO vo = new ResultSaveVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setStatus(status);
        vo.setStatusLabel(SampleStatus.of(status).getLabel());
        List<SampleItem> allItems = listItems(sample.getId());
        vo.setItemTotal(allItems.size());
        vo.setEnteredCount(countEntered(allItems, resultsByItem(sample.getId())));
        vo.setConclusion(overall.getCode());
        vo.setConclusionLabel(overall.getLabel());
        vo.setItems(items);
        return vo;
    }
}
