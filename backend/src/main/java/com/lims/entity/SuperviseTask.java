package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 监抽任务（supervise_task，T-201）。
 * 字典字段（taskNature/regionLevel/samplingStage/status）存中文字典值，
 * 见 db/init/03_task_tables.sql 与 DECISIONS 2026-09-10。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("supervise_task")
public class SuperviseTask extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务编号（唯一） */
    private String taskNo;

    private String taskName;

    /** 监督抽检/委托抽样/委托送样 */
    private String taskNature;

    /** 任务来源（下达单位） */
    private String taskSource;

    /** 省级/市级/区级 */
    private String regionLevel;

    private String leader;

    private String batchNo;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate receiveDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate issueDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate completeDate;

    private String priority;

    private String positiveRateRequirement;

    /** 生产/流通/餐饮 */
    private String samplingStage;

    private String testScope;

    /** 草稿/进行中/已完成/已中止 */
    private String status;

    private String remark;
}
