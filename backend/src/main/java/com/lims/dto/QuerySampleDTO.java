package com.lims.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 样品查询条件（T-801 查询域共用，在检样品 / 历史样品两个接口复用）。
 *
 * <p><b>为什么合用一个 DTO</b>：在检样品与历史样品的筛选维度高度重叠（编号/名称/受检单位/任务/状态/抽样日期），
 * 差异仅在于历史样品多出「整体结论」与「是否已生成报告」两个维度。合用一个 DTO 可避免两处筛选条件漂移，
 * 各 SQL 只取自己需要的那部分条件（MyBatis 动态 SQL 按需拼装）。</p>
 *
 * <p>字段均为可选（未传即不过滤）；分页参数 {@code current}/{@code size} 由 Controller 单独接收，不混入本对象。</p>
 */
@Data
public class QuerySampleDTO {

    /** 样品编号（前缀匹配） */
    private String sampleNo;

    /** 样品名称（模糊匹配） */
    private String sampleName;

    /** 受检单位（模糊匹配） */
    private String clientName;

    /** 任务编号（精确匹配） */
    private String taskNo;

    /** 样品状态 code（精确匹配，见 common/enums/SampleStatus） */
    private Integer status;

    /** 抽样日期起（含），yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate samplingDateFrom;

    /** 抽样日期止（含），yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate samplingDateTo;

    /** 整体结论 code（1=合格 2=不合格 3=待判定，仅历史样品查询使用） */
    private Integer conclusion;

    /** 是否已生成报告（true=仅 report_generated_at 非空，false=仅为空；仅历史样品查询使用） */
    private Boolean reportGenerated;
}
