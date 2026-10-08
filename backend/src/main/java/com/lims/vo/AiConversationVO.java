package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    /** 创建时间（F28：LocalDateTime 必须显式 @JsonFormat，否则输出 ISO-8601 与其余 VO 不一致） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
