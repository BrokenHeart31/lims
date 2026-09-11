package com.lims.security;

import com.lims.common.ResultCode;
import com.lims.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * 安全上下文工具：Service/Controller 获取当前登录人。
 *
 * <p>[骨架] T-102 前 filter 放入的 principal 是 username 字符串；
 * T-102 后切换为 {@link LoginUser}，两类取值方法均已兼容。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 当前登录用户名；未认证返回 empty */
    public static Optional<String> getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof LoginUser loginUser) {
            return Optional.ofNullable(loginUser.getUsername());
        }
        if (principal instanceof String username) {
            return Optional.of(username);
        }
        return Optional.empty();
    }

    /** 当前登录用户主体；未认证或骨架阶段（principal 为字符串）返回 empty */
    public static Optional<LoginUser> getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return Optional.of(loginUser);
        }
        return Optional.empty();
    }

    /** 必须已登录：取用户名，否则抛 401 业务异常 */
    public static String requireUsername() {
        return getUsername()
                .orElseThrow(() -> new BizException(ResultCode.UNAUTHORIZED));
    }
}
