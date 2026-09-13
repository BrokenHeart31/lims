package com.lims.service.result;

import com.lims.common.enums.ConclusionSource;
import com.lims.common.enums.ResultConclusion;
import com.lims.entity.SampleResult;
import com.lims.service.judge.JudgeEngine;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 「有效录入」口径（T-912 裁决定稿，唯一权威）。
 *
 * <p><b>问题</b>：旧口径把「存在一行 sample_result」等同于「已录入」，
 * 而 {@code testValue} 允许为空 → 可以带空结果行一路提交到 S60（流程卫生缺口）。</p>
 *
 * <p><b>定稿口径</b>：一个检测单项算「已录入」，当且仅当
 * <b>①{@code testValue} 非空白</b>，或 <b>②文本/感官项（judgeType=3）已人工选定合格/不合格</b>。
 * 业务依据：说明书「八」明确「样品检测单项的检测数据**全部录入**系统后，样品即转入签发流程」——
 * 空值行不是「检测数据」，不算录入。</p>
 *
 * <p><b>与「待判定」的区别（关键）</b>：
 * <ul>
 *   <li><b>未录入</b>（本类判定为 false）= 检验员的**操作缺漏** → 必须补录，{@code submit} 阻断。</li>
 *   <li><b>待判定</b>（引擎产出 conclusion=3，但 testValue 有值）= **数据缺口**（缺检出限/缺标准文本），
 *       检验员无能为力 → 不阻断提交（2026-09-12 既定自裁），改由 T-701 审核页显式确认后放行。</li>
 * </ul>
 * 两者性质不同，故不可混为一谈：把「未录入」也算作「待判定」会让操作缺漏被静默放过。</p>
 */
public final class ResultEntryPolicy {

    /** 判定类型中文名（录入页 / 审核页共用，避免两处维护） */
    public static final Map<Integer, String> JUDGE_TYPE_LABELS = Map.of(
            JudgeEngine.JT_LIMIT, "限量比较",
            JudgeEngine.JT_NOT_DETECTED, "不得检出/不得使用",
            JudgeEngine.JT_MANUAL, "文本/感官人工");

    /** 判定类型 1=限量比较（别名转发 JudgeEngine，供标准库维护域引用而不必依赖引擎包） */
    public static final int JUDGE_TYPE_LIMIT = JudgeEngine.JT_LIMIT;

    /** 判定类型 2=不得检出/不得使用 */
    public static final int JUDGE_TYPE_NOT_DETECTED = JudgeEngine.JT_NOT_DETECTED;

    /** 判定类型 3=文本/感官人工 */
    public static final int JUDGE_TYPE_TEXT = JudgeEngine.JT_MANUAL;

    private ResultEntryPolicy() {
    }

    /**
     * 判定类型是否为闭集内的合法值（1/2/3）。
     *
     * <p>T-106 标准库维护与 T-401 分解均须校验：越界值一旦落库，
     * 判定引擎会走「闭集外一律待判定」分支，样品静默停在待判定而无人知道原因。</p>
     */
    public static boolean isValidJudgeType(Integer judgeType) {
        return judgeType != null && JUDGE_TYPE_LABELS.containsKey(judgeType);
    }

    /**
     * 判断某检测单项是否已**有效录入**。
     *
     * @param judgeType 单项判定类型（取自 sample_item 快照）
     * @param result    结果行；为 {@code null} 表示从未保存过
     */
    public static boolean isEntered(Integer judgeType, SampleResult result) {
        if (result == null) {
            return false;
        }
        if (StringUtils.hasText(result.getTestValue())) {
            return true;
        }
        // 文本/感官项：检验值可以是描述性文本（可空），但必须已人工选定合格/不合格
        return isManualJudgeType(judgeType)
                && result.getConclusionSource() == ConclusionSource.MANUAL
                && result.getConclusion() != null
                && result.getConclusion() != ResultConclusion.PENDING;
    }

    /** 是否为文本/感官（人工判定）型单项 */
    public static boolean isManualJudgeType(Integer judgeType) {
        return judgeType != null && judgeType == JudgeEngine.JT_MANUAL;
    }

    /** 判定类型中文名（未知类型返回「未知」，便于前端提示数据异常） */
    public static String judgeTypeLabel(Integer judgeType) {
        return JUDGE_TYPE_LABELS.getOrDefault(judgeType, "未知");
    }
}
