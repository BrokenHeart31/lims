package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.ResultCode;
import com.lims.common.exception.BizException;
import com.lims.dto.LoginDTO;
import com.lims.dto.RefreshTokenDTO;
import com.lims.entity.Dept;
import com.lims.entity.SysUser;
import com.lims.mapper.DeptMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.security.JwtTokenProvider;
import com.lims.security.SecurityUtils;
import com.lims.service.AuthService;
import com.lims.service.SysPermissionService;
import com.lims.vo.LoginVO;
import com.lims.vo.MeVO;
import com.lims.vo.UserInfoVO;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final DeptMapper deptMapper;
    private final SysPermissionService permissionService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginVO login(LoginDTO dto) {
        SysUser user = findByUsername(dto.getUsername());
        // 防枚举：用户不存在与密码错误返回同一提示（api-spec 1.1）
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST.getCode(), "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号已停用，请联系管理员");
        }
        return issueTokens(user);
    }

    @Override
    public LoginVO refresh(RefreshTokenDTO dto) {
        Claims claims;
        try {
            claims = jwtTokenProvider.parseToken(dto.getRefreshToken());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "refreshToken 无效或已过期，请重新登录");
        }
        SysUser user = findByUsername(jwtTokenProvider.getUsername(claims));
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.UNAUTHORIZED.getCode(), "账号不存在或已停用");
        }
        return issueTokens(user);
    }

    @Override
    public MeVO me() {
        String username = SecurityUtils.requireUsername();
        SysUser user = findByUsername(username);
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        UserInfoVO userInfo = new UserInfoVO();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setNickname(user.getNickname());
        userInfo.setDeptId(user.getDeptId());
        if (user.getDeptId() != null) {
            Dept dept = deptMapper.selectById(user.getDeptId());
            userInfo.setDeptName(dept == null ? null : dept.getDeptName());
        }
        userInfo.setRoles(permissionService.getRoleCodes(user.getId()));

        MeVO me = new MeVO();
        me.setUser(userInfo);
        me.setPermissions(permissionService.getPermissions(user.getId()));
        me.setMenus(permissionService.getMenuTree(user.getId()));
        return me;
    }

    @Override
    public void logout() {
        // 无状态 JWT：服务端不做强制失效。预留审计日志（log:view 域落地后补）。
        log.info("用户退出登录: {}", SecurityUtils.getUsername().orElse("anonymous"));
    }

    /** 签发双 token（权限标识入 access_token 载荷，DB 为权威源） */
    private LoginVO issueTokens(SysUser user) {
        List<String> permissions = permissionService.getPermissions(user.getId());
        String accessToken = jwtTokenProvider.createAccessToken(user.getUsername(), permissions);
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUsername());
        return new LoginVO(accessToken, refreshToken, jwtTokenProvider.getAccessTokenTtl());
    }

    private SysUser findByUsername(String username) {
        return sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }
}
