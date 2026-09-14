package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.SysOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统操作日志 Mapper（只追加，无自定义 SQL）。
 */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {
}
