package com.lims.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.entity.SysUser;
import com.lims.mapper.SysUserMapper;
import com.lims.service.SysPermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 登录用户装配（T-102 落地）：JWT 过滤器每请求按 username 加载，
 * 权限以数据库为权威源（token 内 perms claim 仅为签发时快照）。
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysPermissionService permissionService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return LoginUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .nickname(user.getNickname())
                .deptId(user.getDeptId())
                .enabled(user.getStatus() != null && user.getStatus() == 1)
                .authorities(permissionService.getPermissions(user.getId())
                        .stream()
                        .map(SimpleGrantedAuthority::new)
                        .toList())
                .build();
    }
}
