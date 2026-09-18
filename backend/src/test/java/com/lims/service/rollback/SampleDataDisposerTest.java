package com.lims.service.rollback;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.entity.SampleDataArchive;
import com.lims.entity.SampleItem;
import com.lims.entity.SampleResult;
import com.lims.mapper.SampleAuditLogMapper;
import com.lims.mapper.SampleDataArchiveMapper;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleResultMapper;
import com.lims.mapper.SampleStatusLogMapper;
import com.lims.mapper.SysOperationLogMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.Invocation;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下游数据处置器单元测试（feature B，T02）——**固化回退不变式④「无物理删除路径」**。
 *
 * <p>设计 §0 / §10-R3 的不变式④原文：{@code sample_audit_log}/{@code sample_status_log}/
 * {@code sys_operation_log} 只增不改；{@code sample_item}/{@code sample_result} 只置失效（软删），
 * <b>绝不物理删除</b>。本类把④从「注释/结构约定」升级为**可运行断言**，两个层次：</p>
 * <ol>
 *   <li><b>行为层</b>：当处置器执行失效 / 清空指派 / 恢复时，**只调用 Mapper 的 UPDATE 入口**
 *       （{@code invalidateBySampleId}/{@code resetAssignBySampleId}/{@code restoreInvalidated}）
 *       与 {@code archiveMapper.insert}；对**所有**被注入的 Mapper，穷举其真实调用记录，
 *       断言**不存在任何 {@code delete*}/{@code remove*} 调用**（不依赖具体重载签名）。</li>
 *   <li><b>结构层</b>：反射断言两张业务 Mapper 的**自定义方法里没有 {@code @Delete}**、
 *       其 {@code @Update} SQL 里**不含 {@code DELETE FROM}**；且四张「只追加」表
 *       （sample_status_log / sample_data_archive / sample_audit_log / sys_operation_log）
 *       的 Mapper **不声明任何 update/delete/remove 方法**。</li>
 * </ol>
 *
 * <p>为什么用「调用记录穷举 + 反射」而不是给 {@code deleteById} 打 {@code never()}：本类只关心
 * 「有没有物理删除这条路径」，与 MP {@code BaseMapper} 的哪个重载签名无关，故按方法名判定更稳。</p>
 */
class SampleDataDisposerTest {

    private static final Long SAMPLE_ID = 10L;
    private static final Long ROLLBACK_ID = 77L;
    private static final String OPERATOR = "system";

    private final ObjectMapper objectMapper = new ObjectMapper()
            // 与 Spring Boot 默认一致：实体上的派生只读 getter（如 SampleResult.conclusionLabel）
            // 会被序列化进出网 JSON，反序列化时须按“未知字段”忽略，否则生产 ObjectMapper 之外的新
            // ObjectMapper 会因 FAIL_ON_UNKNOWN_PROPERTIES 报错（生产用 Spring 容器内的 mapper，不受影响）。
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private SampleItemMapper sampleItemMapper;
    private SampleResultMapper sampleResultMapper;
    private SampleDataArchiveMapper archiveMapper;

    private SampleDataDisposer disposer;

    @BeforeEach
    void setUp() {
        sampleItemMapper = mock(SampleItemMapper.class);
        sampleResultMapper = mock(SampleResultMapper.class);
        archiveMapper = mock(SampleDataArchiveMapper.class);
        // 无 SecurityContext → operator() 回落 "system"
        disposer = new SampleDataDisposer(sampleItemMapper, sampleResultMapper, archiveMapper, objectMapper);
    }

    // ------------------------------------------------------------------ 工具

    private SampleItem item(long id, int assignStatus, String testerNo) {
        SampleItem it = new SampleItem();
        it.setId(id);
        it.setSampleId(SAMPLE_ID);
        it.setSampleNo("JK(2023)-SA-001");
        it.setItemOrder((int) id);
        it.setItemName("铅");
        it.setAssignStatus(assignStatus);
        it.setAssignType(0);
        it.setTesterNo(testerNo);
        return it;
    }

    private SampleResult result(long id, long sampleItemId) {
        SampleResult r = new SampleResult();
        r.setId(id);
        r.setSampleId(SAMPLE_ID);
        r.setSampleItemId(sampleItemId);
        r.setSampleNo("JK(2023)-SA-001");
        r.setItemOrder(1);
        r.setItemName("铅");
        return r;
    }

    private SampleDataArchive archive(String table, Long rowId, String json) {
        SampleDataArchive a = new SampleDataArchive();
        a.setTableName(table);
        a.setRowId(rowId);
        a.setSampleId(SAMPLE_ID);
        a.setSnapshotJson(json);
        return a;
    }

    /** 不变式④行为层断言：给定 Mapper 的真实调用记录里不得出现任何 delete/remove。 */
    private void assertNoPhysicalDelete(Object... mocks) {
        for (Object m : mocks) {
            for (Invocation inv : mockingDetails(m).getInvocations()) {
                String name = inv.getMethod().getName();
                assertFalse(name.startsWith("delete") || name.startsWith("remove"),
                        "不变式④被破坏：出现物理删除调用 " + inv.getMethod().getDeclaringClass().getSimpleName()
                                + "." + name + "()");
            }
        }
    }

