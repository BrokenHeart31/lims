package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 检验方法-检验员资质（`tester_method`，T-103 建表）—— T-501「方法资质规则」的数据源。
 *
 * <p>语义：一行 = 某检验员对某**方法标准号**具备资质。对应 AGENTS 7.4 第二条规则
 * 「其余项目按『检验方法—检验员资质』自动匹配可执行人，允许人工改派（仅列出有资质者）」。</p>
 *
 * <p>⚠️ 数据现状（2026-09-11 实测）：本表 **0 行**。故方法资质规则当前无数据可用，
 * 自动分配会落到「待人工指派」；这是数据缺口而非实现缺陷，由业务方补录后自然生效。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tester_method")
public class TesterMethod extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 检验方法名称（如 食品中兽药最大残留限量测定） */
    private String methodName;

    /** 方法标准号（如 GB 5009.268-2016），与 sample_item.methods 中的条目匹配 */
    private String methodNo;

    /** 检验员工号（sys_user.username） */
    private String testerNo;

    /** 资质状态：1=有效 0=失效（分配时只取 1） */
    private Integer qualStatus;

    /** 备注（资质取得日期 / 授权范围等） */
    private String remark;
}
