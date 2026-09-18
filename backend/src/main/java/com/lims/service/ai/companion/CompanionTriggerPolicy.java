package com.lims.service.ai.companion;

import com.lims.dto.AiChatContextDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标准伴随查询的**触发 / 闭嘴 / 频控键**策略（feature 增量 ai_flow_assistant，T02，设计 §2.5 / §5.1）。
 *
 * <p><b>纯函数，可单测</b>：不引 Mapper、不调模型、不做 IO。</p>
 *
 * <p><b>误报率 = 0 由构造保证</b>：{@link #shouldTrigger(AiChatContextDTO)} 的必要条件之一是
 * 「{@code itemName} 非空白」——**上下文没有明确检测项目名时，绝不主动提示**（G1）。</p>
 *
 * <p>说明（与设计的落地拆分）：本类只做「门禁 + 归一化 + 频控键」这类纯判定；
 * 「该标准号是否已在 {@code gb_document} 建索引」需要查库，放在
 * {@code StandardCompanionServiceImpl} 内做（保持本类纯函数）。</p>
 */
@Component
public class CompanionTriggerPolicy {

    /** 标准号识别（GB 2763-2021 / NY/T 761-2008 / GB/T 5009.19-2008 等）。 */
    private static final Pattern STD_NO = Pattern.compile(
            "(?i)(GB|NY|SN|SB|HJ|DB|JJF|JJG)\\s*/?\\s*(T|Z)?\\s*(\\d{2,6})(?:[.．\\-](\\d{2,4}))?");

    /**
     * 是否应触发伴随查询（纯门禁）。
     *
     * @return 当且仅当 {@code itemName} 非空白时为 true（无项目名绝不主动）
     */
    public boolean shouldTrigger(AiChatContextDTO ctx) {
        return ctx != null && StringUtils.hasText(ctx.getItemName());
    }

    /**
     * 从上下文解析标准号（仅取页面显式带入的 {@code basisCode}）。
     *
     * @return 归一化后的标准号；无 {@code basisCode} 时 {@link Optional#empty()}
     */
    public Optional<String> resolveStdNo(AiChatContextDTO ctx) {
        if (ctx == null || !StringUtils.hasText(ctx.getBasisCode())) {
            return Optional.empty();
        }
        return Optional.ofNullable(normalizeStdNo(ctx.getBasisCode()));
    }

    /**
     * 频控键 {@code sampleNo|itemName|stdNo}（空段留空）。
     */
    public String hintKey(AiChatContextDTO ctx, String stdNo) {
        String sampleNo = ctx == null ? null : ctx.getSampleNo();
        String itemName = ctx == null ? null : ctx.getItemName();
        return safe(sampleNo) + "|" + safe(itemName) + "|" + safe(stdNo);
    }

    /** 建议条一行文案（标准号非空时使用）。 */
    public String oneLine(String stdNo) {
        return "本条涉及 " + safe(stdNo) + "，查看对应限量出处";
    }

    /**
     * 标准号归一化：如 {@code "GB2763-2021"} / {@code "gb 2763-2021"} → {@code "GB 2763-2021"}。
     *
     * @return 无法识别标准号时，返回去空白后的原文（不编造）
     */
    public String normalizeStdNo(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        Matcher m = STD_NO.matcher(raw);
        if (!m.find()) {
            return raw.trim();
        }
        StringBuilder sb = new StringBuilder(m.group(1).toUpperCase());
        if (StringUtils.hasText(m.group(2))) {
            sb.append('/').append(m.group(2).toUpperCase());
        }
        sb.append(' ').append(m.group(3));
        if (StringUtils.hasText(m.group(4))) {
            sb.append('-').append(m.group(4));
        }
        return sb.toString();
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
