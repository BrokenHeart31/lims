package com.lims.controller;

import com.lims.common.R;
import com.lims.dto.LoginDTO;
import com.lims.dto.RefreshTokenDTO;
import com.lims.service.AuthService;
import com.lims.vo.LoginVO;
import com.lims.vo.MeVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证域接口（api-spec 第 1 章）。路径相对 context-path /api。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 1.1 登录（公开） */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /** 1.2 刷新令牌（公开） */
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return R.ok(authService.refresh(dto));
    }

    /** 1.3 当前登录用户信息 */
    @GetMapping("/me")
    public R<MeVO> me() {
        return R.ok(authService.me());
    }

    /** 1.4 退出登录（预留） */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }
}
