package com.lims.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 标准伴随建议条（feature 增量 ai_flow_assistant，T02，设计 §2.5 / §4.2）。
 *
 * <p>由 {@code StandardCompanionServiceImpl} 在「页面显式带入检测项目名」时装配：一条一行文案 +
 * 1~2 条条款预览（带来源类型）+ 一张数值对齐卡片。前端渲染成悬浮窗内**默认折叠的一行建议条**。</p>
 *
 * <p><b>不代操作</b>：本 VO 只承载「查看 / 跳转」语义，无任何写入字段。</p>
 */
@Data
public class AiCompanionHintVO {

    /** 去重键（= sampleNo|itemName|stdNo），供前端会话级去重与静默集使用 */
    private String hintKey;

    /** 上下文样品编号 */
    private String sampleNo;

    /** 检测项目名（触发键之一） */
    private String itemName;

    /** 命中的标准号 */
    private String stdNo;

    /** 标准名称（gb_document.std_title） */
    private String stdTitle;

    /** 建议条一行文案（如「本条涉及 GB 2763-2021，查看对应限量出处」） */
    private String oneLine;

    /** 条款预览（top 1~2 条，含来源类型 ocrDerived） */
    private List<AiCitationVO> citationPreview = new ArrayList<>();

    /** 数值对齐卡片（系统标准库权威值；可能为 null） */
    private AiValueAnchorVO valueAnchor;
}
