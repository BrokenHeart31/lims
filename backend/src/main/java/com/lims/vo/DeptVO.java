package com.lims.vo;

import com.lims.entity.Dept;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门视图（api-spec 第 13 章，T-107）。树形：parent_id=0 为顶级。
 */
@Data
public class DeptVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long parentId;

    private String deptCode;

    private String deptName;

    private String leader;

    private String remark;

    /** 该部门下的用户数（删除前提示，或用于「部门概览」） */
    private Integer userCount;

    private List<DeptVO> children = new ArrayList<>();

    public static DeptVO from(Dept dept) {
        DeptVO vo = new DeptVO();
        vo.setId(dept.getId());
        vo.setParentId(dept.getParentId());
        vo.setDeptCode(dept.getDeptCode());
        vo.setDeptName(dept.getDeptName());
        vo.setLeader(dept.getLeader());
        vo.setRemark(dept.getRemark());
        return vo;
    }
}
