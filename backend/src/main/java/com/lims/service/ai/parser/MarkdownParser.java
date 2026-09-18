package com.lims.service.ai.parser;

import org.springframework.stereotype.Component;

/**
 * Markdown 解析器（feature A，T03）。
 *
 * <p>做轻量清洗：去掉 ATX 标题的 {@code #}、强调标记、行内代码反引号、图片/链接语法壳，
 * 保留**可见文本**。不做完整 Markdown 渲染（那是前端的事，且会引入依赖），
 * 目的只是让检索正文不含语法噪声。</p>
 */
@Component
public class MarkdownParser implements DocumentParser {

    @Override
    public boolean supports(String ext) {
        return "md".equals(ext) || "markdown".equals(ext);
    }

    @Override
    public ParsedDocument parse(String fileName, byte[] bytes) {
        String raw = DocumentParser.decode(bytes);
        String text = strip(raw);
        String head = text.length() > 200 ? text.substring(0, 200) : text;
        return new ParsedDocument(DocumentParser.extractStdNo(fileName, head), TxtParser.firstTitle(text), text);
    }

    /** 轻量 Markdown → 纯文本。 */
    static String strip(String md) {
        if (md == null) {
            return "";
        }
        String s = md;
        s = s.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");        // ATX 标题
        s = s.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");   // 图片 → alt
        s = s.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");    // 链接 → 文字
        s = s.replaceAll("`{1,3}", "");                          // 行内/围栏代码标记
        s = s.replaceAll("(?m)^\\s{0,3}>\\s?", "");              // 引用块
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "");              // 无序列表
        s = s.replaceAll("\\*{1,3}([^*]+)\\*{1,3}", "$1");       // 强调
        s = s.replaceAll("_{1,3}([^_]+)_{1,3}", "$1");           // 强调
        return s;
    }
}
