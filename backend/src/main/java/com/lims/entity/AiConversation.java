package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 助手会话（{@code ai_conversation}，feature A，T03）。
 *
 * <p>一次「新对话」建一行；{@code user_no} 记录归属用户工号，供会话审计（A4）。
 * 消息明细见 {@link AiMessage}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话标题（首问截断） */
    private String title;

    /** 所属用户工号 */
    private String userNo;

    /** 使用的模型名 */
    private String model;
}
