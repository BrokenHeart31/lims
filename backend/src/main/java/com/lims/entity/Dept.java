package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门（dept，业务表复用）。parent_id 支撑数据权限"本部门及下属部门"（AGENTS 8.3）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dept")
public class Dept extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 父部门 id，0=顶级 */
    private Long parentId;

    private String deptCode;

    private String deptName;

    private String leader;

    private String remark;
}
