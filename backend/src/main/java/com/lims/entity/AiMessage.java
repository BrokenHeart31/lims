package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 助手消息（{@code ai_message}，feature A，T03）—— 含引用与拒答留痕，供事后审计。
 *
 * <p>{@code citations_json} 快照**当时**命中的标准号/条款/片段/出处/得分，
 * 可证明「某次回答引用了哪一版标准条款」（审计要求，设计 §2.10）。</p>
 *
 * <p>{@code domain} 只写 business/standard/other（{@code AiDomain.UNKNOWN} 永不落库）；
 * {@code refused=1} 表示越界拒答——**拒答是正常业务结果**（{@code code=0}），不是错误。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class AiMessage extends BaseEntity {

    /** 角色：用户 */
    public static final int ROLE_USER = 1;
    /** 角色：助手 */
    public static final int ROLE_ASSISTANT = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ai_conversation.id */
    private Long conversationId;

    /** 会话内序号（从 1 起） */
    private Integer seq;

    /** 角色 1=用户 2=助手 */
    private Integer role;

    /** 消息正文 */
    private String content;

    /** 领域判定 business / standard / other */
    private String domain;

    /** 是否越界拒答 0=否 1=是 */
    private Integer refused;

    /** 引用清单快照（JSON 文本） */
    private String citationsJson;

    /** 检索命中数 */
    private Integer retrievedCount;

    /** 模型名 */
    private String model;

    /** 本次耗时（毫秒） */
    private Integer elapsedMs;

    /** 角色中文名（非持久化，出网供前端展示） */
    public String getRoleLabel() {
        if (role == null) {
            return null;
        }
        return switch (role) {
            case ROLE_USER -> "用户";
            case ROLE_ASSISTANT -> "助手";
            default -> "未知";
        };
    }
}
