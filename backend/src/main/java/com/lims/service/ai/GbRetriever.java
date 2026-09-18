package com.lims.service.ai;

import com.lims.mapper.GbClauseMapper;
import com.lims.mapper.GbDocumentMapper;
import com.lims.vo.GbSearchHitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GB 标准检索器（feature A，T03 / PRD T2、A-05）—— **只查倒排索引，绝不读文件**。
 *
 * <p>构造 MySQL BOOLEAN 模式查询串：把问题切成词，默认 **AND 语义**（每词前缀 {@code +}），
 * 命中为 0 时**自动降级为 OR 语义**（词间空格）；排序由 SQL 的 {@code MATCH()…AS score} 降序完成。</p>
 *
 * <h3>⚠️ ngram 实测坑（实现依据，务必保留）</h3>
 * <ul>
 *   <li><b>单字被忽略</b>：{@code ngram_token_size=2}，长度 1 的检索词不产生 token，搜不到。
 *       故本类**丢弃单字词**，并暴露 {@link #usableTerms} 供上层提示「请补充 ≥2 字关键词」。</li>
 *   <li><b>{@code innodb_ft_min_token_size=3} 对 ngram 不生效</b>：别据此误判「3 字以下搜不到」——
 *       实际最小粒度由 {@code ngram_token_size}=2 决定。</li>
 *   <li><b>{@code MATCH()} 列必须与 FULLTEXT 索引列完全一致</b>：这里都只匹配 {@code content}
 *       （见 {@code GbClauseMapper.xml}），否则报 1191；{@code std_no} 过滤放 WHERE。</li>
 *   <li><b>{@code +} 与 {@code "短语"}</b>：{@code +词} 必须包含；{@code "词 词"} 相邻短语。
 *       本类默认 {@code +}（精确优先），0 命中再降级。</li>
 *   <li><b>长中文句</b>（无空格）会被当成**一个词** → AND 全部 bigram 过严 → 命中 0 → 触发 OR 降级
 *       （等价于「任一 bigram 命中」），这是刻意的召回兜底。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GbRetriever {

    /** BOOLEAN 模式的运算符字符，需从词中剔除，避免被误当语法。 */
    private static final String OPERATOR_CHARS = "+-<>()~*\"@";

    /** 参与布尔层的「关键词」长度上限：超长即视为整句，改走自然语言模式。 */
    private static final int MAX_KEYWORD_LEN = 12;

    /**
     * 检索噪声词：标准号前缀与纯数字片段。
     *
     * <p>它们几乎出现在每个切块里（页眉「GB 2763—2021」），在布尔 OR 层会造成
     * 「命中很多、全不相关」——实测一次整句提问命中 968 条噪声、正确条款未被召回。</p>
     */
    private static final Set<String> NOISE_TERMS = Set.of(
            "gb", "gbt", "gbz", "sn", "ny", "jy", "db", "mg", "kg", "ml", "ug");

    /**
     * 标准号识别：{@code GB} 或 {@code GB/T}、{@code GB/Z} + 编号（可带年份）。
     *
     * <p>用于回答「用户点名了哪份标准」——点名了库中未收录的标准时必须**诚实说明**，
     * 绝不能拿别的标准（如 GB 2763）的内容去凑答案（2026-09-18 实测：问 GB 2762 铅限量，
     * 检索器返回了 5 条 GB 2763 的无关条款，属于会误导检验人员的错误行为）。</p>
     *
     * <p>兼容 OCR 文本里的破折号（{@code —}）与无空格写法（{@code GB2763—2021}）。</p>
     */
    private static final Pattern STD_NO = Pattern.compile(
            "(?i)GB\\s*(?:/\\s*([TZ]))?\\s*([0-9]{2,5}(?:\\.[0-9]+)*)(?:\\s*[-—–]\\s*([0-9]{4}))?");

    private final GbClauseMapper gbClauseMapper;
    private final GbDocumentMapper gbDocumentMapper;
    private final AiProperties props;

    /** 以默认 topN 检索（无标准号过滤）。 */
    public List<GbSearchHitVO> search(String query) {
        return search(query, props.getTopN(), null);
    }

    /**
     * 库内**已收录**的标准号原始值（如 {@code GB 2763-2021}），供「是否收录」判定与提示。
     * 只取已生效（status=1）且未删除的文档。
     */
    public List<String> indexedStdNos() {
        return gbDocumentMapper.selectIndexedStdNos();
    }

    /**
     * 检索标准条款。
     *
     * @param query 用户检索词/问题
     * @param topN  返回条数
     * @param stdNo 限定标准号（可空）
     * @return 命中列表（按 score 降序）；无可检索词或无命中返回空列表（**绝不编造**）
     */
    public List<GbSearchHitVO> search(String query, int topN, String stdNo) {
        List<String> terms = usableTerms(query);
        // 噪声词从布尔层剔除（2026-09-18 修复）：标准号前缀与纯数字几乎出现在**每个块**里
        // （每页页眉都有「GB 2763—2021」），一旦参与 OR，就会用海量无关命中淹没有效结果。
        // 实测：「GB 2763-2021 里毒死蜱的限量是怎么规定的」→ 布尔 OR 命中 968 条、
        // 正确的 4.121 毒死蜱 块 score=0 完全未被召回（答非所问的直接原因）。
        List<String> keywords = terms.stream().filter(t -> !isNoiseTerm(t)).toList();

        // 第一层：**用户已分词**（≥2 个有意义短词）才走 BOOLEAN（AND → OR）。
        // 为什么整句不能走布尔层：BOOLEAN 模式下多字中文词被当**短语**（要求词序邻接），
        // 整句必然 0 命中；而整句里混入的「GB」「2763-2021」又会用海量 OR 命中把结果带偏。
        // 故整句一律交给下面的自然语言层（按 ngram 拆句 + 相关度排序），语义才对。
        if (keywords.size() >= 2 && keywords.stream().allMatch(t -> t.length() <= MAX_KEYWORD_LEN)) {
            String andQuery = buildAnd(keywords);
            List<GbSearchHitVO> hits = gbClauseMapper.searchByNgram(andQuery, stdNo, topN);
            if (!hits.isEmpty()) {
                return hits;
            }
            // 第二层：布尔 OR（任一命中即返回）——适合多词输入
            String orQuery = buildOr(keywords);
            if (!orQuery.equals(andQuery)) {
                hits = gbClauseMapper.searchByNgram(orQuery, stdNo, topN);
                if (!hits.isEmpty()) {
                    log.debug("[ai] GB 检索 AND 无命中，降级 OR：{}", orQuery);
                    return hits;
                }
            }
        }
        // 第三层（整句主通道）：**自然语言模式**。
        // 为什么必须有：布尔模式下「不含空格的整句中文」被当短语（要求词序邻接）⇒ 必然 0 命中，
        // 而用户提问天然是整句（「毒死蜱在黄瓜上的限量是多少」）。实测同一整句：布尔 0 条 / 自然语言 844 条。
        // 自然语言模式按 ngram 拆整句并按 tf-idf 排序，常见 bigram（如「GB」）权重自然被压低。
        String natural = clean(query == null ? "" : query);
        if (natural.isEmpty()) {
            return List.of();
        }
        List<GbSearchHitVO> naturalHits = gbClauseMapper.searchByNatural(natural, stdNo, topN);
        if (!naturalHits.isEmpty()) {
            log.debug("[ai] GB 检索走自然语言模式：{}", natural);
        }
        return naturalHits;
    }

    // =====================================================================
    // 查询串构造（纯函数，可单测）
    // =====================================================================

    /** 切词并清洗：按空白/常见标点分词，剔除单字词（ngram 忽略）与纯运算符词。 */
    public static List<String> usableTerms(String query) {
        List<String> terms = new ArrayList<>();
        if (query == null) {
            return terms;
        }
        for (String raw : query.split("[\\s,，。；;、:：!！?？()（）\\[\\]【】\"“”'‘’/|]+")) {
            String t = clean(raw);
            if (t.length() >= 2) {
                terms.add(t);
            }
        }
        return terms;
    }

    /** 是否噪声词（标准号前缀 / 纯数字），不参与布尔层。 */
    static boolean isNoiseTerm(String term) {
        if (term == null || term.isBlank()) {
            return true;
        }
        String normalized = term.toLowerCase(Locale.ROOT).replace("/", "").replace(" ", "");
        if (NOISE_TERMS.contains(normalized)) {
            return true;
        }
        // 纯数字/带小数与连字符的数字串（标准号年份、限量值片段）
        return term.matches("[0-9\\-—–.]+");
    }

    /** AND 语义：每个词前加 {@code +}（都必须包含）。 */
    static String buildAnd(List<String> terms) {
        StringBuilder sb = new StringBuilder();
        for (String t : terms) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append('+').append(t);
        }
        return sb.toString();
    }

    /** OR 语义：词间空格（任一包含即命中）。 */
    static String buildOr(List<String> terms) {
        return String.join(" ", terms);
    }

    // =====================================================================
    // 标准号识别（纯函数，可单测）
    // =====================================================================

    /**
     * 抽取问题中提到的标准号，并规范化为 {@code "GB 2763"} 形式（**去掉年份**，便于跨年比对）。
     *
     * <p>例：{@code "GB2763—2021 里毒死蜱的限量"} → {@code ["GB 2763"]}；
     * {@code "GB/T 5009.12 铅的测定"} → {@code ["GB/T 5009.12"]}。按出现顺序、去重。</p>
     */
    public static List<String> mentionedStdNos(String query) {
        List<String> found = new ArrayList<>();
        if (query == null || query.isBlank()) {
            return found;
        }
        Matcher m = STD_NO.matcher(query);
        while (m.find()) {
            String key = keyOf(m.group(1), m.group(2));
            if (!found.contains(key)) {
                found.add(key);
            }
        }
        return found;
    }

    /** 把库内标准号原始值（{@code GB 2763-2021}）规范化为比对键（{@code GB 2763}）。 */
    public static String baseKey(String stdNo) {
        if (stdNo == null) {
            return "";
        }
        Matcher m = STD_NO.matcher(stdNo);
        return m.find() ? keyOf(m.group(1), m.group(2)) : stdNo.strip().toUpperCase();
    }

    private static String keyOf(String type, String number) {
        String norm = number == null ? "" : number.replaceAll("\\.$", "");
        return type == null ? "GB " + norm : "GB/" + type.toUpperCase() + " " + norm;
    }

    private static String clean(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (OPERATOR_CHARS.indexOf(c) >= 0) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString().trim();
    }
}
