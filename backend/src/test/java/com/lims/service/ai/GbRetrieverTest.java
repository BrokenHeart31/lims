package com.lims.service.ai;

import com.lims.mapper.GbClauseMapper;
import com.lims.mapper.GbDocumentMapper;
import com.lims.vo.GbSearchHitVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GB 检索器单元测试（feature A，T03）—— 固化 ngram 查询构造与 AND→OR 降级。
 *
 * <p>覆盖的「坑」：单字词被 ngram 忽略 → 丢弃；BOOLEAN 运算符字符需剔除；
 * AND 无命中自动降级 OR；无可检索词直接返回空（**绝不编造**）。</p>
 */
class GbRetrieverTest {

    private GbClauseMapper mapper;
    private GbDocumentMapper documentMapper;
    private GbRetriever retriever;

    @BeforeEach
    void setUp() {
        mapper = mock(GbClauseMapper.class);
        documentMapper = mock(GbDocumentMapper.class);
        AiProperties props = new AiProperties();
        props.setTopN(5);
        retriever = new GbRetriever(mapper, documentMapper, props);
    }

    @Test
    @DisplayName("★噪声词识别：标准号前缀与纯数字不得进入布尔层")
    void noiseTerms() {
        assertAll(
                () -> assertTrue(GbRetriever.isNoiseTerm("GB")),
                () -> assertTrue(GbRetriever.isNoiseTerm("gb")),
                () -> assertTrue(GbRetriever.isNoiseTerm("GB/T")),
                () -> assertTrue(GbRetriever.isNoiseTerm("2763-2021")),
                () -> assertTrue(GbRetriever.isNoiseTerm("0.05")),
                () -> assertEquals(false, GbRetriever.isNoiseTerm("毒死蜱")),
                () -> assertEquals(false, GbRetriever.isNoiseTerm("水产品"))
        );
    }

    @Test
    @DisplayName("★检索路由：整句提问走自然语言层（布尔层会被噪声词淹没）；已分词短词仍走布尔层")
    void routing() {
        // 实测背景：「GB 2763-2021 里毒死蜱的限量是怎么规定的」在布尔 OR 下命中 968 条噪声，
        // 正确的 4.121 毒死蜱 块 score=0 完全未被召回 —— 必须改走自然语言模式。
        when(mapper.searchByNatural(anyString(), any(), anyInt())).thenReturn(List.of());
        retriever.search("GB 2763-2021 里毒死蜱的限量是怎么规定的", 5, "GB 2763-2021");
        verify(mapper, never()).searchByNgram(anyString(), any(), anyInt());
        verify(mapper, times(1)).searchByNatural(anyString(), any(), anyInt());

        // 已分词短词（用户用空格分开的检索词）：仍走布尔 AND，保持精确优先
        GbSearchHitVO h = new GbSearchHitVO();
        h.setStdNo("GB 2763-2021");
        when(mapper.searchByNgram("+水产品 +限量", null, 5)).thenReturn(List.of(h));
        assertEquals(1, retriever.search("水产品 限量", 5, null).size());
        verify(mapper, times(1)).searchByNgram("+水产品 +限量", null, 5);
    }

    @Test
    @DisplayName("★标准号识别：归一化为「GB 编号」（去年份），兼容无空格与 OCR 破折号")
    void mentionedStdNos() {
        assertAll(
                () -> assertEquals(List.of("GB 2762"), GbRetriever.mentionedStdNos("GB 2762 铅的限量是多少？")),
                () -> assertEquals(List.of("GB 2763"), GbRetriever.mentionedStdNos("GB2763—2021 里毒死蜱的限量")),
                () -> assertEquals(List.of("GB/T 5009.12"), GbRetriever.mentionedStdNos("按 GB/T 5009.12 测定铅")),
                // 一次点名多份：按出现顺序、去重
                () -> assertEquals(List.of("GB 2762", "GB 2763"),
                        GbRetriever.mentionedStdNos("GB 2762 与 GB 2763-2021 的区别")),
                // 没点名标准 → 空（不得凭空认定用户问了某标准）
                () -> assertTrue(GbRetriever.mentionedStdNos("铅的限量是多少").isEmpty()),
                () -> assertTrue(GbRetriever.mentionedStdNos(null).isEmpty())
        );
    }

