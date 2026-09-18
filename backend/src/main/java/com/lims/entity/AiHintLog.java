package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * AI 助手主动提示留痕（{@code ai_hint_log}，feature 增量 ai_flow_assistant，T02，设计 §3.1）。
 *
 * <p><b>追加型事件</b>：记录「何时 / 对谁 / 什么上下文 / 主动提示了什么 / 是否被点开」，
 * 供护栏与触发质量复盘（与 {@code ai_message} 互补：前者记主动提示，后者记问答）。</p>
 *
 * <p>这是 AI 域**唯一的业务写入**（属留痕，非业务数据）；点击回填 {@code clicked/clickedAt}
 * 属状态推进，非「改写历史」。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_hint_log")
public class AiHintLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 触发用户工号 */
    private String userNo;

    /** 关联会话（若已开对话） */
    private Long conversationId;

    /** 上下文样品编号 */
    private String sampleNo;

    /** 来源页面标识（如 result-entry） */
    private String pageKey;

    /** 检测项目名 */
    private String itemName;

    /** 命中的标准号 */
    private String stdNo;

    /** 去重键 sampleNo|itemName|stdNo */
    private String hintKey;

    /** 建议条文案（一行） */
    private String hintText;

    /** 当次上下文快照（JSON 文本） */
    private String contextJson;

    /** 是否被点开 0=否 1=是 */
    private Integer clicked;

    /** 点开时间 */
    private LocalDateTime clickedAt;
}
