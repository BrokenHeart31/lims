package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;
import com.lims.common.exception.BizException;
import com.lims.dto.ItemSaveDTO;
import com.lims.entity.ProductLib;
import com.lims.entity.ProductLibItem;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.mapper.ProductLibItemMapper;
import com.lims.mapper.ProductLibMapper;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.service.ItemService;
import com.lims.vo.ItemMatchVO;
import com.lims.vo.ItemPendingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目分解服务实现（T-401）。
 *
 * <p><b>核心流程</b>：套库预览（不落库）→ 人工调整 → 覆盖式保存 → 确认流转 S20→S30。</p>
 *
 * <p><b>设计要点</b>：
 * <ol>
 *   <li>套库只生成初稿不落库——说明书要求分解结果可人工增删，最终结果才是业务数据；</li>
 *   <li>标准库字段快照下沉到 sample_item，T-601 判定引擎只读本表不回溯标准库；</li>
 *   <li>保存为覆盖式（先逻辑删除旧明细再全量重建），避免增量同步歧义；</li>
 *   <li>状态流转经 SampleStatusTransition 白名单 + 乐观条件 UPDATE 防并发双击跳态。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class ItemServiceImpl extends ServiceImpl<SampleItemMapper, SampleItem> implements ItemService {

    private static final Set<Integer> VALID_JUDGE_TYPES = Set.of(1, 2, 3);
    private static final Set<Integer> VALID_REFERENCE_FLAGS = Set.of(0, 1);
    private static final Set<Integer> VALID_SOURCE_TYPES = Set.of(1, 2);

    private final SampleMapper sampleMapper;
    private final ProductLibMapper productLibMapper;
    private final ProductLibItemMapper productLibItemMapper;

    // =========================================================================
    // 4.2 套库预览
    // =========================================================================

    @Override
    public ItemMatchVO match(Long sampleId) {
        Sample sample = requireSample(sampleId);

        ItemMatchVO vo = new ItemMatchVO();
        vo.setSampleId(sampleId);
        vo.setSampleName(sample.getSampleName());
        vo.setCandidates(new ArrayList<>());
        vo.setItems(new ArrayList<>());

        String sampleName = sample.getSampleName();
        if (!StringUtils.hasText(sampleName)) {
            vo.setMatched(false);
            return vo;
        }

        // 匹配键：sample_info.sample_name = product_lib.product_name（TRIM 后精确匹配）
        List<ProductLib> libs = productLibMapper.selectList(
                new LambdaQueryWrapper<ProductLib>()
                        .apply("TRIM(product_name) = {0}", sampleName.trim())
                        .orderByAsc(ProductLib::getId));

        if (libs.isEmpty()) {
            vo.setMatched(false);
            return vo;
        }

        // 命中多条：取 id 最小者，并把全部候选返回供前端提示
        ProductLib hit = libs.get(0);
        vo.setMatched(true);
        vo.setMatchedLibId(hit.getId());
        vo.setMatchedProductName(hit.getProductName());
        if (libs.size() > 1) {
            vo.setCandidates(libs.stream()
                    .map(l -> new ItemMatchVO.Candidate(l.getId(), l.getProductName(), l.getCategory()))
                    .toList());
        }

        // 取标准库明细（按 item_order 升序）作为初稿
        List<ProductLibItem> libItems = productLibItemMapper.selectList(
                new LambdaQueryWrapper<ProductLibItem>()
                        .eq(ProductLibItem::getProductLibId, hit.getId())
                        .orderByAsc(ProductLibItem::getItemOrder));

        vo.setItems(libItems.stream().map(this::toMatchedItem).toList());
        return vo;
    }

    private ItemMatchVO.MatchedItem toMatchedItem(ProductLibItem src) {
        ItemMatchVO.MatchedItem it = new ItemMatchVO.MatchedItem();
        it.setItemOrder(src.getItemOrder());
        it.setItemName(src.getItemName());
        it.setLibItemId(src.getId());
        it.setUnit(src.getUnit());
        it.setBasisCode(src.getBasisCode());
        it.setMethods(src.getMethods());
        it.setStdValue(src.getStdValue());
        it.setJudgeType(src.getJudgeType() == null ? 1 : src.getJudgeType());
        it.setIsReference(src.getIsReference() == null ? 0 : src.getIsReference());
        it.setLowerLimit(src.getLowerLimit());
        it.setMethodNote(src.getMethodNote());
        return it;
    }

    // =========================================================================
    // 4.3 查询已保存明细
    // =========================================================================

    @Override
    public List<SampleItem> listBySampleId(Long sampleId) {
        requireSample(sampleId);
        return baseMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, sampleId)
                .orderByAsc(SampleItem::getItemOrder));
    }

    // =========================================================================
    // 4.4 保存分解（覆盖式）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveDecompose(ItemSaveDTO dto) {
        Sample sample = requireSample(dto.getSampleId());
        requireStatus(sample, SampleStatus.S20, "保存分解");

        List<ItemSaveDTO.Item> items = dto.getItems();
        if (items == null || items.isEmpty()) {
            throw new BizException(400, "请至少保留一个检验项目");
        }

        // 项次在样品内唯一（后端不自动重排，重复即报错——契约 4.4）
        Set<Integer> orders = new HashSet<>();
        for (ItemSaveDTO.Item it : items) {
            if (it.getItemOrder() == null) {
                throw new BizException(400, "项次不能为空");
            }
            if (!orders.add(it.getItemOrder())) {
                throw new BizException(400, "项次重复：" + it.getItemOrder() + "，请检查后再保存");
            }
            if (!StringUtils.hasText(it.getItemName())) {
                throw new BizException(400, "第 " + it.getItemOrder() + " 项的检验项目名称不能为空");
            }
            if (it.getJudgeType() == null || !VALID_JUDGE_TYPES.contains(it.getJudgeType())) {
                throw new BizException(400, "第 " + it.getItemOrder() + " 项的判定类型非法（仅允许 1/2/3）");
            }
            if (it.getIsReference() == null || !VALID_REFERENCE_FLAGS.contains(it.getIsReference())) {
                throw new BizException(400, "第 " + it.getItemOrder() + " 项的参考性标记非法（仅允许 0/1）");
            }
            if (it.getSourceType() == null || !VALID_SOURCE_TYPES.contains(it.getSourceType())) {
                throw new BizException(400, "第 " + it.getItemOrder() + " 项的来源类型非法（仅允许 1/2）");
            }
        }

        // 覆盖式：先逻辑删除该样品全部旧明细，再全量重建
        baseMapper.delete(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, dto.getSampleId()));

        for (ItemSaveDTO.Item it : items) {
            SampleItem entity = new SampleItem();
            entity.setSampleId(sample.getId());
            entity.setSampleNo(sample.getSampleNo());
            entity.setItemOrder(it.getItemOrder());
            entity.setItemName(it.getItemName().trim());
            entity.setLibItemId(it.getLibItemId());
            entity.setUnit(trimToNull(it.getUnit()));
            entity.setBasisCode(trimToNull(it.getBasisCode()));
            entity.setMethods(trimToNull(it.getMethods()));
            entity.setStdValue(trimToNull(it.getStdValue()));
            entity.setJudgeType(it.getJudgeType());
            entity.setIsReference(it.getIsReference());
            entity.setLowerLimit(trimToNull(it.getLowerLimit()));
            entity.setMethodNote(trimToNull(it.getMethodNote()));
            entity.setSourceType(it.getSourceType());
            entity.setRemark(trimToNull(it.getRemark()));
            baseMapper.insert(entity);
        }
        return items.size();
    }

    // =========================================================================
    // 4.5 分解确认 S20 → S30
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirm(Long sampleId) {
        Sample sample = requireSample(sampleId);
        requireStatus(sample, SampleStatus.S20, "分解确认");

        Long itemCount = baseMapper.selectCount(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, sampleId));
        if (itemCount == null || itemCount == 0) {
            throw new BizException(400, "请先完成项目分解再确认");
        }

        // 状态机白名单校验（AGENTS 7.2 / 0.2 唯一入口）
        SampleStatusTransition.assertTransition(SampleStatus.S20, SampleStatus.S30);

        // 乐观条件 UPDATE 防并发双击跳态
        int updated = sampleMapper.update(null, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sampleId)
                .eq(Sample::getStatus, SampleStatus.S20)
                .set(Sample::getStatus, SampleStatus.S30));
        if (updated == 0) {
            throw new BizException(400, "样品状态已变更，请刷新后重试");
        }
        return SampleStatus.S30.getCode();
    }

    // =========================================================================
    // 4.6 分页查询待分解样品
    // =========================================================================

    @Override
    public Page<ItemPendingVO> pagePending(long pageNum, long pageSize, String sampleNo, String sampleName) {
        Page<Sample> page = new Page<>(pageNum, pageSize);
        Page<Sample> result = sampleMapper.selectPage(page, new LambdaQueryWrapper<Sample>()
                .eq(Sample::getStatus, SampleStatus.S20)
                .like(StringUtils.hasText(sampleNo), Sample::getSampleNo, sampleNo)
                .like(StringUtils.hasText(sampleName), Sample::getSampleName, sampleName)
                .orderByDesc(Sample::getId));

        // 一次性统计本页各样品已保存明细数，避免 N+1
        List<Long> ids = result.getRecords().stream().map(Sample::getId).toList();
        Map<Long, Long> countMap = ids.isEmpty() ? Collections.emptyMap()
                : baseMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                        .in(SampleItem::getSampleId, ids))
                .stream()
                .collect(Collectors.groupingBy(SampleItem::getSampleId, Collectors.counting()));

        List<ItemPendingVO> rows = result.getRecords().stream().map(s -> {
            ItemPendingVO vo = new ItemPendingVO();
            vo.setId(s.getId());
            vo.setSampleNo(s.getSampleNo());
            vo.setSampleName(s.getSampleName());
            vo.setClientName(s.getClientName());
            vo.setTaskNo(s.getTaskNo());
            vo.setTaskBatchNo(s.getTaskBatchNo());
            vo.setStatus(s.getStatus() == null ? null : s.getStatus().getCode());
            vo.setStatusLabel(s.getStatusLabel());
            vo.setSamplingDate(s.getSamplingDate());
            vo.setInspectType(s.getInspectType());
            vo.setItemCount(countMap.getOrDefault(s.getId(), 0L).intValue());
            return vo;
        }).toList();

        Page<ItemPendingVO> out = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        out.setRecords(rows);
        return out;
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

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

    private void requireStatus(Sample sample, SampleStatus expected, String action) {
        if (sample.getStatus() != expected) {
            throw new BizException(400, "样品当前状态为「" + sample.getStatusLabel()
                    + "」，不允许" + action + "（要求「" + expected.getLabel() + "」）");
        }
    }

    private String trimToNull(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        return s.trim();
    }
}
