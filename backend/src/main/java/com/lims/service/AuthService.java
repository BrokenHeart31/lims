package com.lims.service;

import com.lims.dto.LoginDTO;
import com.lims.dto.RefreshTokenDTO;
import com.lims.vo.LoginVO;
import com.lims.vo.MeVO;

/**
 * 认证服务（api-spec 认证域）
 */
public interface AuthService {

    /** 登录：校验用户名密码（BCrypt），签发 access/refresh 双 token */
    LoginVO login(LoginDTO dto);

    /** 刷新：校验 refreshToken，换发双 token（旧 refreshToken 作废由过期时间兜底） */
    LoginVO refresh(RefreshTokenDTO dto);

    /** 当前登录用户：信息 + 角色 + 权限标识 + 菜单树 */
    MeVO me();

    /** 退出：无状态 JWT 预留接口（审计/后续黑名单） */
    void logout();
}
