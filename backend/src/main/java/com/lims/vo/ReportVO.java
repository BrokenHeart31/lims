package com.lims.vo;

import lombok.Data;

import java.util.List;

/**
 * 检验报告合成结果（T-702）。
 *
 * <p>一次性聚合 {@code sample_info + sample_item + sample_result + sample_audit_log + sys_user}
 * 后产出的**完整渲染模型**：字段与业务说明书封面 / 第 1 页信息表 / 第 2 页结果表逐格对齐，
 * 前端打印页无需再做任何映射或二次查询。</p>
 *
 * <p><b>不落快照</b>：本 VO 每次实时聚合。依据——S80 签发后样品无写路径（S90 为终态），
 * 数据天然冻结，实时聚合不会漂移；落快照反而引入「快照与事实两处真相」的一致性风险
 * （见 db/migrations/V6 注释）。</p>
 */
@Data
public class ReportVO {

    // ---- 报告标识 ----
    /** 报告类型 code：1=CMA 2=CMA-CATL */
    private Integer reportType;

    /** 报告类型中文名 */
    private String reportTypeLabel;

    /** 报告编号（= 样品编号） */
    private String reportNo;

    /** 封面资质行：CMA 1 行、CMA-CATL 2 行 */
    private List<String> qualificationLines;

    // ---- 机构信息（来自 ReportProperties） ----
    private String orgName;
    private String address;
    private String phone;
    private String postcode;
    private String fax;

    /** 封面「注意事项」（7 条） */
    private List<String> notes;

    // ---- 封面三要素 ----
    private String productName;
    private String clientName;
    private String inspectType;

    // ---- 第 1 页信息表（顺序即渲染顺序） ----
    private String spec;
    private String brand;
    private String manufacturer;
    private String grade;
    private String samplingAddress;
    /** 采样日期，格式 yyyy.MM.dd */
    private String samplingDate;
    private String sampleQuantity;
    private String sampler;
    private String samplingBase;
    private String originalNo;
    private String sampleState;
    /** 检测项目摘要（固定「见第2页」） */
    private String itemSummary;
    /** 检测依据（该样品全部 basis_code 去重、顿号连接） */
    private String basisText;
    /** 检验日期，格式 yyyy.MM.dd */
    private String inspectDate;
    /** 检验结论整句 */
    private String conclusionText;
    /** 主要仪器（本期无数据源，恒为 null） */
    private String instrument;
    /** 实验环境条件（固定文案） */
    private String environment;
    private String remark;

    // ---- 签署 ----
    /** 签发日期，格式 yyyy.MM.dd */
    private String signAt;
    /** 批准人姓名（= sign_by 的姓名） */
    private String approveName;
    /** 审核人姓名（= audit_by 的姓名） */
    private String auditName;
    /** 编制人姓名（= 该样品 sample_result 最早 entered_by） */
    private String editName;
    /** 批准人电子签名地址（未配置时为 null → 前端渲染虚线占位框） */
    private String approveSignatureUrl;
    /** 审核人电子签名地址（未配置时为 null） */
    private String auditSignatureUrl;
    /** 编制人电子签名地址（未配置时为 null） */
    private String editSignatureUrl;

    // ---- 第 2 页明细 ----
    private List<ReportItemVO> items;
}
