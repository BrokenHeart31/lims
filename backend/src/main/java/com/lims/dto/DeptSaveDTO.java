package com.lims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增/编辑请求（api-spec 第 13 章，T-107）。
 *
 * <p>部门树 parent_id 支撑数据权限「本部门及下属部门」（AGENTS 8.3）：任务分配、查询范围
 * 都按部门子树收敛，因此 parent_id 的自引用成环必须被显式拒绝。</p>
 */
@Data
public class DeptSaveDTO {

    private Long id;

    /** 父部门 id，0=顶级 */
    @NotNull(message = "上级部门不能为空")
    private Long parentId;

    @NotBlank(message = "部门编码不能为空")
    @Size(max = 20, message = "部门编码不能超过 20 字符")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "部门编码只能包含字母、数字、下划线")
    private String deptCode;

    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50, message = "部门名称不能超过 50 字符")
    private String deptName;

    @Size(max = 50, message = "负责人不能超过 50 字符")
    private String leader;

    @Size(max = 255, message = "备注不能超过 255 字符")
    private String remark;
}
