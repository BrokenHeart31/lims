package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 角色-菜单关联（sys_role_menu）：角色权限载体。
 */
@Data
@TableName("sys_role_menu")
public class SysRoleMenu implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long menuId;
}
