package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.SampleStatusLog;

/**
 * 样品状态流水 Mapper（feature B，T02）。
 *
 * <p><b>只追加</b>：业务只调用 insert 与按样品查询；不提供 UPDATE / DELETE 入口。</p>
 */
public interface SampleStatusLogMapper extends BaseMapper<SampleStatusLog> {
}
