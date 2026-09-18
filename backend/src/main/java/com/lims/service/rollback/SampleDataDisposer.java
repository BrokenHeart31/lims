package com.lims.service.rollback;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.common.exception.BizException;
import com.lims.entity.SampleDataArchive;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.mapper.SampleDataArchiveMapper;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 下游数据处置器（**唯一**失效 / 留档 / 恢复入口，feature B，设计 §2.8 / §5.1）。
 *
 * <p>回退 = 「状态层反向补偿」+「数据层失效/留档/恢复」。本类承接数据层三件事：</p>
 * <ol>
 *   <li><b>失效</b>（软删）：把 {@code sample_item}/{@code sample_result} 的 {@code deleted}
 *       置为**该行自身 id**（非 0）——物理行不消失，且因 id 唯一，二次失效永不撞唯一键
 *       （解 Pit 1，设计 §2.9）。</li>
 *   <li><b>留档</b>：失效前把整行 pre-image 写进 {@code sample_data_archive}（取证，只增不删）；
 *       另承接「保存前修订留档」（Pit 2：结果覆盖式 upsert 丢旧 test_value）。</li>
 *   <li><b>恢复</b>：把某次回退留档的行 {@code deleted} 复位为 0 并回填被改写字段（可再撤销）。</li>
 * </ol>
 *
 * <p><b>为什么不走 MP 的 {@code baseMapper.delete} / LambdaUpdateWrapper</b>：见
 * {@link SampleItemMapper} 类注释——MP 的 {@code @TableLogic} 只支持固定字面量，且会给
 * wrapper 型 update 追加 {@code AND deleted = 0}，无法表达「行自身 id」也无法恢复已失效行。
 * 故本类所有写操作走 Mapper 的原生 {@code @Update}。</p>
 *
 * <p><b>事务</b>：本类所有方法**必须**在调用方（{@code RollbackServiceImpl}）的事务内执行，
 * 且失效处置在状态 UPDATE 之后（先锁定状态再处置下游，设计 §10-R3）。</p>
 */
@Component
@RequiredArgsConstructor
public class SampleDataDisposer {

    /** 来源表名：检测明细 */
    public static final String TABLE_ITEM = "sample_item";
    /** 来源表名：检验结果 */
    public static final String TABLE_RESULT = "sample_result";

    /** 留档原因：回退失效 */
    public static final int REASON_ROLLBACK = 1;
    /** 留档原因：保存前修订留档 */
    public static final int REASON_REVISION = 2;

    private final SampleItemMapper sampleItemMapper;
    private final SampleResultMapper sampleResultMapper;
    private final SampleDataArchiveMapper archiveMapper;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // 结果：处置计数 + 摘要
    // =========================================================================

    /**
     * 处置结果（失效计数 + 可读摘要）。
     *
     * @param itemCount   失效的 sample_item 数
     * @param resultCount 失效的 sample_result 数
     * @param summary     人类可读摘要（无处置则为 null）
     */
    public record DispositionResult(int itemCount, int resultCount, String summary) {

        /** 累加两个处置结果（同一次回退的多步处置） */
        public DispositionResult plus(DispositionResult other) {
            if (other == null) {
                return this;
            }
            String merged = joinSummary(this.summary, other.summary);
            return new DispositionResult(this.itemCount + other.itemCount,
                    this.resultCount + other.resultCount, merged);
        }

        private static String joinSummary(String a, String b) {
            if (a == null || a.isEmpty()) {
                return b;
            }
            if (b == null || b.isEmpty()) {
                return a;
            }
            return a + "；" + b;
        }
    }

    // =========================================================================
    // 保存前修订留档（Pit 2）
    // =========================================================================

    /**
     * 保存结果前，对被覆盖的旧行做修订留档（Pit 2：覆盖式 upsert 会丢掉旧 test_value）。
     *
     * <p>仅在「真实修订」时调用（由调用方比较旧值 ≠ 新值，R4：无变化不写）。</p>
     *
     * @param existing   被覆盖的结果行（active 行，来自 selectOne）
     * @param rollbackId 关联回退ID；正常保存传 null
     */
    public void archiveResultRevision(SampleResult existing, Long rollbackId) {
        if (existing == null || existing.getId() == null) {
            return;
        }
        existing.setDeleted(0);
        archiveRow(TABLE_RESULT, existing.getId(), existing.getSampleId(), existing.getSampleNo(),
                REASON_REVISION, rollbackId, existing);
    }

    // =========================================================================
    // 失效
    // =========================================================================

