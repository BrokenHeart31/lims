package com.lims.service.rollback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.enums.ArchiveTarget;
import com.lims.common.enums.SampleStatus;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.vo.RollbackPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 回退下游失效清单计算器（**纯读**，feature B，设计 §5.1）。
 *
 * <p>供预览使用：不改库、不落任何表，只统计「这条回退会失效哪些下游数据」的计数与明细。
 * 由 {@code RollbackServiceImpl.preview} 组装成 {@link RollbackPreviewVO}。</p>
 */
@Component
@RequiredArgsConstructor
public class RollbackPlanner {

    private final SampleItemMapper sampleItemMapper;
    private final SampleResultMapper sampleResultMapper;

    /**
     * 计算下游失效清单（计数 + 明细）。
     *
     * @param sampleId 样品ID
     * @param from     回退前状态
     * @param to       回退后状态
     * @return 各类下游数据的失效预览（可能为空列表）
     */
    public List<RollbackPreviewVO.Invalidation> invalidationList(Long sampleId, SampleStatus from, SampleStatus to) {
        Set<ArchiveTarget> scope = RollbackScope.targets(null, from, to);
        List<RollbackPreviewVO.Invalidation> list = new ArrayList<>();

        // 任务指派清空（S40→S30）：不失效行，只清空指派字段
        if (RollbackScope.resetsAssignFields(scope)) {
            List<SampleItem> assigned = activeItems(sampleId).stream()
                    .filter(this::hasAssignment)
                    .toList();
            if (!assigned.isEmpty()) {
                RollbackPreviewVO.Invalidation inv = new RollbackPreviewVO.Invalidation();
                inv.setType("assign_fields");
                inv.setTypeLabel("任务指派（将被清空）");
                inv.setCount(assigned.size());
                inv.setItems(assigned.stream()
                        .map(i -> new RollbackPreviewVO.Item(i.getId(),
                                orderLabel(i) + " " + i.getItemName() + " → " + label(i.getTesterNo())))
                        .toList());
                list.add(inv);
            }
        }

        // 检测明细失效
        if (RollbackScope.invalidatesItems(scope)) {
            List<SampleItem> items = activeItems(sampleId);
            if (!items.isEmpty()) {
                RollbackPreviewVO.Invalidation inv = new RollbackPreviewVO.Invalidation();
                inv.setType("sample_item");
                inv.setTypeLabel("检测单项(分解明细)");
                inv.setCount(items.size());
                inv.setItems(items.stream()
                        .map(i -> new RollbackPreviewVO.Item(i.getId(), orderLabel(i) + " " + i.getItemName()))
                        .toList());
                list.add(inv);
            }
        }

        // 检验结果失效
        if (RollbackScope.invalidatesResults(scope)) {
            List<SampleResult> results = activeResults(sampleId);
            if (!results.isEmpty()) {
                RollbackPreviewVO.Invalidation inv = new RollbackPreviewVO.Invalidation();
                inv.setType("sample_result");
                inv.setTypeLabel("检验结果");
                inv.setCount(results.size());
                inv.setItems(results.stream()
                        .map(r -> new RollbackPreviewVO.Item(r.getId(),
                                orderLabelOf(r.getItemOrder()) + " " + r.getItemName() + " " + nullToDash(r.getTestValue())))
                        .toList());
                list.add(inv);
            }
        }
        return list;
    }

    // ------------------------------------------------------------------ 内部工具

    private List<SampleItem> activeItems(Long sampleId) {
        return sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, sampleId)
                .orderByAsc(SampleItem::getItemOrder));
    }

    private List<SampleResult> activeResults(Long sampleId) {
        return sampleResultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                .eq(SampleResult::getSampleId, sampleId)
                .orderByAsc(SampleResult::getItemOrder));
    }

    private boolean hasAssignment(SampleItem item) {
        return item.getTesterNo() != null
                || !Objects.equals(item.getAssignStatus(), 0)
                || !Objects.equals(item.getAssignType(), 0);
    }

    private String orderLabel(SampleItem item) {
        return orderLabelOf(item.getItemOrder());
    }

    private String orderLabelOf(Integer order) {
        return order == null ? "#" : order + "";
    }

    private String label(String value) {
        return value == null ? "未指派" : value;
    }

    private String nullToDash(String value) {
        return value == null ? "-" : value;
    }
}
