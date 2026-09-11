package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.UserMethod;
import org.apache.ibatis.annotations.Mapper;

/**
 * 检验员分类资质 Mapper（旧表 user_method，// [LEGACY] 只读）。
 */
@Mapper
public interface UserMethodMapper extends BaseMapper<UserMethod> {
}
