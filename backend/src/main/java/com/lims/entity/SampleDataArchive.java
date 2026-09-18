package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 下游数据留档快照（`sample_data_archive`，feature B，设计 §3.1.3）。
 *
 * <p><b>取证用，只增不删</b>：回退失效前把整行 pre-image 写进本表（含失效前的 deleted 原值），
 * 保证「历史不消失」（PRD G5 / B-06）。另承接**保存前修订留档**（结果覆盖式 upsert 丢旧值 → Pit 2）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_data_archive")
public class SampleDataArchive extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 样品ID */
    private Long sampleId;

    /** 样品编号 */
    private String sampleNo;

    /** 来源表：sample_item / sample_result / sample_info */
    private String tableName;

    /** 来源表主键 id */
    private Long rowId;

    /** 触发留档的回退ID；NULL=保存前修订留档 */
    private Long rollbackId;

    /** 留档原因 1=回退失效 2=保存前修订留档 3=手动留档 */
    private Integer archiveReason;

    /** 整行快照（JSON 文本，含失效前的 deleted 原值） */
    private String snapshotJson;
}
