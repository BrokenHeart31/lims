package com.lims.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 月度趋势行（api-spec 第 14 章，T-803）。
 *
 * <p>折线图/柱状图需要「即使该月零数据也占一个刻度」——否则 1 月、3 月有数据而 2 月没有时，
 * 折线会直接连过去，视觉上抹掉「2 月没做检测」这个事实。故 Service 会补零月。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatTrendVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 月份标签，格式 yyyy-MM */
    private String month;

    /** 该月样品数 */
    private long sampleCount;

    /** 该月已完成样品数（status >= 80） */
    private long completedCount;
}
