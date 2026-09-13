package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.SysUserSaveDTO;
import com.lims.vo.SysUserVO;

/**
 * 用户管理服务（api-spec 第 13 章，T-107）。
 *
 * <p>定位：系统管理 4 页之一。用户是 RBAC 的起点（用户→角色→权限→菜单），
 * 也是 T-501 方法资质自动分配、T-702 报告签名的数据源——因此 username（工号）
 * 的完整性与唯一性是本域的核心不变量。</p>
 */
public interface SysUserService {

    PageResult<SysUserVO> pageQuery(long current, long size, String username, String nickname,
                                    Long deptId, Integer status);

    /** 详情（含已绑定角色 id 集合，供编辑弹窗回显） */
    SysUserVO detail(Long id);

    Long create(SysUserSaveDTO dto);

    /**
     * 更新（不含密码）。
     *
     * <p>username 不可修改：T-401/T-501 的 `tester_no`、报告上的检验员工号都是历史快照，
     * 允许改工号会让历史记录与当前账号脱钩。需要变更工号请新建账号并停用旧号。</p>
     */
    void update(SysUserSaveDTO dto);

    /** 重置密码（独立入口，请求体单向进入、永不回吐） */
    void resetPassword(Long id, String rawPassword);

    /**
     * 逻辑删除用户。
     *
     * <p>fail-loud 保护：不允许删除当前登录账号，也不允许删除最后一个 R100 综合管理员
     * （否则系统再无人能维护权限，属于不可逆的自锁）。</p>
     */
    void remove(Long id);
}
