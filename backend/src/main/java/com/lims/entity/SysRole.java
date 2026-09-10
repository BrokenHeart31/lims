package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色（sys_role）。预置编码见 AGENTS 8.1：R100 综合管理 / R1 样品登记员 / R2 任务管理员 / R3 检验员。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    /** 综合管理员角色编码（拥有全部权限与菜单） */
    public static final String ADMIN_ROLE_CODE = "R100";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色编码，如 R100 */
    private String roleCode;

    /** 角色名称，如 综合管理 */
    private String roleName;

    private String description;
}
