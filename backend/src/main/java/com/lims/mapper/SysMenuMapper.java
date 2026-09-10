package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 用户的按钮权限标识集合（经 用户→角色→菜单 链路，去重） */
    @Select("SELECT DISTINCT m.permission FROM sys_menu m " +
            "JOIN sys_role_menu rm ON rm.menu_id = m.id " +
            "JOIN sys_user_role ur ON ur.role_id = rm.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 " +
            "WHERE ur.user_id = #{userId} AND m.deleted = 0 " +
            "AND m.menu_type = 3 AND m.permission IS NOT NULL AND m.permission <> ''")
    List<String> selectPermissionsByUserId(Long userId);

    /** 全部按钮权限标识（R100 综合管理专用） */
    @Select("SELECT permission FROM sys_menu " +
            "WHERE deleted = 0 AND menu_type = 3 AND permission IS NOT NULL AND permission <> ''")
    List<String> selectAllPermissions();

    /** 用户可见的菜单/目录（menu_type 1/2，按 sort_order 排序，去重） */
    @Select("SELECT DISTINCT m.* FROM sys_menu m " +
            "JOIN sys_role_menu rm ON rm.menu_id = m.id " +
            "JOIN sys_user_role ur ON ur.role_id = rm.role_id " +
            "JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 " +
            "WHERE ur.user_id = #{userId} AND m.deleted = 0 AND m.visible = 1 " +
            "AND m.menu_type IN (1, 2) ORDER BY m.sort_order, m.id")
    List<SysMenu> selectVisibleMenusByUserId(Long userId);

    /** 全部可见菜单/目录（R100 综合管理专用） */
    @Select("SELECT * FROM sys_menu " +
            "WHERE deleted = 0 AND visible = 1 AND menu_type IN (1, 2) " +
            "ORDER BY sort_order, id")
    List<SysMenu> selectAllVisibleMenus();
}
