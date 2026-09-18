package com.lims.controller;

import com.lims.common.R;
import com.lims.dto.AiChatContextDTO;
import com.lims.dto.AiCompanionFeedbackDTO;
import com.lims.service.ai.BusinessContextReader;
import com.lims.service.ai.companion.StandardCompanionService;
import com.lims.vo.AiCompanionHintVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标准伴随查询接口（feature 增量 ai_flow_assistant，T02，设计 §4.1 / A14 / A15）。
 *
 * <p>权限 {@code ai:chat}（seed 已定义，本增量零新增权限点）。<b>全部只读</b>：
 * {@code /ai/companion} 不写任何业务表；{@code /ai/companion/feedback} 只写 {@code ai_hint_log} 留痕。</p>
 */
@Validated
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiCompanionController {

    private final StandardCompanionService companionService;
    private final BusinessContextReader contextReader;

    /**
     * A14 标准伴随查询（上下文触发；{@code itemName} 为空 → 返回 {@code []}）。
     *
     * <p>无触发 / 标准未入库 → HTTP 200 + {@code code=0} + {@code data=[]}（fail-soft，不是错误）。</p>
     */
    @PostMapping("/companion")
    @PreAuthorize("hasAuthority('ai:chat')")
    public R<List<AiCompanionHintVO>> companion(@RequestBody(required = false) AiChatContextDTO ctx) {
        AiChatContextDTO context = ctx == null ? new AiChatContextDTO() : ctx;
        return R.ok(companionService.hints(context, contextReader.readRolePermissions()));
    }

    /** A15 建议条留痕（是否被点开）。 */
    @PostMapping("/companion/feedback")
    @PreAuthorize("hasAuthority('ai:chat')")
    public R<Boolean> feedback(@Valid @RequestBody AiCompanionFeedbackDTO dto) {
        return R.ok(companionService.feedback(dto));
    }
}
