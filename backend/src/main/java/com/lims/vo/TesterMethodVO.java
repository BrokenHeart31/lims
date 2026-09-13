package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 方法-检验员资质列表行（api-spec 第 11 章，T-105）。
 *
 * <p>出网字段刻意含 `testerName`（工号 → sys_user.nickname 反查）：页面直接展示姓名，
 * 避免前端对每一行再发一次用户查询（N+1）。工号仍保留，供「按工号筛选」使用。</p>
 */
@Data
public class TesterMethodVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String methodName;
    private String methodNo;
    private String testerNo;

    /** 检验员姓名（sys_user.nickname；查不到时回填工号，不返回 null 以免页面空列） */
    private String testerName;

    /** 检验员所属部门名称（sys_user.dept_id → dept.dept_name） */
    private String deptName;

    private Integer qualStatus;

    /** 资质状态中文标签：有效 / 失效（由枚举派生，前端不再自行映射） */
    private String qualStatusLabel;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
