package com.lims.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lims.common.enums.SampleStatus;
import com.lims.common.exception.BizException;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SysUser;
import com.lims.entity.TesterMethod;
import com.lims.entity.UserMethod;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.mapper.TesterMethodMapper;
import com.lims.mapper.UserMethodMapper;
import com.lims.vo.AssignAutoResultVO;
import com.lims.vo.AssignDetailVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检验任务安排服务单元测试（T-501，AGENTS 4.3）。
 *
 * <p>覆盖 5.0 自动分配规则与 5.4~5.6 三类流程的语义边界：
 * <ul>
 *   <li><b>分类规则</b>：编号含 NA/XA/SA → user_method 命中 → assignType=1</li>
 *   <li><b>user_method 缺行</b> → 兜底到 CATEGORY_FALLBACK（njna000/njxa000/njsa000）</li>
 *   <li><b>方法资质规则</b>：分类未命中 → 遍历 methods(# 分隔) 匹配 tester_method</li>
 *   <li><b>双不命中</b> → assignStatus=0，reason 给可读原因</li>
 *   <li><b>已人工改派不覆盖</b>（assignType=3）</li>
 *   <li><b>5.5 人工改派无资质拒绝</b>（biz 异常）</li>
 *   <li><b>5.6 安排确认要求全部已指派 + 状态机白名单 + 乐观条件</b></li>
 * </ul>
 * </p>
 */
class AssignServiceImplTest {

    private SampleMapper sampleMapper;
    private SysUserMapper sysUserMapper;
    private UserMethodMapper userMethodMapper;
    private TesterMethodMapper testerMethodMapper;
    private SampleItemMapper sampleItemMapper;

    private AssignServiceImpl service;

    private static final Long SAMPLE_ID = 1L;
    private static final Long ITEM_ID = 100L;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, Sample.class);
        TableInfoHelper.initTableInfo(assistant, SampleItem.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, UserMethod.class);
        TableInfoHelper.initTableInfo(assistant, TesterMethod.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        sampleMapper = mock(SampleMapper.class);
        sysUserMapper = mock(SysUserMapper.class);
        userMethodMapper = mock(UserMethodMapper.class);
        testerMethodMapper = mock(TesterMethodMapper.class);
        sampleItemMapper = mock(SampleItemMapper.class);
        service = new AssignServiceImpl(sampleMapper, sysUserMapper, userMethodMapper, testerMethodMapper);
        Field f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class.getDeclaredField("baseMapper");
        f.setAccessible(true);
        f.set(service, sampleItemMapper);
    }

    // ------------------------------------------------------------------ helpers

    private Sample sample(long id, String sampleNo, SampleStatus status) {
        Sample s = new Sample();
        s.setId(id);
        s.setSampleNo(sampleNo);
        s.setStatus(status);
        s.setClientName("客户A");
        s.setSamplingDate(java.time.LocalDate.of(2026, 9, 1));
        s.setInspectType("1");
        s.setTaskNo("T-2026-001");
        return s;
    }

    private SampleItem item(long id, long sampleId, String itemName, String methods, String stdValue,
                            Integer judgeType, Integer isReference, Integer assignStatus,
                            Integer assignType, String testerNo) {
        SampleItem it = new SampleItem();
        it.setId(id);
        it.setSampleId(sampleId);
        it.setItemOrder(1);
        it.setItemName(itemName);
        it.setMethods(methods);
        it.setUnit("mg/kg");
        it.setStdValue(stdValue);
        it.setJudgeType(judgeType);
        it.setIsReference(isReference);
        it.setAssignStatus(assignStatus);
        it.setAssignType(assignType);
        it.setTesterNo(testerNo);
        return it;
    }

    private UserMethod userMethod(String method, String usergh) {
        UserMethod um = new UserMethod();
        um.setMethod(method);
        um.setUsergh(usergh);
        return um;
    }

    private SysUser user(String username, String nickname, long deptId) {
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setNickname(nickname);
        u.setDeptId(deptId);
        u.setStatus(1);
        return u;
    }

    private TesterMethod testerMethod(String methodNo, String testerNo, int qualStatus) {
        TesterMethod tm = new TesterMethod();
        tm.setMethodNo(methodNo);
        tm.setTesterNo(testerNo);
        tm.setQualStatus(qualStatus);
        return tm;
    }

    // ================================================================== 5.4 自动分配

    @Test
    @DisplayName("分类规则：样品编号含 NA → user_method 命中 → assignType=1")
    void autoAssign_categoryHit() {
        Sample s = sample(SAMPLE_ID, "BATCH-NA-2026-001", SampleStatus.S30);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "六六六", "GB 2763-2021", "0.05", 1, 0, 0, null, null);
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(it));
        when(userMethodMapper.selectList(any())).thenReturn(List.of(userMethod("NA", "njna001")));
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user("njna001", "农残王", 5L)));

        AssignAutoResultVO result = service.autoAssign(SAMPLE_ID);

        assertAll(
                () -> assertEquals(SAMPLE_ID, result.getSampleId()),
                () -> assertEquals(1, result.getTotal()),
                () -> assertEquals(1, result.getAssigned()),
                () -> assertEquals(0, result.getPending()),
                () -> assertEquals("njna001", it.getTesterNo()),
                // 注：autoAssign 主流程不写 item.testerName（写库后由 detail 查询回填，故此处断言 item 内存对象的 nickname 无意义）
                () -> assertEquals(1, it.getAssignStatus()),
                () -> assertEquals(1, it.getAssignType())
        );
        // save 调用 1 次（每项 1 次原子更新，避免 N+1 也没事但要确保持久化）
        verify(sampleItemMapper, times(1)).update(any(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("分类规则：user_method 缺行 → 兜底到 AGENTS 7.4 约定工号 njna000")
    void autoAssign_categoryFallback() {
        Sample s = sample(SAMPLE_ID, "BATCH-XA-2026-002", SampleStatus.S30);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "克伦特罗", "GB 31658", "不得检出", 3, 0, 0, null, null);
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(it));
        when(userMethodMapper.selectList(any())).thenReturn(List.of()); // 缺行
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user("njxa000", "畜残李", 5L)));

        AssignAutoResultVO result = service.autoAssign(SAMPLE_ID);

        assertAll(
                () -> assertEquals(1, result.getAssigned()),
                () -> assertEquals("njxa000", it.getTesterNo()),
                () -> assertEquals(1, it.getAssignType()) // 仍标 1，区别是兜底由后端日志说明
        );
    }

    @Test
    @DisplayName("方法资质规则：分类未命中 → 遍历 methods 匹配 tester_method")
    void autoAssign_methodMatch() {
        Sample s = sample(SAMPLE_ID, "BATCH-2026-XYZ-003", SampleStatus.S30); // 不含 NA/XA/SA
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "菌落总数", "GB 4789.2#GB 4789.15", "10000", 2, 0, 0, null, null);
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(it));
        when(userMethodMapper.selectList(any())).thenReturn(List.of()); // 分类必不命中
        // Impl 用一次 IN(...) 查询所有方法 → 返回命中行即可
        when(testerMethodMapper.selectList(any())).thenReturn(List.of(testerMethod("GB 4789.15", "nj002", 1)));
        when(sysUserMapper.selectList(any())).thenReturn(List.of(user("nj002", "检验员二", 5L)));

        AssignAutoResultVO result = service.autoAssign(SAMPLE_ID);

        assertAll(
                () -> assertEquals(1, result.getAssigned()),
                () -> assertEquals("nj002", it.getTesterNo()),
                () -> assertEquals(2, it.getAssignType()) // 方法资质 = 2
        );
    }

    @Test
    @DisplayName("兜底：分类未命中 + 方法资质未命中 → assignStatus=0 且 reason 给可读原因")
    void autoAssign_remainPending() {
        Sample s = sample(SAMPLE_ID, "BATCH-2026-QQ-004", SampleStatus.S30);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "未知项目", "GB-XXX", "--", 1, 0, 0, null, null);
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(it));
        when(userMethodMapper.selectList(any())).thenReturn(List.of());
        when(testerMethodMapper.selectList(any())).thenReturn(List.of());

        AssignAutoResultVO result = service.autoAssign(SAMPLE_ID);

        assertAll(
                () -> assertEquals(0, result.getAssigned()),
                () -> assertEquals(1, result.getPending()),
                () -> assertEquals(0, it.getAssignStatus()),
                () -> assertNull(it.getTesterNo()),
                () -> assertNotNull(result.getDetails()),
                () -> assertEquals(1, result.getDetails().size()),
                () -> assertNotNull(result.getDetails().get(0).getReason())
        );
        // 兜底时调用 applyUnassign 把可能残留的旧指派清掉（保证可重跑幂等 → 旧 assignStatus/testerNo 必归零）
        verify(sampleItemMapper, times(1)).update(any(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("已人工改派的项（assignType=3）不被自动分配覆盖")
    void autoAssign_preserveManualReassign() {
        Sample s = sample(SAMPLE_ID, "BATCH-NA-2026-005", SampleStatus.S30);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        // 已存在一项，人工改派给 nj999
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "项目甲", "GB", "0.1", 1, 0,
                1, 3, "nj999");
        it.setTesterName("改派员");
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(it));
        when(userMethodMapper.selectList(any())).thenReturn(List.of(userMethod("NA", "njna001")));

        AssignAutoResultVO result = service.autoAssign(SAMPLE_ID);

        assertAll(
                () -> assertEquals(1, result.getAssigned()), // 已指派数计数包含人工
                () -> assertEquals(0, result.getPending()),
                () -> assertEquals("nj999", it.getTesterNo()), // 仍为人工指派
                () -> assertEquals(3, it.getAssignType())     // 类型未变
        );
        verify(sampleItemMapper, never()).update(any(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("autoAssign：样品不存在 → BizException")
    void autoAssign_sampleMissing() {
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(null);
        assertThrows(BizException.class, () -> service.autoAssign(SAMPLE_ID));
    }

    @Test
    @DisplayName("autoAssign：样品非 S30 → 拒绝（5.0 要求 status=S30）")
    void autoAssign_wrongStatus() {
        Sample s = sample(SAMPLE_ID, "BATCH-NA-006", SampleStatus.S20);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        BizException ex = assertThrows(BizException.class, () -> service.autoAssign(SAMPLE_ID));
        assertTrue(ex.getMessage().contains("不允许"));
    }

    // ================================================================== 5.5 人工改派

    @Test
    @DisplayName("reassign：分类命中者 → assignType=3 改派成功")
    void reassign_ok() {
        Sample s = sample(SAMPLE_ID, "BATCH-NA-007", SampleStatus.S30);
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "项目", "GB", "0.1", 1, 0, 1, 1, "njna001");
        it.setTesterName("原");
        when(sampleItemMapper.selectById(ITEM_ID)).thenReturn(it);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        when(userMethodMapper.selectList(any())).thenReturn(List.of(userMethod("NA", "njna002")));
        when(sysUserMapper.selectOne(any())).thenReturn(user("njna002", "新指派", 5L));

        AssignDetailVO.Item vo = service.reassign(ITEM_ID, "njna002");

        assertAll(
                () -> assertEquals(ITEM_ID, vo.getId()),
                () -> assertEquals("njna002", vo.getTesterNo()),
                () -> assertEquals(3, vo.getAssignType()),
                () -> assertEquals(1, vo.getAssignStatus()),
                () -> assertEquals(3, it.getAssignType())
        );
        verify(sampleItemMapper, times(1)).update(any(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("reassign：检验员对该单项无资质 → BizException 400")
    void reassign_notQualified() {
        Sample s = sample(SAMPLE_ID, "BATCH-NA-008", SampleStatus.S30);
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "项目", "GB", "0.1", 1, 0, 0, null, null);
        when(sampleItemMapper.selectById(ITEM_ID)).thenReturn(it);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        // 候选中只有 NA 资质
        when(userMethodMapper.selectList(any())).thenReturn(List.of(userMethod("NA", "njna001")));
        // 但要求改派给 njsa000（SA）→ 资质不符
        when(sysUserMapper.selectOne(any())).thenReturn(user("njsa000", "水产", 5L));
        when(sysUserMapper.selectList(any())).thenReturn(List.of());
        // 兜底路径：tester_method 也没有该方法的资质
        when(testerMethodMapper.selectList(any())).thenReturn(List.of());

        BizException ex = assertThrows(BizException.class,
                () -> service.reassign(ITEM_ID, "njsa000"));
        assertTrue(ex.getMessage().contains("资质"));
    }

    // ================================================================== 5.6 安排确认

    @Test
    @DisplayName("confirm：全部已指派 + S30 → S40，update 影响 1 行")
    void confirm_ok() {
        Sample s = sample(SAMPLE_ID, "BATCH-NA-009", SampleStatus.S30);
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "项目", "GB", "0.1", 1, 0, 1, 1, "njna001");
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(it));
        when(sampleMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        int status = service.confirm(SAMPLE_ID);

        assertEquals(SampleStatus.S40.getCode(), status);
        verify(sampleMapper, times(1)).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("confirm：仍有未指派项 → BizException 400")
    void confirm_pendingExists() {
        Sample s = sample(SAMPLE_ID, "BATCH-NA-010", SampleStatus.S30);
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "项目", "GB", "0.1", 1, 0, 0, null, null);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(it));

        BizException ex = assertThrows(BizException.class, () -> service.confirm(SAMPLE_ID));
        assertTrue(ex.getMessage().contains("待指派"));
        verify(sampleMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("confirm：乐观 UPDATE 影响 0 行 → BizException「请刷新后重试」")
    void confirm_concurrentConflict() {
        Sample s = sample(SAMPLE_ID, "BATCH-NA-011", SampleStatus.S30);
        SampleItem it = item(ITEM_ID, SAMPLE_ID, "项目", "GB", "0.1", 1, 0, 1, 1, "njna001");
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        when(sampleItemMapper.selectList(any())).thenReturn(List.of(it));
        when(sampleMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0); // 冲突

        BizException ex = assertThrows(BizException.class, () -> service.confirm(SAMPLE_ID));
        assertTrue(ex.getMessage().contains("刷新"));
    }

    @Test
    @DisplayName("confirm：样品非 S30 → 拒绝")
    void confirm_wrongStatus() {
        Sample s = sample(SAMPLE_ID, "BATCH-NA-012", SampleStatus.S40);
        when(sampleMapper.selectById(SAMPLE_ID)).thenReturn(s);
        BizException ex = assertThrows(BizException.class, () -> service.confirm(SAMPLE_ID));
        assertTrue(ex.getMessage().contains("不允许"));
    }
}