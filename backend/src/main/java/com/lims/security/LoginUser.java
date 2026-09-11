package com.lims.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.util.Collection;

/**
 * 登录用户主体（UserDetails）。
 *
 * <p>[骨架] T-102 落地 RBAC 六表后，由 UserDetailsServiceImpl 按 sys_user 装配本对象，
 * authorities 为该用户全部权限标识（resource:action，AGENTS.md 8.2）。
 * 密码仅存 BCrypt 密文，任何 VO 禁止携带本字段出网（AGENTS.md 4.2）。</p>
 */
@Data
@Builder
@AllArgsConstructor
public class LoginUser implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户主键 */
    private Long id;

    /** 登录名（工号，如 nj001 / njna000） */
    private String username;

    /** BCrypt 密码密文（禁止出网） */
    private String password;

    /** 姓名/昵称 */
    private String nickname;

    /** 所属部门 id（数据权限：本部门及下属） */
    private Long deptId;

    /** 账号是否启用 */
    private boolean enabled;

    /** 权限标识集合 */
    private Collection<? extends GrantedAuthority> authorities;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
