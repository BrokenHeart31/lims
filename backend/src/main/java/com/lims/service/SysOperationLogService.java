package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.OperationLogQueryDTO;
import com.lims.vo.SysOperationLogVO;

/**
 * 系统操作日志服务（api-spec 第 15 章）。
 *
 * <p>只提供查询——日志由 {@code config/OperationLogInterceptor} 在 MVC 层追加，
 * 业务代码不应（也不允许）手工写审计流水。</p>
 */
public interface SysOperationLogService {

    /**
     * 分页查询操作日志。
     *
     * <p>数据范围：拥有 `log:view` 权限者可查全部（可按操作人筛选）；
     * 无该权限者只能看到自己的记录，且该范围由服务层强制施加，客户端参数无法覆盖。</p>
     */
    PageResult<SysOperationLogVO> page(OperationLogQueryDTO query);
}
