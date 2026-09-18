package com.lims.service.ai.parser;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 正文抽取器（feature A，T03，设计 §2.5）。
 *
 * <p>四种格式各一实现：{@code TxtParser} / {@code MarkdownParser} / {@code HtmlParser}（jsoup）/
 * {@code CsvParser}。**PDF 不在此列**——PDF 必须先由 {@code ai/scripts/prepare-standards.py}
 * 转成 TXT（后端 pdf 一律 4211 拒收），这是显式裁决（设计 §2.5）。</p>
 *
 * <p>后端不引除 jsoup 外的任何解析库（唯一新增 Maven 依赖，DECISIONS 已登记）。</p>
 */
public interface DocumentParser {

    /** 是否支持该扩展名（小写、不含点，如 {@code txt}）。 */
    boolean supports(String ext);

    /**
     * 抽取正文。
     *
     * @param fileName 文件名（用于识别标准号、判断格式）
     * @param bytes    原始字节
     */
    ParsedDocument parse(String fileName, byte[] bytes);

    // ------------------------------------------------------------------ 工具

    /** 标准号模式：GB / GB-T / NY-T / SN-T / SB-T / HJ 等，允许「GB2762-2022」「GB/T 5009.11-2014」。 */
    Pattern STD_NO_PATTERN = Pattern.compile(
            "(?i)(GB\\s*/\\s*[TZ]|GB|NY\\s*/\\s*T|NY|SN\\s*/\\s*T|SN|SB\\s*/\\s*T|HJ|DB\\s*\\d*)"
                    + "\\s*[0-9]+(?:\\.[0-9]+)*(?:\\s*[-—]\\s*[0-9]{4})?");

    /** 取文件扩展名（小写、不含点）；无扩展名返回空串。 */
    static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1
                ? ""
                : fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 从文件名（其次首行）识别标准号，并规范化为「GB 2762-2022」形态。
     *
     * <p>规范化：去多余空格、标准类型斜杠两侧不留空格、数字与年份间的连接符统一为 {@code -}。</p>
     */
    static String extractStdNo(String fileName, String textHead) {
        String fromName = normalize(match(fileName));
        if (fromName != null) {
            return fromName;
        }
        return normalize(match(textHead));
    }

    private static String match(String src) {
        if (src == null || src.isBlank()) {
            return null;
        }
        Matcher m = STD_NO_PATTERN.matcher(src);
        return m.find() ? m.group() : null;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        // 折叠空白 → 单空格；压缩斜杠与连接符两侧空格
        String s = raw.trim().replaceAll("\\s+", " ");
        s = s.replaceAll("\\s*/\\s*", "/");
        s = s.replaceAll("\\s*[-—]\\s*", "-");
        s = s.replaceAll("\\s+", " ").toUpperCase();
        // 规范为「前缀 数字」形态（GB2762-2022 → GB 2762-2022），与报告引用口径一致
        s = s.replaceAll("^([A-Z]+(?:/[A-Z]+)?)\\s*(?=\\d)", "$1 ");
        return s;
    }

    /**
     * 字节 → 字符串：优先 UTF-8（严格），失败退回 GBK。
     *
     * <p>为什么需要：国标 TXT 不少是 GB2312/GBK 编码，直接按 UTF-8 读会出现乱码，
     * 污染检索索引。用严格解码器探测（有非法字节即抛），失败再按 GBK 解——覆盖绝大多数情形，
     * 且**不引任何编码探测库**。</p>
     */
    static String decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        // 去 UTF-8 BOM
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        CharsetDecoder utf8 = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return utf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            // 非 UTF-8 → 按 GBK 解（GB2312/GBK/GB18030 兼容区）
            return new String(bytes, java.nio.charset.Charset.forName("GBK"));
        }
    }
}