    // ============================================================ 失效（软删）

    @Test
    @DisplayName("★不变式④：失效明细 = 先留档 + @Update 置 deleted=id，且全程无任何 delete/remove 调用")
    void invalidateItems_archivesThenSoftDeletes_neverPhysicalDelete() throws Exception {
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(item(1, 0, null), item(2, 1, "njna000")));
        when(sampleItemMapper.invalidateBySampleId(SAMPLE_ID, OPERATOR)).thenReturn(2);

        SampleDataDisposer.DispositionResult r = disposer.invalidateItems(SAMPLE_ID, ROLLBACK_ID);

        assertAll(
                () -> assertEquals(2, r.itemCount()),
                () -> assertEquals(0, r.resultCount()),
                () -> assertNotNull(r.summary()),
                () -> assertTrue(r.summary().contains("2"), r.summary())
        );
        // 逐行留档：表名/行id/回退id/原因=1(回退失效)/快照 deleted 原值=0
        ArgumentCaptor<SampleDataArchive> cap = ArgumentCaptor.forClass(SampleDataArchive.class);
        verify(archiveMapper, times(2)).insert(cap.capture());
        for (SampleDataArchive a : cap.getAllValues()) {
            assertEquals(SampleDataDisposer.TABLE_ITEM, a.getTableName());
            assertEquals(ROLLBACK_ID, a.getRollbackId());
            assertEquals(SampleDataDisposer.REASON_ROLLBACK, a.getArchiveReason());
            assertEquals(SAMPLE_ID, a.getSampleId());
            assertTrue(a.getSnapshotJson().contains("\"deleted\":0"), "快照应含失效前 deleted 原值 0");
        }
        // 软删走显式 UPDATE 入口，且其 SQL 是 SET deleted = id（非 delete）
        verify(sampleItemMapper, times(1)).invalidateBySampleId(SAMPLE_ID, OPERATOR);
        assertNoPhysicalDelete(sampleItemMapper, sampleResultMapper, archiveMapper);
    }

    @Test
    @DisplayName("★不变式④：失效结果 = 先留档 + @Update 置 deleted=id，且全程无任何 delete/remove 调用")
    void invalidateResults_archivesThenSoftDeletes_neverPhysicalDelete() {
        when(sampleResultMapper.selectList(any())).thenReturn(List.of(result(5, 1), result(6, 2)));
        when(sampleResultMapper.invalidateBySampleId(SAMPLE_ID, OPERATOR)).thenReturn(2);

        SampleDataDisposer.DispositionResult r = disposer.invalidateResults(SAMPLE_ID, ROLLBACK_ID);

        assertAll(
                () -> assertEquals(0, r.itemCount()),
                () -> assertEquals(2, r.resultCount()),
                () -> assertTrue(r.summary().contains("2"), r.summary())
        );
        ArgumentCaptor<SampleDataArchive> cap = ArgumentCaptor.forClass(SampleDataArchive.class);
        verify(archiveMapper, times(2)).insert(cap.capture());
        for (SampleDataArchive a : cap.getAllValues()) {
            assertEquals(SampleDataDisposer.TABLE_RESULT, a.getTableName());
            assertEquals(SampleDataDisposer.REASON_ROLLBACK, a.getArchiveReason());
        }
        verify(sampleResultMapper, times(1)).invalidateBySampleId(SAMPLE_ID, OPERATOR);
        assertNoPhysicalDelete(sampleItemMapper, sampleResultMapper, archiveMapper);
    }

    @Test
    @DisplayName("失效：无有效行 → 不留档、不 UPDATE、不删除")
    void invalidateItems_noActiveRows_noSideEffect() {
        when(sampleItemMapper.selectList(any())).thenReturn(List.of());

        SampleDataDisposer.DispositionResult r = disposer.invalidateItems(SAMPLE_ID, ROLLBACK_ID);

        assertEquals(0, r.itemCount());
        verify(archiveMapper, never()).insert(any(SampleDataArchive.class));
        verify(sampleItemMapper, never()).invalidateBySampleId(any(), any());
        assertNoPhysicalDelete(sampleItemMapper, archiveMapper);
    }

    // ============================================================ 清空指派（不改 deleted）

    @Test
    @DisplayName("★不变式④：清空指派只留档「确有指派」的行，明细**不失效**、无 delete/remove")
    void resetAssignFields_onlyAssignedRowsArchived_noInvalidation() {
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(item(1, 1, "njna000"), item(2, 0, null)));
        when(sampleItemMapper.resetAssignBySampleId(SAMPLE_ID, OPERATOR)).thenReturn(1);

        SampleDataDisposer.DispositionResult r = disposer.resetAssignFields(SAMPLE_ID, ROLLBACK_ID);

        assertAll(
                () -> assertEquals(0, r.itemCount()),
                () -> assertTrue(r.summary().contains("1"), r.summary())
        );
        // 仅 1 行（有指派的那行）留档；且不改 deleted → 不调 invalidate
        verify(archiveMapper, times(1)).insert(any(SampleDataArchive.class));
        verify(sampleItemMapper, times(1)).resetAssignBySampleId(SAMPLE_ID, OPERATOR);
        verify(sampleItemMapper, never()).invalidateBySampleId(any(), any());
        assertNoPhysicalDelete(sampleItemMapper, archiveMapper);
    }

    // ============================================================ 恢复

    @Test
    @DisplayName("★不变式④：恢复 = 按留档 @Update deleted 复位为 0，无 delete/remove")
    void restoreByRollback_resetsDeletedViaUpdate() throws Exception {
        when(archiveMapper.selectList(any())).thenReturn(List.of(
                archive(SampleDataDisposer.TABLE_ITEM, 1L, objectMapper.writeValueAsString(item(1, 1, "njna000"))),
                archive(SampleDataDisposer.TABLE_RESULT, 5L, objectMapper.writeValueAsString(result(5, 1)))));
        when(sampleItemMapper.restoreInvalidated(any(SampleItem.class), eq(OPERATOR))).thenReturn(1);
        when(sampleResultMapper.restoreInvalidated(eq(5L), eq(OPERATOR))).thenReturn(1);

        SampleDataDisposer.DispositionResult r = disposer.restoreByRollback(ROLLBACK_ID);

        assertAll(
                () -> assertEquals(1, r.itemCount()),
                () -> assertEquals(1, r.resultCount())
        );
        verify(sampleItemMapper, times(1)).restoreInvalidated(any(SampleItem.class), eq(OPERATOR));
        verify(sampleResultMapper, times(1)).restoreInvalidated(eq(5L), eq(OPERATOR));
        verify(archiveMapper, never()).insert(any(SampleDataArchive.class));
        assertNoPhysicalDelete(sampleItemMapper, sampleResultMapper, archiveMapper);
    }

    // ============================================================ 保存前修订留档（Pit 2）

    @Test
    @DisplayName("修订留档：仅当旧行有 id 时写入，原因=2、rollbackId=null；无 id 直接跳过")
    void archiveResultRevision_onlyWhenIdPresent() {
        disposer.archiveResultRevision(result(5, 1), null);
        disposer.archiveResultRevision(new SampleResult(), null); // 无 id → 跳过

        ArgumentCaptor<SampleDataArchive> cap = ArgumentCaptor.forClass(SampleDataArchive.class);
        verify(archiveMapper, times(1)).insert(cap.capture());
        SampleDataArchive a = cap.getValue();
        assertAll(
                () -> assertEquals(SampleDataDisposer.TABLE_RESULT, a.getTableName()),
                () -> assertEquals(5L, a.getRowId()),
                () -> assertEquals(SampleDataDisposer.REASON_REVISION, a.getArchiveReason()),
                () -> assertNull(a.getRollbackId())
        );
        assertNoPhysicalDelete(sampleItemMapper, sampleResultMapper, archiveMapper);
    }

    // ============================================================ 结构层（反射）

    @Test
    @DisplayName("★不变式④结构层：sample_item / sample_result Mapper 无 @Delete、@Update SQL 不含 DELETE FROM")
    void businessMappersDeclareNoPhysicalDelete() {
        for (Class<?> mapper : List.of(SampleItemMapper.class, SampleResultMapper.class)) {
            Method[] declared = mapper.getDeclaredMethods();
            assertTrue(declared.length > 0, mapper.getSimpleName() + " 应声明显式失效/恢复 SQL（避免空断言）");
            for (Method m : declared) {
                assertNull(m.getAnnotation(Delete.class),
                        mapper.getSimpleName() + "." + m.getName() + " 不允许存在物理删除 SQL");
                Update update = m.getAnnotation(Update.class);
                if (update != null) {
                    String sql = update.value()[0].toUpperCase();
                    assertFalse(sql.matches("(?s).*\\bDELETE\\s+FROM\\b.*"),
                            mapper.getSimpleName() + "." + m.getName() + " 的 @Update 不得含 DELETE FROM：" + sql);
                    assertTrue(sql.contains("SET "),
                            mapper.getSimpleName() + "." + m.getName() + " 应为 SET 型更新：" + sql);
                }
            }
        }
    }

    @Test
    @DisplayName("★不变式④结构层：四张「只追加」表 Mapper 不声明任何 update/delete/remove 方法")
    void appendOnlyMappersDeclareNoMutation() {
        for (Class<?> mapper : List.of(SampleStatusLogMapper.class, SampleDataArchiveMapper.class,
                SampleAuditLogMapper.class, SysOperationLogMapper.class)) {
            for (Method m : mapper.getDeclaredMethods()) {
                String name = m.getName().toLowerCase();
                assertFalse(name.contains("update") || name.contains("delete") || name.contains("remove"),
                        mapper.getSimpleName() + " 只增不改，不得声明：" + m.getName());
                assertNull(m.getAnnotation(Delete.class),
                        mapper.getSimpleName() + "." + m.getName() + " 不得标注 @Delete");
            }
        }
    }
}