    @Test
    @DisplayName("★标准号比对键：库内原始值与用户提问的规范化结果必须一致（跨年份可比）")
    void baseKey() {
        assertAll(
                () -> assertEquals("GB 2763", GbRetriever.baseKey("GB 2763-2021")),
                () -> assertEquals("GB 2763", GbRetriever.baseKey("GB2763—2021")),
                () -> assertEquals("GB/T 5009.12", GbRetriever.baseKey("GB/T 5009.12-2017")),
                // 用户说 GB 2763（不带年份）能与库内 GB 2763-2021 匹配上
                () -> assertEquals(GbRetriever.baseKey("GB 2763-2021"), "GB 2763")
        );
    }

    @Test
    @DisplayName("库内已收录标准号：只透传 mapper 结果，不做本地缓存（避免导入后读到陈旧清单）")
    void indexedStdNos() {
        when(documentMapper.selectIndexedStdNos()).thenReturn(List.of("GB 2763-2021"));
        assertEquals(List.of("GB 2763-2021"), retriever.indexedStdNos());
        verify(documentMapper, times(1)).selectIndexedStdNos();
    }

    @Test
    @DisplayName("切词：按空白/标点切分，丢弃单字词（ngram 忽略），剔除 BOOLEAN 运算符")
    void usableTerms() {
        assertAll(
                // 单字「铅」被丢（ngram_token_size=2 不产生 token），剩「限量」
                () -> assertEquals(List.of("限量"), GbRetriever.usableTerms("铅 限量")),
                // 标点切分；两词均 ≥2 字保留
                () -> assertEquals(List.of("水产品", "铅含量"), GbRetriever.usableTerms("水产品，铅含量")),
                // 运算符「+」被剔除后「铅」成单字 → 该词整段丢弃
                () -> assertEquals(List.of("水产品"), GbRetriever.usableTerms("水产品，+铅")),
                // 全单字 → 无可用词
                () -> assertTrue(GbRetriever.usableTerms("铅 汞 镉").isEmpty()),
                // 长中文句（无空格）作为整体词保留
                () -> assertEquals(List.of("水产品中铅的限量"), GbRetriever.usableTerms("水产品中铅的限量"))
        );
    }

    @Test
    @DisplayName("查询串：AND=每词加 +，OR=词间空格")
    void buildQueries() {
        assertAll(
                () -> assertEquals("+铅 +限量", GbRetriever.buildAnd(List.of("铅", "限量"))),
                () -> assertEquals("铅 限量", GbRetriever.buildOr(List.of("铅", "限量")))
        );
    }

    @Test
    @DisplayName("★检索：AND 有命中则直接返回，不再打 OR")
    void searchAndHit() {
        GbSearchHitVO hit = new GbSearchHitVO();
        hit.setStdNo("GB 2762-2022");
        when(mapper.searchByNgram("+水产品 +限量", null, 5)).thenReturn(List.of(hit));

        List<GbSearchHitVO> r = retriever.search("水产品 限量", 5, null);

        assertEquals(1, r.size());
        verify(mapper, never()).searchByNgram(eq("水产品 限量"), isNull(), anyInt());
    }

    @Test
    @DisplayName("★检索：AND 无命中 → 自动降级 OR 语义（召回兜底）")
    void searchFallsBackToOr() {
        when(mapper.searchByNgram("+水产品 +限量", null, 5)).thenReturn(List.of());
        GbSearchHitVO hit = new GbSearchHitVO();
        hit.setStdNo("GB 2762-2022");
        when(mapper.searchByNgram("水产品 限量", null, 5)).thenReturn(List.of(hit));

        List<GbSearchHitVO> r = retriever.search("水产品 限量", 5, null);

        assertEquals(1, r.size());
        verify(mapper, times(1)).searchByNgram("+水产品 +限量", null, 5);
        verify(mapper, times(1)).searchByNgram("水产品 限量", null, 5);
    }

    @Test
    @DisplayName("★检索：无可检索词（全单字）→ 返回空，不查库不编造")
    void searchNoUsableTerms() {
        List<GbSearchHitVO> r = retriever.search("铅", 5, null);
        assertTrue(r.isEmpty());
        verify(mapper, never()).searchByNgram(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), anyInt());
    }
}
