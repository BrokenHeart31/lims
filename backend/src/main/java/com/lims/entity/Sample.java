package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.lims.common.enums.ResultConclusion;
import com.lims.common.enums.SampleStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 样品登记（sample_info，T-301）。
 *
 * <p>字段与采样单 Excel 列头一一对应（db/init/05_sample_tables.sql）。</p>
 *
 * <p>表名用 {@code sample_info} 而非 {@code sample}：`SAMPLE` 是 SQL 关键字，
 * 与 MyBatis-Plus 分页插件的 JSqlParser 冲突，会令 count SQL 优化失败
 * （DECISIONS 2026-09-11；同 sys_user 避开 user 关键字的处理原则）。</p>
 *
 * <p>{@code status} 为 {@link SampleStatus} 枚举，落库 TINYINT code、JSON 输出数字 code
 * （AGENTS 4.3「业务状态字段用枚举，禁止魔法数字」）；中文名另经 {@link #getStatusLabel()} 出网。
 * 状态变更必须经 SampleStatusTransition 白名单校验，DAO 层禁止直接 set。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_info")
public class Sample extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品编号（唯一，如 JK(2023)-SA-001） */
    private String sampleNo;

    private String sampleName;

    /** 受检单位 */
    private String clientName;

    /** 抽样地址 */
    private String samplingAddress;

    private String payee;

    private BigDecimal fee;

    /** 样品数量（文本，如 3kg） */
    private String sampleQuantity;

    /** 项目名称（如 市级例行） */
    private String projectName;

    /** 采样日期（Excel「日期」列） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate samplingDate;

    private String remark;

    private String sampler;

    /** 生产单位 */
    private String manufacturer;

    /** 抽样基数 */
    private String samplingBase;

    /** 样品状态（如 鲜活） */
    private String sampleState;

    /** 规格型号 */
    private String spec;

    private String brand;

    /** 样品等级 */
    private String grade;

    /** 原编号或生产日期 */
    private String originalNo;

    /** 检验类别（如 监督抽检） */
    private String inspectType;

    /** 要求完成日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate requireCompleteDate;

    /** 关联监抽任务编号（supervise_task.task_no） */
    private String taskNo;

    /** 任务批号 */
    private String taskBatchNo;

    /** 样品状态机（落库 TINYINT code；导入落库即 S10） */
    private SampleStatus status;

    /**
     * 整体结论（T-601）：1=合格 2=不合格 3=待判定。
     *
     * <p>AGENTS 7.3 规则 6 的派生值——由「该样品全部**非参考项**单项结论」聚合而来
     * （参考项不计入整体，白名单 D3 裁决）。在结果保存/提交/查询时重算回写，
     * 供报告生成（T-702）与查询（T-801）直接取用。未进入录入阶段时为 {@code null}。</p>
     */
    private ResultConclusion conclusion;

    /** 登记确认人（S10→S20 时写入） */
    private String confirmedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmedAt;

    // ---------------------------------------------------------------------
    // 审核 / 签发（T-701：S60→S70→S80）
    // 完整流水见 sample_audit_log；本处保存**当前有效值**，供 T-702 报告合成直接取用
    // （业务说明书报告页脚：「报告无制表、审核、批准人签字无效」）。
    // ---------------------------------------------------------------------

    /** 审核人工号（S60→S70 写入；审核退回时清空） */
    private String auditBy;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime auditAt;

    /** 审核意见（可空；审核退回时原因记入 sample_audit_log.opinion） */
    private String auditOpinion;

    /** 签发人工号（S70→S80 写入） */
    private String signBy;

    /** 签发时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime signAt;

    /** 状态中文名（非持久化，出网供前端展示，避免前端维护 code→label 字典） */
    public String getStatusLabel() {
        return status == null ? null : status.getLabel();
    }

    /** 整体结论中文名（非持久化，出网供前端展示） */
    public String getConclusionLabel() {
        return conclusion == null ? null : conclusion.getLabel();
    }
}
