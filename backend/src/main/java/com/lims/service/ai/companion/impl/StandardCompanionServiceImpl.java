package com.lims.service.ai.companion.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lims.dto.AiChatContextDTO;
import com.lims.dto.AiCompanionFeedbackDTO;
import com.lims.entity.AiHintLog;
import com.lims.entity.GbDocument;
import com.lims.entity.ProductLibItem;
import com.lims.mapper.AiHintLogMapper;
import com.lims.mapper.GbDocumentMapper;
import com.lims.mapper.ProductLibItemMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.ai.GbRetriever;
import com.lims.service.ai.companion.CompanionTriggerPolicy;
import com.lims.service.ai.companion.StandardCompanionService;
import com.lims.service.ai.companion.ValueAnchorAssembler;
import com.lims.vo.AiCitationVO;
import com.lims.vo.AiCompanionHintVO;
import com.lims.vo.AiValueAnchorVO;
import com.lims.vo.GbSearchHitVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 标准伴随查询实现（feature 增量 ai_flow_assistant，T02，设计 §2.5 / §6.1）。
 *
 * <p>编排：触发门禁（{@code itemName} 空 → 返回 {@code []}）→ 解析标准号（优先页面带入
 * {@code basisCode}，否则按项目名查系统标准库）→ 校验标准已入库（{@code gb_document status=1}）
 * → 取 1~2 条条款预览 → 装配数值对齐卡片 → 写 {@code ai_hint_log} 留痕。</p>
 *
 * <p><b>只读业务</b>：仅写 {@code ai_hint_log}；不碰任何业务表、不写结论字段、不调 {@code JudgeEngine}。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StandardCompanionServiceImpl implements StandardCompanionService {

    /** 建议条预览条数（top 1~2）。 */
    private static final int PREVIEW_TOP_N = 2;
    /** 数值对齐跳转所需权限。 */
    private static final String LIB_VIEW_PERM = "base:lib:list";
    /** 引用片段截断长度。 */
    private static final int SNIPPET_MAX = 220;

    private final CompanionTriggerPolicy triggerPolicy;
    private final GbRetriever gbRetriever;
    private final ValueAnchorAssembler valueAnchorAssembler;
    private final GbDocumentMapper gbDocumentMapper;
    private final ProductLibItemMapper productLibItemMapper;
    private final AiHintLogMapper aiHintLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<AiCompanionHintVO> hints(AiChatContextDTO ctx, List<String> perms) {
        // ① 触发门禁：无项目名 → 空（误报率 = 0 由构造保证）
        if (!triggerPolicy.shouldTrigger(ctx)) {
            return List.of();
        }
        // ② 解析标准号：优先页面带入 basisCode，否则按项目名查系统标准库
        String stdNo = triggerPolicy.resolveStdNo(ctx).orElseGet(() -> resolveStdNoFromLib(ctx.getItemName()));
        if (!StringUtils.hasText(stdNo)) {
            return List.of();
        }
        // ③ 该标准必须已建索引（gb_document status=1），否则不提示
        GbDocument doc = findIndexedDoc(stdNo);
        if (doc == null) {
            return List.of();
        }
        // ④ 条款预览（只查倒排索引，绝不读文件）
        List<AiCitationVO> preview = new ArrayList<>();
        try {
            for (GbSearchHitVO hit : gbRetriever.search(ctx.getItemName(), PREVIEW_TOP_N, stdNo)) {
                preview.add(toCitation(hit));
            }
        } catch (RuntimeException e) {
            // 检索异常不应阻断数值对齐（数值路径与检索路径物理隔离）
            log.warn("[ai-companion] 条款预览检索失败（忽略）：{}", e.getMessage());
        }
        // ⑤ 数值对齐卡片（系统标准库权威值；数值路径不碰 OCR）
        boolean canJump = perms != null && perms.contains(LIB_VIEW_PERM);
        AiValueAnchorVO anchor = valueAnchorAssembler
                .anchor(ctx.getItemName(), stdNo, ctx.getSampleNo(), canJump)
                .orElse(null);
        // ⑥ 装配建议条
        AiCompanionHintVO hint = new AiCompanionHintVO();
        hint.setHintKey(triggerPolicy.hintKey(ctx, stdNo));
        hint.setSampleNo(ctx.getSampleNo());
        hint.setItemName(ctx.getItemName());
        hint.setStdNo(stdNo);
        hint.setStdTitle(doc.getStdTitle());
        hint.setOneLine(triggerPolicy.oneLine(stdNo));
        hint.setCitationPreview(preview);
        hint.setValueAnchor(anchor);
        // ⑦ 留痕（唯一写入，追加型）
        saveHint(ctx, stdNo, hint);
        return List.of(hint);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean feedback(AiCompanionFeedbackDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getHintKey())) {
            return false;
        }
        String username = username();
        AiHintLog existing = aiHintLogMapper.selectOne(new LambdaQueryWrapper<AiHintLog>()
                .eq(AiHintLog::getHintKey, dto.getHintKey())
                .orderByDesc(AiHintLog::getId)
                .last("LIMIT 1"));
        Boolean clicked = dto.getClicked();
        if (existing == null) {
            // 罕见：前端未先收到 hints 即回传 → 补一条留痕，保证事件不丢
            AiHintLog logRow = new AiHintLog();
            logRow.setUserNo(username);
            logRow.setConversationId(null);
            logRow.setSampleNo(dto.getSampleNo());
            logRow.setPageKey(dto.getPageKey());
            logRow.setItemName(dto.getItemName());
            logRow.setStdNo(dto.getStdNo());
            logRow.setHintKey(dto.getHintKey());
            logRow.setHintText(null);
            logRow.setClicked(Boolean.TRUE.equals(clicked) ? 1 : 0);
            logRow.setClickedAt(Boolean.TRUE.equals(clicked) ? LocalDateTime.now() : null);
            logRow.setCreatedBy(username);
            logRow.setUpdatedBy(username);
            aiHintLogMapper.insert(logRow);
            return true;
        }
        existing.setClicked(Boolean.TRUE.equals(clicked) ? 1 : 0);
        existing.setClickedAt(Boolean.TRUE.equals(clicked) ? LocalDateTime.now() : null);
        if (StringUtils.hasText(dto.getPageKey())) {
            existing.setPageKey(dto.getPageKey());
        }
        existing.setUpdatedBy(username);
        aiHintLogMapper.updateById(existing);
        return true;
    }

    // =====================================================================
    // 内部
    // =====================================================================

    /** 依项目名从系统标准库解析标准号（只读）。 */
    private String resolveStdNoFromLib(String itemName) {
        if (!StringUtils.hasText(itemName)) {
            return null;
        }
        ProductLibItem item = productLibItemMapper.selectOne(new LambdaQueryWrapper<ProductLibItem>()
                .eq(ProductLibItem::getItemName, itemName)
                .isNotNull(ProductLibItem::getBasisCode)
                .ne(ProductLibItem::getBasisCode, "")
                .orderByAsc(ProductLibItem::getId)
                .last("LIMIT 1"));
        if (item == null) {
            return null;
        }
        String normalized = triggerPolicy.normalizeStdNo(item.getBasisCode());
        return StringUtils.hasText(normalized) ? normalized : item.getBasisCode();
    }

    /** 查已建索引的标准文档（{@code status=1}）。 */
    private GbDocument findIndexedDoc(String stdNo) {
        return gbDocumentMapper.selectOne(new LambdaQueryWrapper<GbDocument>()
                .eq(GbDocument::getStatus, GbDocument.STATUS_DONE)
                .like(GbDocument::getStdNo, stdNo)
                .orderByAsc(GbDocument::getId)
                .last("LIMIT 1"));
    }

    /** 写留痕（AI 域唯一写）。 */
    private void saveHint(AiChatContextDTO ctx, String stdNo, AiCompanionHintVO hint) {
        String username = username();
        AiHintLog row = new AiHintLog();
        row.setUserNo(username);
        row.setSampleNo(ctx.getSampleNo());
        row.setPageKey(ctx.getPageKey());
        row.setItemName(ctx.getItemName());
        row.setStdNo(stdNo);
        row.setHintKey(hint.getHintKey());
        row.setHintText(hint.getOneLine());
        row.setContextJson(toJson(ctx));
        row.setClicked(0);
        row.setCreatedBy(username);
        row.setUpdatedBy(username);
        aiHintLogMapper.insert(row);
    }

    private AiCitationVO toCitation(GbSearchHitVO h) {
        AiCitationVO c = new AiCitationVO();
        c.setStdNo(h.getStdNo());
        c.setClauseNo(h.getClauseNo());
        c.setClauseTitle(h.getClauseTitle());
        c.setSnippet(snippet(h.getSnippet()));
        c.setDocId(h.getDocId());
        c.setSourceFile(h.getSourceFile());
        c.setScore(h.getScore());
        c.setSourceType(h.getSourceType());
        c.setOcrDerived(h.isOcrDerived());
        return c;
    }

    private String snippet(String text) {
        if (text == null) {
            return null;
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() <= SNIPPET_MAX ? t : t.substring(0, SNIPPET_MAX) + "…";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[ai-companion] context 序列化失败：{}", e.getMessage());
            return null;
        }
    }

    private String username() {
        return SecurityUtils.getUsername().orElse("anonymous");
    }
}
