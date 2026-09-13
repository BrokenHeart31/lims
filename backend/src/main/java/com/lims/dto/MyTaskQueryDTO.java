package com.lims.dto;

import lombok.Data;

/**
 * 检验员任务查询参数（api-spec 第 11 章，T-603）。
 *
 * <p><b>数据范围不由前端决定</b>：{@code testerNo} 由 Service 依据当前登录人身份注入
 * （R100 综合管理 → 不过滤，可查全部；其余角色 → 强制本人），DTO 里只放业务筛选条件。
 * 这样即使前端伪造参数也无法越权看他人任务。</p>
 */
@Data
public class MyTaskQueryDTO {

    /** 样品编号（前缀匹配） */
    private String sampleNo;

    /** 样品名称（模糊匹配） */
    private String sampleName;

    /** 受检单位（模糊匹配） */
    private String clientName;

    /** 任务编号（精确匹配） */
    private String taskNo;

    /** 是否只看未录入项（true=只看尚未有效录入的项，便于检验员清尾） */
    private Boolean onlyUnentered;

    /**
     * 内部注入的检验员范围（非前端参数）：
     * {@code null} = 不按检验员过滤（R100 全部）；非空 = 仅该工号。
     */
    private String testerScope;
}
