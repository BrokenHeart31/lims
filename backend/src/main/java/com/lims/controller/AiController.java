package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.dto.AiChatDTO;
import com.lims.service.ai.AiAssistantService;
import com.lims.service.ai.BusinessContextReader;
import com.lims.service.ai.flow.FlowGuideAssembler;
import com.lims.vo.AiAnswerVO;
import com.lims.vo.AiConversationVO;
import com.lims.vo.AiMessageVO;
import com.lims.vo.AiStatusVO;
import com.lims.vo.FlowGuideVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 助手接口（api-spec 第 16 章 /api/ai，feature A，T03）。
 *
 * <p>权限标识与 seed {@code sys_menu}(id=121/122/123/124) 一致：
 * <ul>
 *   <li>{@code ai:chat}（124）：对话与流式对话；</li>
 *   <li>{@code ai:log:view}（123）：会话审计查询；</li>
 *   <li>{@code /ai/status}：**登录即可**（不加 @PreAuthorize，仅需认证）——前端角标要能在无额外权限时探测服务状态。</li>
 * </ul>
 */
@Validated
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiAssistantService aiAssistantService;
    private final FlowGuideAssembler flowGuideAssembler;
    private final BusinessContextReader contextReader;

    /** A1 AI 服务健康检查（fail-soft：离线也 HTTP 200 + code=0，online=false）。 */
    @GetMapping("/status")
    public R<AiStatusVO> status() {
        return R.ok(aiAssistantService.status());
    }

    /** A2 非流式问答（自检/降级/测试用）。 */
    @PostMapping("/chat")
    @PreAuthorize("hasAuthority('ai:chat')")
    public R<AiAnswerVO> chat(@Valid @RequestBody AiChatDTO dto) {
        return R.ok(aiAssistantService.chat(dto));
    }

    /** A3 流式问答（SSE 主通道）。 */
    @PostMapping("/chat/stream")
    @PreAuthorize("hasAuthority('ai:chat')")
    public SseEmitter chatStream(@Valid @RequestBody AiChatDTO dto) {
        return aiAssistantService.chatStream(dto);
    }

    /** A4 分页查询会话（审计）。 */
    @GetMapping("/conversations")
    @PreAuthorize("hasAuthority('ai:log:view')")
    public R<PageResult<AiConversationVO>> conversations(
            @RequestParam(defaultValue = "1") @Min(1) long current,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) long size) {
        return R.ok(aiAssistantService.pageConversations(current, size));
    }

    /** A5 会话消息明细（含引用/拒答留痕）。 */
    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("hasAuthority('ai:log:view')")
    public R<List<AiMessageVO>> messages(@PathVariable Long id) {
        return R.ok(aiAssistantService.messages(id));
    }

    /**
     * A16 业务全流程引导（feature 增量 ai_flow_assistant，设计 §2.6 / §4.4）。
     *
     * <p><b>确定性事实层</b>：由 {@link FlowGuideAssembler} × {@code BusinessFlowMap} 装配，
     * <b>业务域完全不调模型</b>；同一「当前状态 + 角色」重复询问，结构化事实字段 100% 一致（G3）。</p>
     *
     * <p>权限收敛（TE）：不具备的步骤 {@code hasPermission=false} 且不提供跳转，前端灰显 +
     * 提示「需 X 权限，联系 R100/R2 开通」，<b>不诱导越权</b>。参数 {@code sampleNo}/{@code status}
     * 皆可空（空则返回总览，不标当前步）。</p>
     */
    @GetMapping("/flow/guide")
    @PreAuthorize("hasAuthority('ai:chat')")
    public R<FlowGuideVO> flowGuide(
            @RequestParam(required = false) String sampleNo,
            @RequestParam(required = false) Integer status) {
        return R.ok(flowGuideAssembler.guide(status, sampleNo, contextReader.readRolePermissions()));
    }
}
