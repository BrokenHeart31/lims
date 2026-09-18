package com.lims.service.ai;

import com.lims.controller.AiCompanionController;
import com.lims.mapper.SampleItemFactMapper;
import com.lims.mapper.SampleMapper;
import com.lims.service.ai.companion.CompanionTriggerPolicy;
import com.lims.service.ai.companion.ValueAnchorAssembler;
import com.lims.service.ai.companion.impl.StandardCompanionServiceImpl;
import com.lims.service.ai.flow.BusinessFlowMap;
import com.lims.service.ai.flow.FlowGuideAssembler;
import com.lims.service.ai.impl.AiAssistantServiceImpl;
import com.lims.service.ai.impl.GbIndexServiceImpl;
import com.lims.service.ai.scan.ScanOcrJobStore;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AI 域隔离**红线**测试（T3 硬约束；增量 ai_flow_assistant 扩容，设计 §2.6 / §5.2）。
 *
 * <p>用反射**穷举** AI 各类的协作者依赖，固化「AI 是旁路、绝不写业务判定结论」：</p>
 * <ol>
 *   <li>AI 任何类都不得依赖 {@code SampleItemMapper}/{@code SampleResultMapper}/{@code JudgeEngine}
 *       （判定/结论相关）；</li>
 *   <li>AI 引用的 {@code *Mapper} 仅限 {@code ai_*}/{@code gb_*} 与只读 {@code SampleMapper}/
 *       {@code SampleItemFactMapper}/{@code ProductLibItemMapper}；</li>
 *   <li>业务只读收敛在 {@link BusinessContextReader}，且其**唯一**依赖为 {@code SampleMapper}；</li>
 *   <li>{@link BusinessContextReader} **无任何写方法**（save/update/insert/delete/remove）；</li>
 *   <li><b>数值路径物理隔离</b>：{@link ValueAnchorAssembler} 依赖集**不含** {@code GbClauseMapper}/
 *       {@code GbRetriever}/{@code GbClause}/{@code JudgeEngine}（数值永不取 OCR）；</li>
 *   <li>{@link SampleItemFactMapper} **仅含 {@code @Select} 只读方法**（无写方法）。</li>
 * </ol>
 */
class AiDomainIsolationTest {

    /** 判定 / 结论相关协作者——AI 一律禁止引用。 */
    private static final Set<String> FORBIDDEN = Set.of(
            "SampleItemMapper", "SampleResultMapper", "JudgeEngine",
            "ItemMapper", "ResultMapper", "SampleItemResultMapper");

    private static final Set<String> ALLOWED_MAPPERS = Set.of(
            "AiConversationMapper", "AiMessageMapper", "AiHintLogMapper",
            "GbDocumentMapper", "GbClauseMapper", "GbImportJobMapper",
            "SampleMapper", "SampleItemFactMapper", "ProductLibItemMapper");

    private static final List<Class<?>> AI_CLASSES = List.of(
            AiAssistantServiceImpl.class, GbIndexServiceImpl.class, GbIndexWorker.class,
            GbRetriever.class, DomainGuard.class, BusinessContextReader.class,
            BusinessRuleAssembler.class, GbStandardsStore.class, ChunkSplitter.class,
            OllamaClient.class, OllamaHealthChecker.class, AiProperties.class,
            // 增量 ai_flow_assistant 新增类
            FlowGuideAssembler.class, BusinessFlowMap.class,
            StandardCompanionServiceImpl.class, CompanionTriggerPolicy.class,
            ValueAnchorAssembler.class, ScanOcrJobStore.class, AiCompanionController.class);

    /** 仅识别本项目的 Mapper（排除 Jackson {@code ObjectMapper} 等名字也以 Mapper 结尾的类）。 */
    private static boolean isLimsMapper(Field f) {
        return f.getType().getName().startsWith("com.lims.mapper.");
    }

    @Test
    @DisplayName("★红线：AI 各类不得依赖任何判定/结论相关 Mapper 或 JudgeEngine")
    void aiClassesDoNotDependOnJudgementCollaborators() {
        for (Class<?> c : AI_CLASSES) {
            for (Field f : c.getDeclaredFields()) {
                String type = f.getType().getSimpleName();
                assertFalse(FORBIDDEN.contains(type),
                        c.getSimpleName() + "." + f.getName() + " 依赖了禁用的判定协作者：" + type);
            }
        }
    }

    @Test
    @DisplayName("★红线：AI 引用的 *Mapper 字段仅限 ai_*/gb_* 与只读 SampleMapper/SampleItemFactMapper/ProductLibItemMapper")
    void aiMapperDependenciesAreScoped() {
        for (Class<?> c : AI_CLASSES) {
            for (Field f : c.getDeclaredFields()) {
                if (isLimsMapper(f)) {
                    assertTrue(ALLOWED_MAPPERS.contains(f.getType().getSimpleName()),
                            c.getSimpleName() + "." + f.getName() + " 引用了越界 Mapper："
                                    + f.getType().getSimpleName());
                }
            }
        }
    }

    @Test
    @DisplayName("★红线：业务只读收敛在 BusinessContextReader，唯一依赖 SampleMapper")
    void businessContextReaderOnlyReadsSample() {
        Field[] fields = BusinessContextReader.class.getDeclaredFields();
        assertEquals(1, fields.length);
        assertEquals(SampleMapper.class, fields[0].getType());
    }

    @Test
    @DisplayName("★红线：BusinessContextReader 无任何写方法")
    void businessContextReaderHasNoWriteMethods() {
        for (Method m : BusinessContextReader.class.getDeclaredMethods()) {
            String n = m.getName().toLowerCase();
            assertFalse(n.startsWith("save") || n.startsWith("update") || n.startsWith("insert")
                            || n.startsWith("delete") || n.startsWith("remove") || n.startsWith("write"),
                    "BusinessContextReader 出现写方法：" + m.getName());
        }
    }

    @Test
    @DisplayName("AiAssistantServiceImpl 仅依赖 ai_* Mapper（不越界业务写表）")
    void assistantOnlyDependsOnAiMappers() {
        Set<String> aiMappers = Set.of("AiConversationMapper", "AiMessageMapper");
        for (Field f : AiAssistantServiceImpl.class.getDeclaredFields()) {
            if (isLimsMapper(f)) {
                assertTrue(aiMappers.contains(f.getType().getSimpleName()),
                        "AiAssistantServiceImpl 越界依赖：" + f.getType().getSimpleName());
            }
        }
    }

    @Test
    @DisplayName("★红线：ValueAnchorAssembler 依赖集不含 gb_*/GbRetriever/JudgeEngine（数值路径不碰 OCR）")
    void valueAnchorAssemblerIsIsolatedFromOcr() {
        Set<String> forbidden = Set.of("GbClauseMapper", "GbRetriever", "GbClause", "JudgeEngine",
                "GbDocumentMapper");
        for (Field f : ValueAnchorAssembler.class.getDeclaredFields()) {
            String type = f.getType().getSimpleName();
            assertFalse(forbidden.contains(type),
                    "ValueAnchorAssembler." + f.getName() + " 依赖了数值路径禁用的协作者：" + type);
        }
    }

    @Test
    @DisplayName("★红线：SampleItemFactMapper 仅含 @Select 只读方法（无写方法）")
    void sampleItemFactMapperIsReadOnly() {
        for (Method m : SampleItemFactMapper.class.getDeclaredMethods()) {
            assertTrue(m.isAnnotationPresent(Select.class),
                    "SampleItemFactMapper 出现非 @Select 方法：" + m.getName());
        }
    }
}
