package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.common.PageResult;
import com.lims.common.exception.BizException;
import com.lims.dto.SysUserSaveDTO;
import com.lims.entity.Dept;
import com.lims.entity.SysRole;
import com.lims.entity.SysUser;
import com.lims.entity.SysUserRole;
import com.lims.mapper.DeptMapper;
import com.lims.mapper.SysRoleMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.mapper.SysUserRoleMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.SysUserService;
import com.lims.vo.SysUserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理实现（T-107）。
 *
 * <p>关键不变量：
 * <ol>
 *   <li>username 全局唯一且创建后不可变（历史快照靠它锚定，见接口注释）；</li>
 *   <li>密码单向：只有 create / resetPassword 写入 BCrypt 密文，VO 无密码字段；</li>
 *   <li>自锁保护：不能删自己、不能删掉最后一个 R100。</li>
 * </ol></p>
 *
 * <p>N+1 规避：列表页的 deptName / roleNames 用两次 IN 批量反查后在内存拼装，
 * 而不是逐行查库——6 个用户看不出差别，但真实客户 200+ 检验员时是 400 次查询。</p>
 */
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final SysRoleMapper sysRoleMapper;
    private final DeptMapper deptMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<SysUserVO> pageQuery(long current, long size, String username, String nickname,
                                           Long deptId, Integer status) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .like(StringUtils.hasText(username), SysUser::getUsername, username)
                .like(StringUtils.hasText(nickname), SysUser::getNickname, nickname)
                .eq(deptId != null, SysUser::getDeptId, deptId)
                .eq(status != null, SysUser::getStatus, status)
                .orderByAsc(SysUser::getId);

        Page<SysUser> page = sysUserMapper.selectPage(new Page<>(current, size), wrapper);
        List<SysUser> records = page.getRecords();

        // 批量反查部门名 + 角色，避免 N+1
        Map<Long, String> deptNameMap = loadDeptNames(records.stream()
                .map(SysUser::getDeptId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        Map<Long, List<String>> userRoleNames = loadRoleNamesByUserIds(
                records.stream().map(SysUser::getId).collect(java.util.stream.Collectors.toSet()));
        Map<Long, List<Long>> userRoleIds = loadRoleIdsByUserIds(
                records.stream().map(SysUser::getId).collect(java.util.stream.Collectors.toSet()));

        return PageResult.of(page, u -> toVO(u, deptNameMap, userRoleNames, userRoleIds));
    }

    @Override
    public SysUserVO detail(Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在或已删除");
        }
        Set<Long> ids = Set.of(id);
        return toVO(user,
                loadDeptNames(user.getDeptId() == null ? Set.of() : Set.of(user.getDeptId())),
                loadRoleNamesByUserIds(ids),
                loadRoleIdsByUserIds(ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysUserSaveDTO dto) {
        String username = dto.getUsername().trim();
        Long dup = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (dup != null && dup > 0) {
            throw new BizException(409, "登录名「" + username + "」已存在");
        }
        if (!StringUtils.hasText(dto.getPassword())) {
            throw new BizException(400, "新增用户必须设置初始密码");
        }
        validateDept(dto.getDeptId());

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname().trim());
        user.setDeptId(dto.getDeptId());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setSignatureUrl(dto.getSignatureUrl());
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        user.setRemark(dto.getRemark());
        sysUserMapper.insert(user);

        rebindRoles(user.getId(), dto.getRoleIds());
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(SysUserSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(400, "用户 id 不能为空");
        }
        SysUser exist = sysUserMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BizException(404, "用户不存在或已删除");
        }
        validateDept(dto.getDeptId());
        guardLastAdminStatusChange(exist, dto.getStatus());

        SysUser user = new SysUser();
        user.setId(dto.getId());
        // username 不写入：有意为之，见接口注释
        user.setNickname(dto.getNickname().trim());
        user.setDeptId(dto.getDeptId());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setSignatureUrl(dto.getSignatureUrl());
        user.setStatus(dto.getStatus());
        user.setRemark(dto.getRemark());
        sysUserMapper.updateById(user);

        if (dto.getRoleIds() != null) {
            rebindRoles(dto.getId(), dto.getRoleIds());
        }
    }

    @Override
    public void resetPassword(Long id, String rawPassword) {
        if (!StringUtils.hasText(rawPassword) || rawPassword.length() < 6) {
            throw new BizException(400, "密码长度不能少于 6 位");
        }
        SysUser exist = sysUserMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "用户不存在或已删除");
        }
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(passwordEncoder.encode(rawPassword));
        sysUserMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        SysUser exist = sysUserMapper.selectById(id);
        if (exist == null) {
            throw new BizException(404, "用户不存在或已删除");
        }
        String currentUsername = SecurityUtils.getUsername().orElse(null);
        if (currentUsername != null && currentUsername.equals(exist.getUsername())) {
            throw new BizException(409, "不能删除当前登录账号");
        }
        // 自锁保护：不允许删掉最后一个可用的综合管理员
        if (isAdminUser(id) && countActiveAdmins() <= 1) {
            throw new BizException(409, "系统必须保留至少一名综合管理（R100）账号");
        }
        sysUserMapper.deleteById(id);
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
    }

    // ==================== 内部方法 ====================

    /** 全量覆盖式重绑角色（先清后建） */
    private void rebindRoles(Long userId, List<Long> roleIds) {
        sysUserRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        Set<Long> distinct = new LinkedHashSet<>(roleIds);
        // 校验角色存在，避免写入悬空 role_id
        if (sysRoleMapper.selectBatchIds(distinct).size() != distinct.size()) {
            throw new BizException(400, "存在无效的角色 id");
        }
        for (Long roleId : distinct) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            sysUserRoleMapper.insert(ur);
        }
    }

    private void validateDept(Long deptId) {
        if (deptId != null && deptMapper.selectById(deptId) == null) {
            throw new BizException(400, "所属部门不存在");
        }
    }

    /** 编辑时若把最后一个可用 R100 账号停用，会造成全系统失权——拒绝 */
    private void guardLastAdminStatusChange(SysUser exist, Integer newStatus) {
        if (newStatus == null || newStatus == 1) {
            return;
        }
        if (exist.getStatus() != null && exist.getStatus() == 0) {
            return; // 本来就是停用状态，不构成新增风险
        }
        if (isAdminUser(exist.getId()) && countActiveAdmins() <= 1) {
            throw new BizException(409, "系统必须保留至少一名启用状态的综合管理（R100）账号");
        }
    }

    private boolean isAdminUser(Long userId) {
        List<SysRole> roles = sysRoleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .inSql(SysRole::getId,
                        "SELECT role_id FROM sys_user_role WHERE user_id = " + userId));
        return roles.stream().anyMatch(r -> SysRole.ADMIN_ROLE_CODE.equals(r.getRoleCode()));
    }

    private long countActiveAdmins() {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .inSql(SysUser::getId,
                        "SELECT ur.user_id FROM sys_user_role ur "
                                + "JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 "
                                + "WHERE r.role_code = '" + SysRole.ADMIN_ROLE_CODE + "'"));
        return count == null ? 0 : count;
    }

    private Map<Long, String> loadDeptNames(Set<Long> deptIds) {
        if (deptIds.isEmpty()) {
            return Map.of();
        }
        List<Dept> depts = deptMapper.selectBatchIds(deptIds);
        Map<Long, String> map = new HashMap<>();
        for (Dept d : depts) {
            map.put(d.getId(), d.getDeptName());
        }
        return map;
    }

    private Map<Long, List<Long>> loadRoleIdsByUserIds(Set<Long> userIds) {
        Map<Long, List<Long>> map = new HashMap<>();
        if (userIds.isEmpty()) {
            return map;
        }
        List<SysUserRole> links = sysUserRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .in(SysUserRole::getUserId, userIds));
        for (SysUserRole link : links) {
            map.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(link.getRoleId());
        }
        return map;
    }

    private Map<Long, List<String>> loadRoleNamesByUserIds(Set<Long> userIds) {
        Map<Long, List<String>> result = new HashMap<>();
        if (userIds.isEmpty()) {
            return result;
        }
        Map<Long, List<Long>> roleIdsByUser = loadRoleIdsByUserIds(userIds);
        Set<Long> allRoleIds = new HashSet<>();
        roleIdsByUser.values().forEach(allRoleIds::addAll);
        if (allRoleIds.isEmpty()) {
            return result;
        }
        Map<Long, String> roleNameById = new HashMap<>();
        for (SysRole role : sysRoleMapper.selectBatchIds(allRoleIds)) {
            roleNameById.put(role.getId(), role.getRoleName());
        }
        for (Map.Entry<Long, List<Long>> entry : roleIdsByUser.entrySet()) {
            List<String> names = new ArrayList<>();
            for (Long roleId : entry.getValue()) {
                String name = roleNameById.get(roleId);
                if (name != null) {
                    names.add(name);
                }
            }
            result.put(entry.getKey(), names);
        }
        return result;
    }

    private SysUserVO toVO(SysUser user, Map<Long, String> deptNameMap,
                           Map<Long, List<String>> roleNames, Map<Long, List<Long>> roleIds) {
        SysUserVO vo = new SysUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setDeptId(user.getDeptId());
        vo.setDeptName(user.getDeptId() == null ? null : deptNameMap.get(user.getDeptId()));
        vo.setEmail(user.getEmail());
        vo.setPhone(user.getPhone());
        vo.setSignatureUrl(user.getSignatureUrl());
        vo.setStatus(user.getStatus());
        vo.setRemark(user.getRemark());
        vo.setRoleIds(roleIds.getOrDefault(user.getId(), List.of()));
        vo.setRoleNames(roleNames.getOrDefault(user.getId(), List.of()));
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }
}
