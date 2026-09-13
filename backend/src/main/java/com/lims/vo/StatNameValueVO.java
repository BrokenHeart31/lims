package com.lims.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用「名称-数值」统计项（api-spec 第 14 章，T-803）。
 *
 * <p>用于饼图/柱状图的统一结构：检验类别分布、食品大类分布、部门任务量等。
 * 一个结构覆盖多张图，避免为每个图各造一个 VO。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatNameValueVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分类名称（如「监督抽检」「蔬菜」「农残检验室」） */
    private String name;

    /** 计数 */
    private long value;

    /**
     * 附加比例（%，可选；饼图由前端算亦可，后端给则前后端一致）。
     * 无意义场景（柱状图）保持 {@code null}。
     */
    private Double percent;
}
