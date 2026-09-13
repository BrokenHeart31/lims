package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.exception.BizException;
import com.lims.dto.DeptSaveDTO;
import com.lims.entity.Dept;
import com.lims.entity.SysUser;
import com.lims.mapper.DeptMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.service.DeptService;
import com.lims.vo.DeptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 部门管理实现（T-107）。
 *
 * <p>部门树是数据权限的骨架（AGENTS 8.3「本部门及下属部门」）。因此删除与移动都必须严格：
 * 删有子部门/有用户的部门 = 制造孤儿数据，会让权限范围计算静默错位——比报错危险得多。</p>
 */
@Service
@RequiredArgsConstructor
public class DeptServiceImpl implements DeptService {

    private final DeptMapper deptMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public List<DeptVO> tree() {
        List<Dept> depts = deptMapper.selectList(new LambdaQueryWrapper<Dept>()
                .orderByAsc(Dept::getParentId)
                .orderByAsc(Dept::getId));
        Map<Long, Integer> userCounts = countUsersByDept();
        return buildTree(depts, userCounts);
    }

    @Override
    public List<DeptVO> listAll() {
        List<Dept> depts = deptMapper.selectList(new LambdaQueryWrapper<Dept>()
                .orderByAsc(Dept::getParentId)
                .orderByAsc(Dept::getId));
        Map<Long, Integer> userCounts = countUsersByDept();
        List<DeptVO> result = new ArrayList<>();
        for (Dept dept : depts) {
            DeptVO vo = DeptVO.from(dept);
            vo.setUserCount(userCounts.getOrDefault(dept.getId(), 0));
            result.add(vo);
        }
        return result;
    }

    @Override
    public DeptVO detail(Long id) {
        Dept dept = deptMapper.selectById(id);
        if (dept == null) {
            throw new BizException(404, "部门不存在或已删除");
        }
        return DeptVO.from(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(DeptSaveDTO dto) {
        validateUniqueCode(dto.getDeptCode(), null);
        validateParent(dto.getParentId(), null);

        Dept dept = new Dept();
        copy(dept, dto);
        deptMapper.insert(dept);
        return dept.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(DeptSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(400, "部门 id 不能为空");
        }
        Dept exist = deptMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BizException(404, "部门不存在或已删除");
        }
        validateUniqueCode(dto.getDeptCode(), dto.getId());
        validateParent(dto.getParentId(), dto.getId());

        Dept dept = new Dept();
        dept.setId(dto.getId());
        copy(dept, dto);
        deptMapper.updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        Dept exist = deptMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "部门不存在或已删除");
        }
        Long childCount = deptMapper.selectCount(new LambdaQueryWrapper<Dept>()
                .eq(Dept::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BizException(409, "该部门下还有 " + childCount + " 个子部门，请先删除子部门");
        }
        Long userCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getDeptId, id));
        if (userCount != null && userCount > 0) {
            throw new BizException(409, "该部门下还有 " + userCount + " 个用户，请先调整用户所属部门");
        }
        deptMapper.deleteById(id);
    }

    // ==================== 内部方法 ====================

    private void copy(Dept dept, DeptSaveDTO dto) {
        dept.setParentId(dto.getParentId());
        dept.setDeptCode(dto.getDeptCode().trim());
        dept.setDeptName(dto.getDeptName().trim());
        dept.setLeader(dto.getLeader());
        dept.setRemark(dto.getRemark());
    }

    private void validateUniqueCode(String deptCode, Long selfId) {
        LambdaQueryWrapper<Dept> wrapper = new LambdaQueryWrapper<Dept>()
                .eq(Dept::getDeptCode, deptCode.trim());
        if (selfId != null) {
            wrapper.ne(Dept::getId, selfId);
        }
        Long dup = deptMapper.selectCount(wrapper);
        if (dup != null && dup > 0) {
            throw new BizException(409, "部门编码「" + deptCode.trim() + "」已存在");
        }
    }

    /** 父部门校验：存在 + 不成环（沿 parent_id 上溯，途经自身即成环） */
    private void validateParent(Long parentId, Long selfId) {
        if (parentId == null) {
            throw new BizException(400, "上级部门不能为空（顶级请传 0）");
        }
        if (parentId == 0L) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BizException(400, "上级部门不能是自己");
        }
        if (deptMapper.selectById(parentId) == null) {
            throw new BizException(400, "上级部门不存在");
        }
        if (selfId == null) {
            return;
        }
        Long cursor = parentId;
        int guard = 0;
        while (cursor != null && cursor != 0L && guard++ < 64) {
            if (cursor.equals(selfId)) {
                throw new BizException(400, "上级部门不能是自己的下级部门（会形成环路）");
            }
            Dept ancestor = deptMapper.selectById(cursor);
            cursor = ancestor == null ? null : ancestor.getParentId();
        }
    }

    private Map<Long, Integer> countUsersByDept() {
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .select(SysUser::getDeptId)
                .isNotNull(SysUser::getDeptId));
        Map<Long, Integer> map = new HashMap<>();
        for (SysUser user : users) {
            map.merge(user.getDeptId(), 1, Integer::sum);
        }
        return map;
    }

    private List<DeptVO> buildTree(List<Dept> depts, Map<Long, Integer> userCounts) {
        Map<Long, DeptVO> nodeMap = new LinkedHashMap<>();
        for (Dept dept : depts) {
            DeptVO vo = DeptVO.from(dept);
            vo.setUserCount(userCounts.getOrDefault(dept.getId(), 0));
            nodeMap.put(dept.getId(), vo);
        }
        List<DeptVO> roots = new ArrayList<>();
        for (DeptVO node : nodeMap.values()) {
            DeptVO parent = node.getParentId() == null ? null : nodeMap.get(node.getParentId());
            if (parent != null && !parent.getId().equals(node.getId())) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }
}
