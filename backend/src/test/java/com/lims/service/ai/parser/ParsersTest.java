package com.lims.service.ai.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 正文解析器单元测试（feature A，T03，设计 §2.5）。
 *
 * <p>覆盖：标准号识别与规范化、UTF-8/GBK 解码探测、CSV 单元格「列: 值」化、
 * HTML 去脚本、Markdown 去语法、扩展名识别。</p>
 */
class ParsersTest {

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("扩展名识别：小写、无点；无扩展名返回空")
    void extOf() {
        assertAll(
                () -> assertEquals("txt", DocumentParser.extOf("a.b.TXT")),
                () -> assertEquals("", DocumentParser.extOf("noext")),
                () -> assertEquals("", DocumentParser.extOf("trailing."))
        );
    }

    @Test
    @DisplayName("★标准号：从文件名识别并规范化为「GB 2762-2022」")
    void stdNoFromFileName() {
        ParsedDocument p = new TxtParser().parse("GB 2762-2022 食品中污染物限量.txt",
                utf8("食品中污染物限量\n正文正文正文"));
        assertEquals("GB 2762-2022", p.stdNo());
    }

    @Test
    @DisplayName("★标准号：无空格形态 GB2762-2022 → 规范化为 GB 2762-2022")
    void stdNoNormalize() {
        ParsedDocument p = new TxtParser().parse("GB2762-2022.txt", utf8("正文"));
        assertEquals("GB 2762-2022", p.stdNo());
    }

    @Test
    @DisplayName("TXT：首行作为标准名称")
    void txtTitle() {
        ParsedDocument p = new TxtParser().parse("a.txt", utf8("食品中污染物限量\n第二行"));
        assertEquals("食品中污染物限量", p.stdTitle());
    }

    @Test
    @DisplayName("★TXT 标题：跳过 OCR 并档的 # source 注释与 ===== page N ===== 分页标记")
    void txtTitleSkipsOcrComments() {
        String text = "# source: GB2763-2021-ys.pdf（OCR 通道；数值请以系统标准库为准）\n"
                + "\n"
                + "===== page 1 =====\n"
                + "ICS 65.100\n"
                + "中华人民共和国国家标准\n"
                + "GB 2763—2021\n"
                + "代替GB2763—2019\n"
                + "食品安全国家标准\n"
                + "食品中农药最大残留限量\n"
                + "National food safety standard-\n";
        ParsedDocument p = new TxtParser().parse("GB2763-2021-ys.ocr.txt", utf8(text));
        // 修复前：标题被写成 "# source: GB2763-2021-ys.pdf（OCR 通道…）"
        assertEquals("食品安全国家标准 食品中农药最大残留限量", p.stdTitle());
        assertFalse(p.stdTitle().startsWith("#"));
    }

    @Test
    @DisplayName("★TXT 标题：无国标封面版式时，退回首个有意义行（跳过注释/分页/纯数字）")
    void txtTitleFallback() {
        String text = "# source: x.pdf\n===== page 1 =====\n2021-03-03\n食品中污染物限量\n";
        assertEquals("食品中污染物限量", new TxtParser().parse("x.txt", utf8(text)).stdTitle());
    }

    @Test
    @DisplayName("★解码：UTF-8 正常；非 UTF-8 字节回退 GBK（不定库探测）")
    void decodeUtf8AndGbk() {
        assertEquals("中文", DocumentParser.decode(utf8("中文")));
        // 「中文」的 GBK 字节（D6 D0 CE C4）非合法 UTF-8 → 回退 GBK
        byte[] gbk = new byte[]{(byte) 0xD6, (byte) 0xD0, (byte) 0xCE, (byte) 0xC4};
        assertEquals("中文", DocumentParser.decode(gbk));
    }

    @Test
    @DisplayName("★CSV：单元格转「列: 值」，支撑 ngram 检索命中")
    void csvCells() {
        ParsedDocument p = new CsvParser().parse("t.csv", utf8("项目,限量\n铅,0.5\n"));
        assertTrue(p.text().contains("项目: 铅"));
        assertTrue(p.text().contains("限量: 0.5"));
    }

    @Test
    @DisplayName("★HTML：移除 script/style，标题取 <title>")
    void htmlRemovesScript() {
        String html = "<html><head><title>国标</title></head><body>"
                + "<script>var x = 1;</script><p>铅限量 0.5 mg/kg</p></body></html>";
        ParsedDocument p = new HtmlParser().parse("t.html", utf8(html));
        assertEquals("国标", p.stdTitle());
        assertTrue(p.text().contains("铅限量 0.5"));
        assertFalse(p.text().contains("var x"));
    }

    @Test
    @DisplayName("Markdown：去标题/强调语法，保留可见文本")
    void markdownStrips() {
        ParsedDocument p = new MarkdownParser().parse("t.md", utf8("# 铅限量\n**重点** 0.5"));
        assertTrue(p.text().contains("铅限量"));
        assertTrue(p.text().contains("重点"));
        assertFalse(p.text().contains("#"));
        assertFalse(p.text().contains("**"));
    }

    @Test
    @DisplayName("扩展名支持矩阵")
    void supports() {
        assertAll(
                () -> assertTrue(new TxtParser().supports("txt")),
                () -> assertFalse(new TxtParser().supports("md")),
                () -> assertTrue(new HtmlParser().supports("htm")),
                () -> assertTrue(new MarkdownParser().supports("markdown")),
                () -> assertTrue(new CsvParser().supports("tsv"))
        );
    }
}
