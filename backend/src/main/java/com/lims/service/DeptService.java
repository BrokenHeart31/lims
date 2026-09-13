package com.lims.service;

import com.lims.dto.DeptSaveDTO;
import com.lims.vo.DeptVO;

import java.util.List;

/**
 * 部门管理服务（api-spec 第 13 章，T-107）。
 *
 * <p>部门树支撑数据权限「本部门及下属部门」（AGENTS 8.3），因此父子关系是不变量：
 * 不允许自引用成环（把 A 挂到 A 的子孙下会让子树遍历无限递归）。</p>
 */
public interface DeptService {

    /** 部门树 */
    List<DeptVO> tree();

    /** 下拉选项（扁平，带层级缩进标记由前端处理） */
    List<DeptVO> listAll();

    DeptVO detail(Long id);

    Long create(DeptSaveDTO dto);

    void update(DeptSaveDTO dto);

    /**
     * 逻辑删除部门。
     *
     * <p>fail-loud 保护：存在子部门或存在所属用户时拒绝删除——用户 dept_id 悬空会让
     * 数据权限计算范围异常（查不到或查到不该看的）。</p>
     */
    void remove(Long id);
}
