package com.lims.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话（审计列表项，feature A，T03 / api-spec A4）。
 */
@Data
public class AiConversationVO {

    private Long id;

    private String title;

    private String userNo;

    private String model;

    /** 消息条数（列表内展示，便于判断是否空会话） */
    private Integer messageCount;

    private LocalDateTime createdAt;
}
