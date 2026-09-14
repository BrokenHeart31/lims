package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.common.PageResult;
import com.lims.dto.OperationLogQueryDTO;
import com.lims.entity.SysOperationLog;
import com.lims.mapper.SysOperationLogMapper;
import com.lims.security.LoginUser;
import com.lims.security.SecurityUtils;
import com.lims.service.SysOperationLogService;
import com.lims.vo.SysOperationLogVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 操作日志查询实现（api-spec 第 15 章）。
 *
 * <p><b>数据范围规则（服务端强制）</b>：</p>
 * <ul>
 *   <li>拥有 `log:view` 权限（R100 综合管理，或角色被显式授权）：可查全部日志，可用 operator 过滤；</li>
 *   <li>其余登录用户：强制附加 `operator = 本人工号`，即使请求里传了别人的工号也无效。</li>
 * </ul>
 *
 * <p>为什么不做成「无权限就直接 403」：日志入口位于用户菜单的「我的操作日志」，
 * 每个用户都应该能查自己做过什么（这是个人可追溯性的基本诉求，也是 ALCOA+ 的要求）；
 * 「跨用户查看」才是需要 `log:view` 的特权。把范围收在服务端，客户端无法绕过。</p>
 */
@Service
@RequiredArgsConstructor
public class SysOperationLogServiceImpl implements SysOperationLogService {

    /** 跨用户查看操作日志所需权限（seed `sys_menu` id=1151） */
    private static final String PERM_VIEW_ALL = "log:view";

    private final SysOperationLogMapper operationLogMapper;

    @Override
    public PageResult<SysOperationLogVO> page(OperationLogQueryDTO query) {
        String selfNo = SecurityUtils.getUsername().orElse(null);
        boolean canViewAll = hasPermission(PERM_VIEW_ALL);

        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<>();
        // 数据范围：无 log:view 者只能看自己 —— 参数无法覆盖，范围在服务端收口
        if (!canViewAll) {
            wrapper.eq(SysOperationLog::getOperator, selfNo);
        } else if (StringUtils.hasText(query.getOperator())) {
            wrapper.eq(SysOperationLog::getOperator, query.getOperator().trim());
        }
        if (StringUtils.hasText(query.getModule())) {
            wrapper.eq(SysOperationLog::getModule, query.getModule().trim());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(SysOperationLog::getCreatedAt, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(SysOperationLog::getCreatedAt, query.getEndTime());
        }
        // 最新的排最前：审计日志的使用场景是「刚才那步操作到底成功没有」
        wrapper.orderByDesc(SysOperationLog::getId);

        Page<SysOperationLog> page = new Page<>(query.getCurrent(), query.getSize());
        operationLogMapper.selectPage(page, wrapper);
        return PageResult.of(page, SysOperationLogVO::from);
    }

    /** 当前登录人是否拥有指定权限标识 */
    private boolean hasPermission(String permission) {
        Optional<LoginUser> loginUser = SecurityUtils.getLoginUser();
        if (loginUser.isEmpty()) {
            return false;
        }
        return loginUser.get().getAuthorities().stream()
                .anyMatch(authority -> permission.equals(authority.getAuthority()));
    }
}
