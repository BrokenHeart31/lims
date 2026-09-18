package com.lims.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.StatusEventType;
import com.lims.common.exception.BizException;
import com.lims.entity.Sample;
import com.lims.entity.SampleStatusLog;
import com.lims.mapper.SampleStatusLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 样品状态流水服务单元测试（feature B，T02）。
 *
 * <p>覆盖：append 字段映射完整（谁/何时/从哪到哪/为何/入口/关联回退）、
 * 审计四字段不手工赋值、timeline 按 id 升序、空入参拒绝，
 * 以及**「只追加」不变式**——服务接口不得暴露任何 update/delete/remove 方法
 * （检验机构审计要求：流水只增不改，靠代码结构保证而非纪律）。</p>
 */
class SampleStatusLogServiceImplTest {

    private static final Long SAMPLE_ID = 7L;

    private SampleStatusLogMapper mapper;
    private SampleStatusLogServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SampleStatusLog.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(SampleStatusLogMapper.class);
        service = new SampleStatusLogServiceImpl(mapper);
    }

    private Sample sample() {
        Sample s = new Sample();
        s.setId(SAMPLE_ID);
        s.setSampleNo("JK(2023)-SA-001");
        return s;
    }

    @Test
    @DisplayName("append：字段完整落库（事件类型/从哪到哪/原因/入口/关联回退/处置摘要；操作人取登录上下文）")
    void append_persistsAllFields() {
        when(mapper.insert(any(SampleStatusLog.class))).thenReturn(1);

        service.append(sample(), StatusEventType.ROLLBACK, SampleStatus.S60, SampleStatus.S50,
                "回退至检验中", "误提交，尚有一个项目未录", "ROLLBACK_PANEL", 9L, "失效 3 项检验结果");

        ArgumentCaptor<SampleStatusLog> captor = ArgumentCaptor.forClass(SampleStatusLog.class);
        verify(mapper, times(1)).insert(captor.capture());
        SampleStatusLog log = captor.getValue();
        assertAll(
                () -> assertEquals(SAMPLE_ID, log.getSampleId()),
                () -> assertEquals("JK(2023)-SA-001", log.getSampleNo(), "样品编号应冗余写入"),
                () -> assertEquals(StatusEventType.ROLLBACK, log.getEventType()),
                () -> assertEquals(SampleStatus.S60, log.getFromStatus()),
                () -> assertEquals(SampleStatus.S50, log.getToStatus()),
                () -> assertEquals("回退至检验中", log.getActionLabel()),
                () -> assertEquals("误提交，尚有一个项目未录", log.getReason()),
                () -> assertEquals("ROLLBACK_PANEL", log.getSource()),
                () -> assertEquals(9L, log.getRollbackId()),
                () -> assertEquals("失效 3 项检验结果", log.getDataDisposition()),
                // 无 SecurityContext（单测环境）→ 兜底 system；四字段审计填充不在此列
                () -> assertEquals("system", log.getOperatedBy()),
                () -> assertTrue(log.getOperatedAt() != null, "操作时间应由服务写入"),
                () -> assertNull(log.getCreatedBy(), "审计字段留空，交由 AuditMetaObjectHandler 填充")
        );
    }

    @Test
    @DisplayName("append：样品为空 → 拒绝且不落库")
    void append_rejectsNullSample() {
        BizException ex = assertThrows(BizException.class, () -> service.append(null,
                StatusEventType.FORWARD, SampleStatus.S10, SampleStatus.S20, "登记确认", null, "SAMPLE", null, null));
        assertEquals(400, ex.getCode());
        verify(mapper, never()).insert(any(SampleStatusLog.class));
    }

    @Test
    @DisplayName("timeline：按样品返回全链路事件（升序由 SQL order by 保证），并带出中文标签")
    void timeline_returnsEvents() {
        SampleStatusLog first = new SampleStatusLog();
        first.setId(1L);
        first.setSampleId(SAMPLE_ID);
        first.setEventType(StatusEventType.FORWARD);
        first.setFromStatus(SampleStatus.S10);
        first.setToStatus(SampleStatus.S20);
        when(mapper.selectList(any())).thenReturn(List.of(first));

        List<SampleStatusLog> logs = service.timeline(SAMPLE_ID);

        assertAll(
                () -> assertEquals(1, logs.size()),
                () -> assertEquals("正向推进", logs.get(0).getEventTypeLabel()),
                () -> assertEquals("已登记", logs.get(0).getFromStatusLabel()),
                () -> assertEquals("登记确认", logs.get(0).getToStatusLabel())
        );
        verify(mapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("timeline：样品ID为空 → 拒绝")
    void timeline_rejectsNullId() {
        BizException ex = assertThrows(BizException.class, () -> service.timeline(null));
        assertEquals(400, ex.getCode());
        verify(mapper, never()).selectList(any());
    }

    @Test
    @DisplayName("★只追加不变式：服务接口不暴露任何 update / delete / remove 方法")
    void interfaceExposesNoMutatingApiBeyondAppend() {
        for (Method m : com.lims.service.SampleStatusLogService.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase();
            assertFalse(name.contains("update") || name.contains("delete") || name.contains("remove"),
                    "状态流水只增不改，不得暴露：" + m.getName());
        }
    }
}
