package com.lims.service.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 标准正文切块器（feature A，T03，设计 §2.4）。
 *
 * <p>策略：先按**条款边界**（如 {@code 4.2}、{@code 表3}、{@code 第五章}）切段，再把过短段
 * 合并、过长段硬切，使每块落在 **200~500 字**（{@code TARGET_MIN}~{@code TARGET_MAX}），
 * 重叠 0。块小 → 单行小 → 检索命中后取正文、投喂模型的内存与网络开销都受控。</p>
 *
 * <p>页码为**近似值**：按正文中的分页符 {@code \f} / {@code 第 N 页} 标记推进，取不到则为 null。</p>
 */
@Component
public class ChunkSplitter {

    /** 目标块下限（字） */
    private static final int TARGET_MIN = 200;
    /** 目标块上限（字） */
    private static final int TARGET_MAX = 500;

    /** 条款编号开头（如 "4.2 铅限量" / "5 要求"） */
    private static final Pattern CLAUSE = Pattern.compile("^(\\d+(?:\\.\\d+){0,4})[.、\\s]+(.{0,50})$");
    /** 表格/图编号开头（如 "表3 铅限量" / "图1"） */
    private static final Pattern TABLE_FIG = Pattern.compile("^((?:表|图)\\s*\\d+(?:[.\\-]\\d+)?)[.、\\s]*(.{0,40})$");
    /** 章节（"第五章 …"） */
    private static final Pattern CHAPTER = Pattern.compile("^(第[一二三四五六七八九十百千\\d]+[章节条])\\s*(.{0,40})$");
    /** 分页标记 "第 N 页" */
    private static final Pattern PAGE = Pattern.compile("第\\s*(\\d+)\\s*页");
    /**
     * 标题开头被 OCR 空格拆出的数字（如 {@code "121 毒死蜱"} 中的 {@code 121}）。
     * 数字后必须紧跟空白/汉字/行尾，避免误吃以数字开头的名称（如 {@code 2,4-滴丁酸}）。
     */
    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d+(?:\\.\\d+)*)(?=\\s|[\\u4e00-\\u9fa5]|$)");
    /**
     * OCR 会在数字与小数点之间插入空格（{@code "4. 121"}、{@code "0. 05"}）。
     * 识别边界前先还原，否则条款号会被截断成「4」、标题变成「121 毒死蜱」。
     */
    private static final Pattern DOT_SPACE = Pattern.compile("(\\d)\\s*\\.\\s*(\\d)");
    /**
     * 「只有条款号」的行（两行式标题的第一行）。必须含小数点，故 {@code "95"}（页码）不会命中。
     */
    private static final Pattern CLAUSE_NO_ONLY = Pattern.compile("^(\\d{1,2}(?:\\.\\d{1,4}){1,4})[.、]?$");

    /** 切块结果。 */
    public record Chunk(String clauseNo, String clauseTitle, String content, Integer pageNo, int chunkOrder) {
    }

    /** 把整篇正文切成块。空文本返回空列表。 */
    public List<Chunk> split(String text) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String[] lines = mergeTwoLineHeadings(
                text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1));

        StringBuilder buf = new StringBuilder();
        String curClauseNo = null;
        String curTitle = null;
        int page = 1;
        boolean pageSeen = false;
        int order = 0;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.strip();
            // 分页符推进
            if (raw != null && raw.indexOf('\f') >= 0) {
                page++;
                pageSeen = true;
            }
            Matcher pm = PAGE.matcher(line);
            if (pm.find()) {
                page = Integer.parseInt(pm.group(1));
                pageSeen = true;
            }
            if (line.isEmpty()) {
                continue;
            }
            Boundary b = boundaryOf(line);
            if (b != null) {
                if (buf.length() == 0) {
                    // 缓冲为空：直接采用本边界作为当前元数据
                    curClauseNo = b.no();
                    curTitle = b.title();
                } else if (shouldBreak(buf, b)) {
                    order = flush(chunks, buf, curClauseNo, curTitle, pageSeen ? page : null, order);
                    curClauseNo = b.no();
                    curTitle = b.title();
                }
            }
            // 追加本行；超上限则硬切成多块
            if (buf.length() > 0) {
                buf.append('\n');
            }
            buf.append(line);
            while (buf.length() > TARGET_MAX) {
                order = flush(chunks, buf, curClauseNo, curTitle, pageSeen ? page : null, order);
            }
        }
        flush(chunks, buf, curClauseNo, curTitle, pageSeen ? page : null, order);
        return chunks;
    }

    private int flush(List<Chunk> chunks, StringBuilder buf, String clauseNo, String title,
                      Integer page, int order) {
        String content = buf.toString().strip();
        if (!content.isEmpty()) {
            order++;
            chunks.add(new Chunk(clauseNo, title, content, page, order));
        }
        buf.setLength(0);
        return order;
    }

    // =====================================================================
    // OCR 版式归一（2026-09-18）—— 决定条款号能否与内容正确对应
    // =====================================================================

    /**
     * 合并「两行式条款标题」。
     *
     * <h3>为什么必须有</h3>
     * 扫描版国标把条款号与名称**排成两行**（实测 GB 2763-2021 原文）：
     * <pre>
     * 4. 121
     * 毒死蜱（chlorpyrifos）
     * </pre>
     * 单行正则永远拼不出「编号 + 名称」。不合并时编号行被当成「无标题伪边界」丢弃，
     * 于是 <b>4.121 的正文被挂到上一张表的编号下</b>（引用卡片显示「表120」），
     * 且「毒死蜱」再也无法出现在任何块的标题里 → 检索定位不到该条款。
     *
     * @return 归一后的行数组（编号行被 {@code "4.121 毒死蜱（chlorpyrifos）"} 取代，名称行被吞并）
     */
    private static String[] mergeTwoLineHeadings(String[] lines) {
        List<String> out = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            String no = clauseNoOnly(lines[i]);
            if (no != null && i + 1 < lines.length && looksLikeTitleLine(lines[i + 1])) {
                out.add(no + " " + lines[i + 1].strip());
                i++;
                continue;
            }
            out.add(lines[i]);
        }
        return out.toArray(new String[0]);
    }

    /**
     * 该行是否「只有条款号」（OCR 两行式标题的第一行），如 {@code "4. 121"}、{@code "4. 121. 1"}。
     *
     * <p>约束：必须含小数点，且首位不能是 0 —— 否则 {@code "0.01"}、{@code "0.05"} 这类
     * **限量数值**会被误当条款号（表格式文本里极常见）。</p>
     */
    private static String clauseNoOnly(String line) {
        if (line == null) {
            return null;
        }
        String t = normalizeNumbers(line.strip());
        Matcher m = CLAUSE_NO_ONLY.matcher(t);
        if (!m.matches()) {
            return null;
        }
        String no = m.group(1);
        return no.startsWith("0") ? null : no;
    }

    /** 该行是否像「条款名称」（用于与上一行的编号拼成标题）。 */
    private static boolean looksLikeTitleLine(String line) {
        String t = line == null ? "" : line.strip();
        if (t.isEmpty() || t.length() > 40 || t.startsWith("=====")) {
            return false;
        }
        if (clauseNoOnly(t) != null) {
            return false;
        }
        // 必须含汉字/字母：纯数字行（页码、限值）不是名称
        return hasWordChar(t);
    }

    private record Boundary(String no, String title) {
    }

    /**
     * 是否在边界处切分（2026-09-18 修复「条款归属错误」）。
     *
     * <h3>问题</h3>
     * 旧实现只在「缓冲已达 {@link #TARGET_MIN}」时才切分，于是**短缓冲里的真实条款标题被吞掉**：
     * 表格若干行（不足 200 字）之后出现「4.121 毒死蜱（chlorpyrifos）」，该行既不切分也不更新元数据，
     * 结果 4.121 的正文被挂在**上一张表的编号**（如「表119」）下 —— 引用卡片会显示错误的条款号。
     * 对出 CMA/CATL 报告的合规系统，**错误归属比没有归属更危险**。
     *
     * <h3>判据</h3>
     * <ul>
     *   <li><b>带标题的边界</b>（真实条款/表标题）：无论缓冲多短都切分，保证「编号 ↔ 内容」一致；</li>
     *   <li><b>仅编号无标题</b>：沿用旧策略（缓冲够长才切），避免碎片化。</li>
     * </ul>
     */
    private static boolean shouldBreak(StringBuilder buf, Boundary b) {
        if (buf.length() == 0) {
            return false;
        }
        return b.title() != null || buf.length() >= TARGET_MIN;
    }

    private Boundary boundaryOf(String line) {
        String normalized = normalizeNumbers(line);
        Matcher c = CLAUSE.matcher(normalized);
        if (c.matches()) {
            return boundary(c.group(1), c.group(2));
        }
        Matcher t = TABLE_FIG.matcher(normalized);
        if (t.matches()) {
            return boundary(t.group(1).replaceAll("\\s+", ""), t.group(2));
        }
        Matcher ch = CHAPTER.matcher(normalized);
        if (ch.matches()) {
            return boundary(ch.group(1), ch.group(2));
        }
        return null;
    }

    /** 还原 OCR 在「数字 . 数字」之间插入的空格；反复替换以处理 {@code "4. 121. 4"} 这类多重拆散。 */
    private static String normalizeNumbers(String line) {
        String prev;
        String cur = line == null ? "" : line;
        do {
            prev = cur;
            cur = DOT_SPACE.matcher(prev).replaceAll("$1.$2");
        } while (!cur.equals(prev));
        return cur;
    }

    /**
     * 组装边界，并**剔除伪边界**（2026-09-18 修复）。
     *
     * <h3>为什么必须剔除</h3>
     * OCR 正文里大量出现「页眉/目录」残留行，形态恰好命中条款正则，例如 {@code "4 1"}、
     * {@code "4. 15"} 会被解析成 {@code clauseNo="4", clauseTitle="1"}。
     * 实测 GB 2763-2021 切出 1269 块，其中 **824 块（65%）的 clauseTitle 是这种纯数字噪声** ——
     * 引用卡片上会显示「4 · 1」这类无意义标题，用户无法据此定位条目。
     *
     * <h3>判据</h3>
     * 标题**非空且不含任何中日韩文字或拉丁字母**（纯数字/标点/空白）⇒ 判为伪边界，
     * 该行降级为普通正文参与聚合。空标题不剔除：{@code "4.121"} 单独成行是真实的条款起始行。
     */
    private Boundary boundary(String no, String rawTitle) {
        String title = rawTitle == null ? "" : rawTitle.strip();
        if (!title.isEmpty() && !hasWordChar(title)) {
            return null;
        }
        // 还原被 OCR 空格拆散的条款号（2026-09-18）：
        // 原文「4.121 毒死蜱」在扫描件里常被识别成「4. 121 毒死蜱」，正则只能取到编号「4」，
        // 于是标题变成「121 毒死蜱…」，卡片上会显示成「4 · 121毒死蜱」这种错位编号。
        // 这里把标题开头的数字并回编号，得到正确的「4.121 · 毒死蜱」。
        // 判据：数字之后必须紧跟空白、汉字或行尾——避免误吃「2,4-滴丁酸」这类以数字开头的名称。
        Matcher lead = LEADING_NUMBER.matcher(title);
        if (lead.find()) {
            String merged = no + "." + lead.group(1);
            title = title.substring(lead.end()).strip();
            return new Boundary(merged, title.isEmpty() ? null : title);
        }
        return new Boundary(no, title.isEmpty() ? null : title);
    }

    /** 是否含「词字符」：中日韩统一表意文字、假名或拉丁字母。 */
    private static boolean hasWordChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(s.charAt(i));
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL
                    || script == Character.UnicodeScript.LATIN) {
                return true;
            }
        }
        return false;
    }
}
