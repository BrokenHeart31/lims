package com.lims.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 消息明细（会话审计，feature A，T03 / api-spec A5）。
 *
 * <p>含引用与拒答留痕；{@code citations} 由 {@code ai_message.citations_json} 反序列化而来，
 * 证明「某次回答引用了哪一版条款」。</p>
 */
@Data
public class AiMessageVO {

    private Long id;

    private Long conversationId;

    private Integer seq;

    /** 角色 1=用户 2=助手 */
    private Integer role;

    /** 角色中文名 */
    private String roleLabel;

    private String content;

    /** 领域 business / standard / other */
    private String domain;

    /** 是否越界拒答 0/1 */
    private Integer refused;

    /** 引用清单（回放当时命中） */
    private List<AiCitationVO> citations = new ArrayList<>();

    private Integer retrievedCount;

    private String model;

    private Integer elapsedMs;

    private LocalDateTime createdAt;
}
