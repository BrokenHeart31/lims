package com.lims.service.ai.parser;

import org.springframework.stereotype.Component;

/**
 * 纯文本解析器（feature A，T03）。
 *
 * <p>国标 TXT 常见 UTF-8 或 GBK 编码，统一走 {@link DocumentParser#decode} 探测解码。</p>
 */
@Component
public class TxtParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        return "txt".equals(ext);
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        String text = DocumentParser.decode(bytes);
        String head = text.length() > 200 ? text.substring(0, 200) : text;
        return new ParsedDocument(DocumentParser.extractStdNo(fileName, head), firstTitle(text), text);
    }

    /**
     * 取标准名称（尽力而为，识别不到返回 null）。
     *
     * <h3>2026-09-18 修复</h3>
     * 旧实现取「正文首个非空行」，而 OCR 并档产物的首行是 {@code # source: xxx.pdf（OCR 通道…）}
     * 这类**注释**，于是 {@code gb_document.std_title} 被写成注释内容，在「AI 知识库」页面上
     * 显示为一行带 {@code # source:} 的说明文字——既不是标准名，也误导用户以为是标题。
     *
     * <h3>策略（先精确、后退回）</h3>
     * <ol>
     *   <li>优先用国标封面版式：定位「（中华人民共和国）国家标准」标记行，取其后的**中文名称行**
     *       （跳过 {@code ICS}/{@code GB xxx}/{@code 代替…}/{@code 发布·实施日期} 等版式行），
     *       遇到英文标题行即停止。例：{@code 食品安全国家标准} + {@code 食品中农药最大残留限量}
     *       → {@code "食品安全国家标准 食品中农药最大残留限量"}。</li>
     *   <li>取不到则退回「首个有意义行」：跳过注释行（{@code #}）、分页标记（{@code ===== page N =====}）
     *       与纯数字行。</li>
     * </ol>
     */
    static String firstTitle(String text) {
        if (text == null) {
            return null;
        }
        String[] lines = text.split("\\r?\\n");

        int marker = -1;
        for (int i = 0; i < lines.length && i < 60; i++) {
            if (lines[i].strip().contains("国家标准")) {
                marker = i;
                break;
            }
        }
        if (marker >= 0) {
            StringBuilder sb = new StringBuilder();
            int taken = 0;
            for (int i = marker + 1; i < lines.length && i <= marker + 8; i++) {
                String t = lines[i].strip();
                if (t.isEmpty() || isNoiseLine(t)) {
                    continue;
                }
                // 中文名之后的英文标题行/发布单位行：停止，避免把英文名接进来
                if (!containsHan(t)) {
                    break;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(t);
                // 国标名称一般为 1~2 行（「食品安全国家标准」+ 具体名称）；
                // 注意判据是**收集行数**而非累计字数——首行「食品安全国家标准」本身就够长，
                // 用字数判据会在只拿到通用前缀时就提前收工。
                if (++taken >= 2) {
                    break;
                }
            }
            if (sb.length() > 0) {
                return cut(sb.toString());
            }
        }

        for (String line : lines) {
            String t = line.strip();
            if (t.isEmpty() || isNoiseLine(t)) {
                continue;
            }
            return cut(t);
        }
        return null;
    }

    /** 版式行/注释行：不是标准名，参与抽取只会污染结果。 */
    private static boolean isNoiseLine(String t) {
        if (t.startsWith("#") || t.startsWith("=") || t.startsWith("代替")) {
            return true;
        }
        if (t.toUpperCase().startsWith("ICS") || t.toUpperCase().startsWith("GB")) {
            return true;
        }
        // 发布/实施日期、页码等
        return t.matches(".*\\d{4}\\s*[-—–/]\\s*\\d{2}\\s*[-—–/]\\s*\\d{2}.*")
                || t.matches("^[0-9\\s.\\-—–]+$");
    }

    private static boolean containsHan(String s) {
        return s.codePoints().anyMatch(cp ->
                Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN);
    }

    private static String cut(String s) {
        return s.length() > 120 ? s.substring(0, 120) : s;
    }
}
