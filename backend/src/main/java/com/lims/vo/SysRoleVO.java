package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色视图（api-spec 第 13 章，T-107）。
 */
@Data
public class SysRoleVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    /** 该角色绑定的菜单/权限 id 集合（编辑弹窗回显勾选态） */
    private List<Long> menuIds;

    /** 绑定的用户数（删除前提示「该角色下还有 N 个用户」，避免误删致用户失权） */
    private Integer userCount;

    /** ⚠️ LocalDateTime 必须显式 @JsonFormat（jackson.date-format 对 JSR-310 不生效），与项目其余 VO 保持一致 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