    /**
     * 失效某样品全部「有效」检测明细（留档 + deleted = 行自身 id）。
     */
    public DispositionResult invalidateItems(Long sampleId, Long rollbackId) {
        List<SampleItem> items = sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, sampleId)
                .orderByAsc(SampleItem::getId));
        if (items.isEmpty()) {
            return new DispositionResult(0, 0, null);
        }
        int reason = rollbackId == null ? REASON_REVISION : REASON_ROLLBACK;
        for (SampleItem item : items) {
            item.setDeleted(0);   // 记录失效前 deleted 原值（active 行恒为 0）
            archiveRow(TABLE_ITEM, item.getId(), item.getSampleId(), item.getSampleNo(),
                    reason, rollbackId, item);
        }
        int affected = sampleItemMapper.invalidateBySampleId(sampleId, operator());
        return new DispositionResult(affected, 0, "失效 " + affected + " 项检测明细");
    }

    /**
     * 失效某样品全部「有效」检验结果（留档 + deleted = 行自身 id）。
     *
     * <p>与 {@link #invalidateItems} 配合使用时**必须先失效明细再失效结果**：明细失效后，
     * 指向这些明细的结果若仍有效即为孤儿引用（Pit 1），本方法同步把它们失效以保证一致性。</p>
     */
    public DispositionResult invalidateResults(Long sampleId, Long rollbackId) {
        List<SampleResult> results = sampleResultMapper.selectList(new LambdaQueryWrapper<SampleResult>()
                .eq(SampleResult::getSampleId, sampleId)
                .orderByAsc(SampleResult::getId));
        if (results.isEmpty()) {
            return new DispositionResult(0, 0, null);
        }
        int reason = rollbackId == null ? REASON_REVISION : REASON_ROLLBACK;
        for (SampleResult r : results) {
            r.setDeleted(0);
            archiveRow(TABLE_RESULT, r.getId(), r.getSampleId(), r.getSampleNo(),
                    reason, rollbackId, r);
        }
        int affected = sampleResultMapper.invalidateBySampleId(sampleId, operator());
        return new DispositionResult(0, affected, "失效 " + affected + " 项检验结果");
    }

    /**
     * 清空某样品全部检测明细的指派字段（回退至 S30「已分解」时调用）。
     *
     * <p>指派不是「行级失效」，故不改变 {@code deleted}；但清空前仍留档 pre-image（取证 + 可恢复）。</p>
     *
     * @param rollbackId 关联回退ID（用于恢复时定位留档行）
     */
    public DispositionResult resetAssignFields(Long sampleId, Long rollbackId) {
        List<SampleItem> items = sampleItemMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, sampleId)
                .orderByAsc(SampleItem::getId));
        // 仅对「确有指派」的行留档，避免无谓留档（R4）
        List<SampleItem> assigned = items.stream().filter(this::hasAssignment).toList();
        if (assigned.isEmpty()) {
            return new DispositionResult(0, 0, null);
        }
        for (SampleItem item : assigned) {
            item.setDeleted(0);
            archiveRow(TABLE_ITEM, item.getId(), item.getSampleId(), item.getSampleNo(),
                    REASON_ROLLBACK, rollbackId, item);
        }
        int affected = sampleItemMapper.resetAssignBySampleId(sampleId, operator());
        return new DispositionResult(0, 0, "清空 " + affected + " 项任务指派");
    }

    // =========================================================================
    // 恢复
    // =========================================================================

    /**
     * 按回退记录恢复下游数据：把该次回退留档的行 {@code deleted} 复位为 0，并回填被改写字段。
     *
     * <p>只在「未产生新下游数据」时由 {@code RollbackServiceImpl.recover} 调用
     * （避免与新建数据撞唯一键）。</p>
     */
    public DispositionResult restoreByRollback(Long rollbackId) {
        List<SampleDataArchive> archives = archiveMapper.selectList(new LambdaQueryWrapper<SampleDataArchive>()
                .eq(SampleDataArchive::getRollbackId, rollbackId)
                .orderByAsc(SampleDataArchive::getId));
        int items = 0;
        int results = 0;
        for (SampleDataArchive archive : archives) {
            if (TABLE_ITEM.equals(archive.getTableName())) {
                SampleItem snapshot = parse(archive.getSnapshotJson(), SampleItem.class);
                if (snapshot == null || snapshot.getId() == null) {
                    continue;
                }
                items += sampleItemMapper.restoreInvalidated(snapshot, operator());
            } else if (TABLE_RESULT.equals(archive.getTableName())) {
                SampleResult snapshot = parse(archive.getSnapshotJson(), SampleResult.class);
                if (snapshot == null || snapshot.getId() == null) {
                    continue;
                }
                results += sampleResultMapper.restoreInvalidated(snapshot.getId(), operator());
            }
        }
        String summary = (items == 0 && results == 0) ? null : ("恢复 " + items + " 项明细、" + results + " 项结果");
        return new DispositionResult(items, results, summary);
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    /** 单行留档（写 sample_data_archive；只增不删） */
    private void archiveRow(String tableName, Long rowId, Long sampleId, String sampleNo,
                            int archiveReason, Long rollbackId, Object snapshot) {
        SampleDataArchive archive = new SampleDataArchive();
        archive.setSampleId(sampleId);
        archive.setSampleNo(sampleNo);
        archive.setTableName(tableName);
        archive.setRowId(rowId);
        archive.setRollbackId(rollbackId);
        archive.setArchiveReason(archiveReason);
        archive.setSnapshotJson(toJson(snapshot));
        archiveMapper.insert(archive);
    }

    private boolean hasAssignment(SampleItem item) {
        return item.getTesterNo() != null
                || !Objects.equals(item.getAssignStatus(), 0)
                || !Objects.equals(item.getAssignType(), 0);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "留档快照序列化失败：" + e.getOriginalMessage());
        }
    }

    private <T> T parse(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BizException(500, "留档快照反序列化失败：" + e.getOriginalMessage());
        }
    }

    private String operator() {
        return SecurityUtils.getUsername().orElse("system");
    }
}
