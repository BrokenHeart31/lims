package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.SampleDataArchive;

/**
 * 下游数据留档快照 Mapper（feature B，T02）。
 *
 * <p><b>只追加</b>：留档是取证数据，不提供 UPDATE / DELETE 入口（PRD G5 / B-06）。</p>
 */
public interface SampleDataArchiveMapper extends BaseMapper<SampleDataArchive> {
}
