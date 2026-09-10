package com.lims.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户信息（api-spec 1.3 user 节点）。脱敏：禁止出现 password/salt。
 */
@Data
public class UserInfoVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String username;

    private String nickname;

    private Long deptId;

    private String deptName;

    /** 角色编码集合，如 ["R100"] */
    private List<String> roles;
}
