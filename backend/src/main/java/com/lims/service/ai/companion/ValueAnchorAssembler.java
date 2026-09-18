package com.lims.service.ai.companion;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.entity.ProductLibItem;
import com.lims.mapper.ProductLibItemMapper;
import com.lims.mapper.SampleItemFactMapper;
import com.lims.service.ai.SampleItemFact;
import com.lims.vo.AiValueAnchorVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * 数值对齐卡片装配器（feature 增量 ai_flow_assistant，T02，设计 §2.3 / §5.1）。
 *
 * <p><b>本增量关键红线（数值路径物理隔离）</b>：本类**只读** {@code product_lib_item}（系统权威标准库）
 * 与 {@code sample_item} 的快照（经只读投影 {@link SampleItemFactMapper}）；其**依赖集合里绝不出现**
 * {@code GbClauseMapper} / {@code GbRetriever} / {@code GbClause} / {@code JudgeEngine}——
 * 即**数值路径根本不接触 OCR 文本**（OCR 出错字即资质事故，故数值永不取 OCR）。
 * 该依赖集合由 {@code AiDomainIsolationTest} 反射锁死。</p>
 *
 * <p>本类**无任何写方法**、不调模型；卡片只承载「展示 + 只读跳转」语义。</p>
 */
@Component
@RequiredArgsConstructor
public class ValueAnchorAssembler {

    /** 跳转目标：系统标准库查询页（需 {@code base:lib:list} 权限）。 */
    private static final String LIB_QUERY_PATH = "/query/lib?itemName=";

    private final ProductLibItemMapper productLibItemMapper;
    private final SampleItemFactMapper sampleItemFactMapper;

    /**
     * 装配数值对齐卡片。
     *
     * @param itemName     检测项目名（如「毒死蜱」）
     * @param stdNo        标准号（可选，用于收紧标准库匹配）
     * @param sampleNo     样品编号（可选；有则优先取该样品快照值）
     * @param canJumpToLib 当前用户是否具备 {@code base:lib:list} 权限（false → 不给跳转，TE 收敛）
     * @return 卡片；无可用权威数值时 {@link Optional#empty()}
     */
    public Optional<AiValueAnchorVO> anchor(String itemName, String stdNo, String sampleNo, boolean canJumpToLib) {
        if (!StringUtils.hasText(itemName)) {
            return Optional.empty();
        }
        // ① 优先：该样品已套库的 sample_item 快照（最能代表“这个样品”的判定依据）
        if (StringUtils.hasText(sampleNo)) {
            SampleItemFact fact = sampleItemFactMapper.selectFactBySampleAndItem(sampleNo, itemName);
            if (fact != null && StringUtils.hasText(fact.stdValue())) {
                AiValueAnchorVO vo = build(itemName, fact.stdValue(), fact.unit(),
                        fact.basisCode(), null, fact.judgeType(), fact.isReference(),
                        canJumpToLib);
                return Optional.of(vo);
            }
        }
        // ② 兜底：系统标准库 product_lib_item（权威值；不查即不编造，查不到返回空）
        LambdaQueryWrapper<ProductLibItem> w = new LambdaQueryWrapper<ProductLibItem>()
                .eq(ProductLibItem::getItemName, itemName)
                .orderByAsc(ProductLibItem::getId)
                .last("LIMIT 1");
        if (StringUtils.hasText(stdNo)) {
            w.like(ProductLibItem::getBasisCode, stdNo.trim());
        }
        ProductLibItem lib = productLibItemMapper.selectOne(w);
        if (lib == null || !StringUtils.hasText(lib.getStdValue())) {
            return Optional.empty();
        }
        AiValueAnchorVO vo = build(itemName, lib.getStdValue(), lib.getUnit(),
                lib.getBasisCode(), lib.getId(), lib.getJudgeType(), lib.getIsReference(),
                canJumpToLib);
        return Optional.of(vo);
    }

    private AiValueAnchorVO build(String itemName, String stdValue, String unit, String basisCode,
                                  Long libItemId, Integer judgeType, Integer isReference,
                                  boolean canJumpToLib) {
        AiValueAnchorVO vo = new AiValueAnchorVO();
        vo.setItemName(itemName);
        vo.setUnit(unit);
        vo.setStdValue(stdValue);
        vo.setJudgeTypeLabel(judgeTypeLabel(judgeType));
        vo.setIsReference(isReference != null && isReference == 1);
        vo.setBasisCode(basisCode);
        vo.setLibItemId(libItemId);
        vo.setSourceLabel("系统标准库（权威）");
        if (canJumpToLib) {
            vo.setJumpPath(LIB_QUERY_PATH + URLEncoder.encode(itemName, StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return vo;
    }

    /** 判定类型中文名（1=限量比较 2=不得检出/不得使用 3=文本/感官人工）。 */
    private String judgeTypeLabel(Integer judgeType) {
        if (judgeType == null) {
            return null;
        }
        return switch (judgeType) {
            case 1 -> "限量比较";
            case 2 -> "不得检出/不得使用";
            case 3 -> "文本/感官人工";
            default -> "未知";
        };
    }
}
