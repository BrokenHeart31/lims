package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户（sys_user）。密码为 BCrypt 密文，任何 VO 禁止携带（AGENTS 4.2）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名/工号，如 nj001、njna000 */
    private String username;

    /** BCrypt 密码密文 */
    private String password;

    /** 姓名 */
    private String nickname;

    /** 所属部门 dept.id */
    private Long deptId;

    private String email;

    private String phone;

    /** 状态 1=启用 0=停用 */
    private Integer status;

    private String remark;
}
