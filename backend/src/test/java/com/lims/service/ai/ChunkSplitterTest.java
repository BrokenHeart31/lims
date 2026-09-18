package com.lims.service.ai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 标准正文切块器单元测试（feature A，T03，设计 §2.4）。
 *
 * <p>固化：空文本 → 空；按**条款边界**（如 {@code 4.2}）切段并携带 clauseNo；
 * 每块内容非空。</p>
 */
class ChunkSplitterTest {

    private final ChunkSplitter splitter = new ChunkSplitter();

    @Test
    @DisplayName("空/null 文本 → 空块")
    void emptyText() {
        assertTrue(splitter.split(null).isEmpty());
        assertTrue(splitter.split("   ").isEmpty());
    }

    @Test
    @DisplayName("★按条款边界切段：边界前的段落归属上一 clause，边界后的归属新 clause")
    void clauseBoundary() {
        StringBuilder sb = new StringBuilder();
        sb.append("第一章 范围\n");
        sb.append("x".repeat(260)).append('\n');   // 撑过 TARGET_MIN(200) 触发边界 flush
        sb.append("4.2 铅限量\n");
        sb.append("y".repeat(100)).append('\n');

        List<ChunkSplitter.Chunk> chunks = splitter.split(sb.toString());

        assertEquals(2, chunks.size());
        assertEquals("第一章", chunks.get(0).clauseNo());
        assertEquals("4.2", chunks.get(1).clauseNo());
        assertEquals("铅限量", chunks.get(1).clauseTitle());
        assertTrue(chunks.get(1).content().contains("铅限量"));
        chunks.forEach(c -> assertFalse(c.content().isBlank()));
    }

    @Test
    @DisplayName("无边界纯文本 → 合成单块")
    void singleChunk() {
        List<ChunkSplitter.Chunk> chunks = splitter.split("这是一段没有条款编号的正文，用于验证兜底切块。");
        assertEquals(1, chunks.size());
        assertEquals(1, chunks.get(0).chunkOrder());
    }

    @Test
    @DisplayName("★伪边界剔除：页眉/目录残留行（\"4 15\"）不得被当成条款标题")
    void pseudoBoundaryRejected() {
        // 实测背景：GB 2763-2021 OCR 文本里有大量这种行，旧实现切出 1269 块中 824 块
        // 标题是纯数字（如 clauseNo=4 / title=15），引用卡片上显示「4 · 15」这种无意义标题。
        StringBuilder sb = new StringBuilder();
        sb.append("4.121 毒死蜱（chlorpyrifos）\n");
        sb.append("z".repeat(260)).append('\n');   // 撑过 TARGET_MIN
        sb.append("4 15\n");                        // 伪边界：标题纯数字
        sb.append("w".repeat(260)).append('\n');
        sb.append("4.122 敌敌畏（dichlorvos）\n");
        sb.append("y".repeat(60)).append('\n');

        List<ChunkSplitter.Chunk> chunks = splitter.split(sb.toString());

        // 伪边界不切段 ⇒ 不会产生 clauseNo="4"/title="15" 的块
        assertTrue(chunks.stream().noneMatch(c -> "15".equals(c.clauseTitle())),
                "纯数字标题的伪边界应被剔除，实际块标题：" + chunks.stream()
                        .map(ChunkSplitter.Chunk::clauseTitle).toList());
        // 真实边界仍然生效（即便标题为空也不剔除）
        assertTrue(chunks.stream().anyMatch(c -> "4.121".equals(c.clauseNo())));
        assertTrue(chunks.stream().anyMatch(c -> "4.122".equals(c.clauseNo())));
    }

    @Test
    @DisplayName("★标题归一化：有编号无标题的行 → clauseTitle 为 null（而非空串）")
    void blankTitleBecomesNull() {
        StringBuilder sb = new StringBuilder();
        sb.append("x".repeat(260)).append('\n');
        sb.append("5.3、\n");               // 真实边界（编号 + 分隔符），但本行无标题
        sb.append("y".repeat(60)).append('\n');

        List<ChunkSplitter.Chunk> chunks = splitter.split(sb.toString());

        ChunkSplitter.Chunk c = chunks.stream()
                .filter(x -> "5.3".equals(x.clauseNo())).findFirst().orElseThrow();
        assertEquals(null, c.clauseTitle());
    }

    @Test
    @DisplayName("★OCR 条款号还原：\"4. 121 毒死蜱（chlorpyrifos）\" → clauseNo=4.121 / title=毒死蜱（chlorpyrifos）")
    void ocrSplitClauseNoIsRestored() {
        // 实测背景：扫描件把「4.121」识别成「4. 121」，旧实现只取到编号 4，
        // 标题变成「121 毒死蜱…」，引用卡片显示成「4 · 121毒死蜱」这种错位编号。
        StringBuilder sb = new StringBuilder();
        sb.append("x".repeat(260)).append('\n');
        sb.append("4. 121 毒死蜱（chlorpyrifos）\n");
        sb.append("4. 121. 4 最大残留限量：应符合表121的规定。\n");
        sb.append("y".repeat(60)).append('\n');

        List<ChunkSplitter.Chunk> chunks = splitter.split(sb.toString());

        ChunkSplitter.Chunk c = chunks.stream()
                .filter(x -> "4.121".equals(x.clauseNo())).findFirst().orElseThrow();
        assertEquals("毒死蜱（chlorpyrifos）", c.clauseTitle());
        // 多重拆散（"4. 121. 4"）也要还原
        assertTrue(chunks.stream().anyMatch(x -> "4.121.4".equals(x.clauseNo())),
                "实际编号：" + chunks.stream().map(ChunkSplitter.Chunk::clauseNo).toList());
    }

    @Test
    @DisplayName("★两行式条款标题（OCR 国标版式）：编号行 + 名称行 → 正确归属与标题")
    void twoLineClauseHeading() {
        // 实测背景（GB 2763-2021 原文）：条款号与名称分列两行，
        // 旧实现把编号行当伪边界丢弃 ⇒ 4.121 正文被挂到「表120」下，且「毒死蜱」不出现在任何块标题里。
        StringBuilder sb = new StringBuilder();
        sb.append("表120（续） 食品类别/名称 最大残留限量，mg/kg\n");
        sb.append("饮料类 0. 01*\n");
        sb.append("4. 121\n");
        sb.append("毒死蜱（chlorpyrifos）\n");
        sb.append("4. 121. 4\n");
        sb.append("最大残留限量：应符合表121的规定。\n");
        sb.append("表121 食品类别/名称 最大残留限量，mg/kg\n");
        sb.append("蔬菜 黄瓜 0. 05\n");

        List<ChunkSplitter.Chunk> chunks = splitter.split(sb.toString());

        ChunkSplitter.Chunk c = chunks.stream()
                .filter(x -> "4.121".equals(x.clauseNo())).findFirst().orElseThrow();
        assertEquals("毒死蜱（chlorpyrifos）", c.clauseTitle());
        assertTrue(c.content().contains("毒死蜱"), "4.121 块内容：" + c.content());
        assertTrue(chunks.stream().anyMatch(x -> "4.121.4".equals(x.clauseNo())),
                "实际编号：" + chunks.stream().map(ChunkSplitter.Chunk::clauseNo).toList());
        // 上一张表的内容不得被 4.121 抢走
        assertTrue(chunks.stream().anyMatch(x -> "表120".equals(x.clauseNo())
                && x.content().contains("饮料类")));
    }
}
