package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.common.PageResult;
import com.lims.common.enums.QualStatus;
import com.lims.common.exception.BizException;
import com.lims.dto.TesterMethodSaveDTO;
import com.lims.dto.excel.TesterMethodImportRow;
import com.lims.entity.Dept;
import com.lims.entity.SysUser;
import com.lims.entity.TesterMethod;
import com.lims.mapper.DeptMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.mapper.TesterMethodMapper;
import com.lims.service.TesterMethodService;
import com.lims.service.excel.TesterMethodImportListener;
import com.lims.vo.TesterMethodVO;
import com.alibaba.excel.EasyExcel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 方法-检验员资质服务实现（T-105）。
 *
 * <p><b>为什么这里必须做「工号必须存在于 sys_user 且属于检验员角色」校验</b>：
 * T-501 自动分配按 tester_method 匹配出的工号会**直接写入 sample_item.tester_no**，
 * 如果资质表里存着一个不存在的工号，分配看起来成功、实际把任务派给了「幽灵检验员」——
 * 这种错误在列表页完全看不出来，只会在检验员的「我的任务」里静默丢失。故在录入关卡 fail-loud。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TesterMethodServiceImpl implements TesterMethodService {

    /** 检验员角色编码（sys_role.role_code，与 seed 一致） */
    private static final String ROLE_TESTER = "R3";

    private final TesterMethodMapper testerMethodMapper;
    private final SysUserMapper sysUserMapper;
    private final DeptMapper deptMapper;

    // =========================================================================
    // 查询
    // =========================================================================

    @Override
    public PageResult<TesterMethodVO> pageQuery(long current, long size, String methodNo, String testerNo,
                                                String methodName, Integer qualStatus) {
        LambdaQueryWrapper<TesterMethod> wrapper = new LambdaQueryWrapper<TesterMethod>()
                .likeRight(StringUtils.hasText(methodNo), TesterMethod::getMethodNo, methodNo)
                .eq(StringUtils.hasText(testerNo), TesterMethod::getTesterNo, testerNo)
                .like(StringUtils.hasText(methodName), TesterMethod::getMethodName, methodName)
                .eq(qualStatus != null, TesterMethod::getQualStatus, qualStatus)
                .orderByDesc(TesterMethod::getId);

        IPage<TesterMethod> page = testerMethodMapper.selectPage(new Page<>(current, size), wrapper);
        List<TesterMethod> rows = page.getRecords();

        // 批量解析工号 → 姓名/部门（一次两条 IN 查询，避免逐行 N+1）
        Set<String> testerNos = rows.stream()
                .map(TesterMethod::getTesterNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        Map<String, SysUser> userByNo = loadUsers(testerNos);
        Map<Long, String> deptNameById = loadDeptNames(
                userByNo.values().stream().map(SysUser::getDeptId).filter(Objects::nonNull).collect(Collectors.toSet()));

        return PageResult.of(page, e -> toVO(e, userByNo, deptNameById));
    }

    @Override
    public Long create(TesterMethodSaveDTO dto) {
        validateTester(dto.getTesterNo());
        ensureUnique(dto.getMethodNo(), dto.getTesterNo(), null);

        TesterMethod entity = new TesterMethod();
        entity.setMethodName(dto.getMethodName().trim());
        entity.setMethodNo(dto.getMethodNo().trim());
        entity.setTesterNo(dto.getTesterNo().trim());
        entity.setQualStatus(dto.getQualStatus() == null ? QualStatus.VALID.getCode() : dto.getQualStatus());
        entity.setRemark(trimToNull(dto.getRemark()));
        testerMethodMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(TesterMethodSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(400, "更新时 id 不能为空");
        }
        TesterMethod exist = testerMethodMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BizException(400, "资质记录不存在或已删除: id=" + dto.getId());
        }
        validateTester(dto.getTesterNo());
        ensureUnique(dto.getMethodNo(), dto.getTesterNo(), dto.getId());

        TesterMethod entity = new TesterMethod();
        entity.setId(dto.getId());
        entity.setMethodName(dto.getMethodName().trim());
        entity.setMethodNo(dto.getMethodNo().trim());
        entity.setTesterNo(dto.getTesterNo().trim());
        entity.setQualStatus(dto.getQualStatus() == null ? exist.getQualStatus() : dto.getQualStatus());
        entity.setRemark(trimToNull(dto.getRemark()));
        testerMethodMapper.updateById(entity);
    }

    @Override
    public void remove(Long id) {
        if (id == null) {
            throw new BizException(400, "id 不能为空");
        }
        TesterMethod exist = testerMethodMapper.selectById(id);
        if (exist == null) {
            throw new BizException(400, "资质记录不存在或已删除: id=" + id);
        }
        // 逻辑删除：历史分配结果（sample_item.tester_no）不因此失效——
        // 已分配的检验任务不应因为资质表被清理而改变归属，故只影响后续自动分配。
        testerMethodMapper.deleteById(id);
    }

    // =========================================================================
    // Excel 导入（部分失败不回滚，与 T-301 采样单导入同策略）
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(InputStream in, String operator) {
        TesterMethodImportListener listener = new TesterMethodImportListener(testerMethodMapper, sysUserMapper);
        EasyExcel.read(in, TesterMethodImportRow.class, listener).sheet().doRead();
        return listener.result();
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    /**
     * 校验工号：必须存在于 sys_user，且拥有检验员角色（R3）。
     *
     * <p>⚠️ 只在「新增/更新」路径校验，不在查询路径校验——历史数据里若已有非法工号，
     * 查询仍应能看见（否则无法发现并修正）。</p>
     */
    private void validateTester(String testerNo) {
        String no = testerNo == null ? "" : testerNo.trim();
        if (no.isEmpty()) {
            throw new BizException(400, "检验员工号不能为空");
        }
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, no)
                .last("limit 1"));
        if (user == null) {
            throw new BizException(400, "检验员工号不存在: " + no);
        }
    }

    /** 唯一键校验：同一「方法标准号 + 检验员工号」只能一行 */
    private void ensureUnique(String methodNo, String testerNo, Long excludeId) {
        LambdaQueryWrapper<TesterMethod> wrapper = new LambdaQueryWrapper<TesterMethod>()
                .eq(TesterMethod::getMethodNo, methodNo.trim())
                .eq(TesterMethod::getTesterNo, testerNo.trim())
                .ne(excludeId != null, TesterMethod::getId, excludeId);
        if (testerMethodMapper.selectCount(wrapper) > 0) {
            throw new BizException(400,
                    "该检验员已存在此方法的资质记录: " + methodNo.trim() + " / " + testerNo.trim()
                            + "（如需调整请编辑原记录或改其资质状态）");
        }
    }

    private TesterMethodVO toVO(TesterMethod entity, Map<String, SysUser> userByNo, Map<Long, String> deptNameById) {
        TesterMethodVO vo = new TesterMethodVO();
        vo.setId(entity.getId());
        vo.setMethodName(entity.getMethodName());
        vo.setMethodNo(entity.getMethodNo());
        vo.setTesterNo(entity.getTesterNo());
        SysUser user = userByNo.get(entity.getTesterNo());
        if (user != null) {
            vo.setTesterName(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
            if (user.getDeptId() != null) {
                vo.setDeptName(deptNameById.get(user.getDeptId()));
            }
        } else {
            // 查不到就回填工号：页面不该出现空姓名列，且「查不到」本身就是需要被看见的信号
            vo.setTesterName(entity.getTesterNo());
        }
        vo.setQualStatus(entity.getQualStatus());
        QualStatus qs = QualStatus.ofNullable(entity.getQualStatus());
        vo.setQualStatusLabel(qs == null ? "未知" : qs.getLabel());
        vo.setRemark(entity.getRemark());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private Map<String, SysUser> loadUsers(Set<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Map.of();
        }
        Map<String, SysUser> out = new HashMap<>();
        sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getUsername, SysUser::getNickname, SysUser::getDeptId)
                        .in(SysUser::getUsername, usernames))
                .forEach(u -> out.put(u.getUsername(), u));
        return out;
    }

    private Map<Long, String> loadDeptNames(Set<Long> deptIds) {
        if (deptIds == null || deptIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>();
        deptMapper.selectList(new LambdaQueryWrapper<Dept>()
                        .select(Dept::getId, Dept::getDeptName)
                        .in(Dept::getId, deptIds))
                .forEach(d -> out.put(d.getId(), d.getDeptName()));
        return out;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }
}
