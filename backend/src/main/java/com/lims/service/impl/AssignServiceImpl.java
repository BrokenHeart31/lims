package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lims.common.enums.AssignType;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;
import com.lims.common.exception.BizException;
import com.lims.entity.Sample;
import com.lims.entity.SampleItem;
import com.lims.entity.SysUser;
import com.lims.entity.TesterMethod;
import com.lims.entity.UserMethod;
import com.lims.mapper.SampleItemMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.mapper.TesterMethodMapper;
import com.lims.mapper.UserMethodMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.AssignService;
import com.lims.vo.AssignAutoResultVO;
import com.lims.vo.AssignDetailVO;
import com.lims.vo.AssignPendingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 检验任务安排服务实现（T-501）。
 *
 * <p><b>分配规则</b>（AGENTS 7.4，契约 api-spec 5.0）：
 * <ol>
 *   <li><b>分类规则（优先）</b>：样品编号含 {@code NA}/{@code XA}/{@code SA} → 对应共享检验员；
 *       代码→工号映射以旧表 {@code user_method} 为数据源，缺行时回退 AGENTS 约定工号。</li>
 *   <li><b>方法资质规则</b>：分类规则未命中时，按单项 {@code methods}（{@code #} 分隔）
 *       匹配 {@code tester_method.method_no}（{@code qual_status=1}）。</li>
 *   <li><b>兜底</b>：两条都未命中 → 保持「待指派」并给出可读原因，
 *       <b>绝不默认指派任意检验员</b>（fail-loud，避免把报告结论交到无资质者手里）。</li>
 * </ol>
 *
 * <p><b>为什么指派粒度是「检测单项」而非「样品」</b>：同一样品的不同单项方法不同，
 * 可能分属不同资质范围的检验员；且 T-601 结果录入以单项为工作单元，
 * 指派结果随单项落库后，录入阶段可直接按 {@code tester_no} 过滤「我的任务」。</p>
 *
 * <p><b>为什么复用 sample_item 而非新建指派表</b>：指派是单项的 1:1 属性（非历史流水），
 * 落在单项行上可避免关联查询与 N+1；改派由 {@code assign_type=3} + 审计字段留痕。</p>
 */
@Service
@RequiredArgsConstructor
public class AssignServiceImpl extends ServiceImpl<SampleItemMapper, SampleItem> implements AssignService {

    private final SampleMapper sampleMapper;
    private final SysUserMapper sysUserMapper;
    private final UserMethodMapper userMethodMapper;
    private final TesterMethodMapper testerMethodMapper;

    /** 分类代码的固定匹配顺序（编号同时含多个代码时取先命中者） */
    private static final List<String> CATEGORY_ORDER = List.of("NA", "XA", "SA");

    /** 分类代码 → 默认检验员工号（AGENTS 7.4；user_method 缺行时兜底） */
    private static final Map<String, String> CATEGORY_FALLBACK = Map.of(
            "NA", "njna000",
            "XA", "njxa000",
            "SA", "njsa000");

    private static final int ASSIGNED = 1;
    private static final int NOT_ASSIGNED = 0;
    private static final int QUAL_VALID = 1;
    private static final int USER_ENABLED = 1;

    private static final String SOURCE_CATEGORY = "CATEGORY";
    private static final String SOURCE_METHOD = "METHOD";

    // =========================================================================
    // 5.2 分页查询待安排样品
    // =========================================================================

    @Override
    public Page<AssignPendingVO> pagePending(long pageNum, long pageSize, String sampleNo, String sampleName) {
        Page<Sample> page = new Page<>(pageNum, pageSize);
        Page<Sample> result = sampleMapper.selectPage(page, new LambdaQueryWrapper<Sample>()
                .eq(Sample::getStatus, SampleStatus.S30)
                // 只列出「已分解出明细」的样品；子查询与逻辑删除约定保持一致（deleted = 0）
                .inSql(Sample::getId, "SELECT DISTINCT sample_id FROM sample_item WHERE deleted = 0")
                .like(StringUtils.hasText(sampleNo), Sample::getSampleNo, sampleNo)
                .like(StringUtils.hasText(sampleName), Sample::getSampleName, sampleName)
                .orderByDesc(Sample::getId));

        List<Long> ids = result.getRecords().stream().map(Sample::getId).toList();
        Map<Long, List<SampleItem>> itemsBySample = ids.isEmpty() ? Collections.emptyMap()
                : baseMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                        .in(SampleItem::getSampleId, ids))
                .stream()
                .collect(Collectors.groupingBy(SampleItem::getSampleId));

        List<AssignPendingVO> rows = result.getRecords().stream().map(s -> {
            List<SampleItem> items = itemsBySample.getOrDefault(s.getId(), Collections.emptyList());
            AssignPendingVO vo = new AssignPendingVO();
            vo.setId(s.getId());
            vo.setSampleNo(s.getSampleNo());
            vo.setSampleName(s.getSampleName());
            vo.setClientName(s.getClientName());
            vo.setTaskNo(s.getTaskNo());
            vo.setStatus(s.getStatus() == null ? null : s.getStatus().getCode());
            vo.setStatusLabel(s.getStatusLabel());
            vo.setSamplingDate(s.getSamplingDate());
            vo.setInspectType(s.getInspectType());
            vo.setAssignTotal(items.size());
            vo.setAssignDone(countAssigned(items));
            return vo;
        }).toList();

        Page<AssignPendingVO> out = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        out.setRecords(rows);
        return out;
    }

    // =========================================================================
    // 5.3 查询样品安排明细
    // =========================================================================

    @Override
    public AssignDetailVO detail(Long sampleId) {
        Sample sample = requireSample(sampleId);
        List<SampleItem> items = listItems(sampleId);

        AssignDetailVO vo = new AssignDetailVO();
        vo.setSampleId(sample.getId());
        vo.setSampleNo(sample.getSampleNo());
        vo.setSampleName(sample.getSampleName());
        vo.setStatus(sample.getStatus() == null ? null : sample.getStatus().getCode());
        vo.setStatusLabel(sample.getStatusLabel());
        vo.setAssignTotal(items.size());
        int done = countAssigned(items);
        vo.setAssignDone(done);
        vo.setInputPermitted(!items.isEmpty() && done == items.size());
        vo.setItems(items.stream().map(this::toItemVO).toList());
        vo.setCandidates(buildCandidates(sample, items));
        return vo;
    }

    // =========================================================================
    // 5.4 执行自动分配
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignAutoResultVO autoAssign(Long sampleId) {
        Sample sample = requireSample(sampleId);
        requireStatus(sample, SampleStatus.S30, "执行任务安排");

        List<SampleItem> items = listItems(sampleId);
        if (items.isEmpty()) {
            throw new BizException(400, "该样品尚未完成项目分解，无可安排的检测单项");
        }

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();

        CategoryHit category = resolveCategory(sample.getSampleNo());
        int assigned = 0;
        int pending = 0;
        List<AssignAutoResultVO.Detail> details = new ArrayList<>();

        for (SampleItem item : items) {
            // 已人工改派的项不覆盖（人工意图优先于自动规则，且保证「自动」可重跑幂等）
            boolean manual = Objects.equals(item.getAssignType(), AssignType.MANUAL.getCode())
                    && Objects.equals(item.getAssignStatus(), ASSIGNED);
            if (manual) {
                assigned++;
                details.add(toAutoDetail(item, null));
                continue;
            }

            String testerNo = null;
            AssignType type = AssignType.NONE;
            String reason;

            if (category != null) {
                testerNo = category.testerNo();
                type = AssignType.CATEGORY;
                reason = null;
            } else {
                MethodHit hit = resolveMethodTester(item.getMethods());
                if (hit != null) {
                    testerNo = hit.testerNo();
                    type = AssignType.METHOD;
                    reason = null;
                } else {
                    reason = StringUtils.hasText(item.getMethods())
                            ? "未命中分类规则，且检验方法「" + item.getMethods() + "」无有资质检验员"
                            : "未命中分类规则，且该单项未维护检验方法，无法做资质匹配";
                }
            }

            if (testerNo != null) {
                applyAssign(item.getId(), testerNo, type, operator, now);
                item.setTesterNo(testerNo);
                item.setAssignType(type.getCode());
                item.setAssignStatus(ASSIGNED);
                assigned++;
                details.add(toAutoDetail(item, null));
            } else {
                applyUnassign(item.getId(), operator, now);
                item.setTesterNo(null);
                item.setTesterName(null);
                item.setAssignType(AssignType.NONE.getCode());
                item.setAssignStatus(NOT_ASSIGNED);
                pending++;
                details.add(toAutoDetail(item, reason));
            }
        }

        // 回填姓名（批量查询 sys_user，避免逐项查询）
        fillTesterNames(details.stream()
                .map(AssignAutoResultVO.Detail::getTesterNo)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new)));

        AssignAutoResultVO out = new AssignAutoResultVO();
        out.setSampleId(sampleId);
        out.setTotal(items.size());
        out.setAssigned(assigned);
        out.setPending(pending);
        out.setDetails(details);
        return out;
    }

    // =========================================================================
    // 5.5 人工改派
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AssignDetailVO.Item reassign(Long itemId, String testerNo) {
        SampleItem item = baseMapper.selectById(itemId);
        if (item == null) {
            throw new BizException(400, "检测单项不存在或已删除: id=" + itemId);
        }
        Sample sample = requireSample(item.getSampleId());
        requireStatus(sample, SampleStatus.S30, "人工改派");

        String target = testerNo == null ? null : testerNo.trim();
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, target)
                .last("LIMIT 1"));
        if (user == null || !Objects.equals(user.getStatus(), USER_ENABLED)) {
            throw new BizException(400, "检验员不存在或已停用: " + target);
        }
        if (!hasQualification(sample, item, target)) {
            // AGENTS 7.4「仅列出有资质者」：无资质者不可被指派
            throw new BizException(400,
                    "检验员「" + user.getNickname() + "」对检测单项「" + item.getItemName() + "」无检验资质，不允许改派");
        }

        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();
        applyAssign(item.getId(), target, AssignType.MANUAL, operator, now);

        item.setTesterNo(target);
        item.setTesterName(user.getNickname());
        item.setAssignType(AssignType.MANUAL.getCode());
        item.setAssignStatus(ASSIGNED);
        item.setAssignedAt(now);
        return toItemVO(item);
    }

    // =========================================================================
    // 5.6 安排确认 S30 → S40
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirm(Long sampleId) {
        Sample sample = requireSample(sampleId);
        requireStatus(sample, SampleStatus.S30, "安排确认");

        List<SampleItem> items = listItems(sampleId);
        if (items.isEmpty()) {
            throw new BizException(400, "该样品尚未完成项目分解，无法安排");
        }
        long pending = items.stream()
                .filter(i -> !Objects.equals(i.getAssignStatus(), ASSIGNED))
                .count();
        if (pending > 0) {
            // 不允许带「待指派」项流转——否则后续录入会出现无归属的检测任务
            throw new BizException(400, "仍有 " + pending + " 个检测单项待指派，请先完成安排");
        }

        SampleStatusTransition.assertTransition(SampleStatus.S30, SampleStatus.S40);

        int updated = sampleMapper.update(null, new LambdaUpdateWrapper<Sample>()
                .eq(Sample::getId, sampleId)
                .eq(Sample::getStatus, SampleStatus.S30)
                .set(Sample::getStatus, SampleStatus.S40));
        if (updated == 0) {
            throw new BizException(400, "样品状态已变更，请刷新后重试");
        }
        return SampleStatus.S40.getCode();
    }

    // =========================================================================
    // 规则实现
    // =========================================================================

    /** 分类规则结果 */
    private record CategoryHit(String code, String testerNo) {
    }

    /** 方法资质规则结果 */
    private record MethodHit(String methodNo, String testerNo) {
    }

    /**
     * 分类规则：按样品编号中的 NA/XA/SA 命中共享检验员。
     *
     * <p>代码→工号映射优先取旧表 {@code user_method}（数据可配置），缺行时回退 AGENTS 7.4 约定工号。</p>
     */
    private CategoryHit resolveCategory(String sampleNo) {
        if (!StringUtils.hasText(sampleNo)) {
            return null;
        }
        String upper = sampleNo.toUpperCase(Locale.ROOT);

        Map<String, String> configured = new LinkedHashMap<>();
        List<UserMethod> rows = userMethodMapper.selectList(new LambdaQueryWrapper<UserMethod>()
                .in(UserMethod::getMethod, CATEGORY_ORDER));
        for (UserMethod row : rows) {
            if (StringUtils.hasText(row.getMethod()) && StringUtils.hasText(row.getUsergh())) {
                configured.putIfAbsent(row.getMethod().trim().toUpperCase(Locale.ROOT), row.getUsergh().trim());
            }
        }

        for (String code : CATEGORY_ORDER) {
            if (upper.contains(code)) {
                String testerNo = configured.getOrDefault(code, CATEGORY_FALLBACK.get(code));
                if (StringUtils.hasText(testerNo)) {
                    return new CategoryHit(code, testerNo);
                }
            }
        }
        return null;
    }

    /**
     * 方法资质规则：按单项 methods 匹配 tester_method（qual_status=1）。
     *
     * <p>多个方法命中多个检验员时，按 {@code tester_method.id} 升序取第一条 —— 保证结果**确定性**
     * （同一数据重复执行必须得到同一指派，否则「可重跑」语义不成立）。</p>
     */
    private MethodHit resolveMethodTester(String methods) {
        List<String> methodNos = splitMethods(methods);
        if (methodNos.isEmpty()) {
            return null;
        }
        List<TesterMethod> rows = testerMethodMapper.selectList(new LambdaQueryWrapper<TesterMethod>()
                .in(TesterMethod::getMethodNo, methodNos)
                .eq(TesterMethod::getQualStatus, QUAL_VALID)
                .orderByAsc(TesterMethod::getId));
        if (rows.isEmpty()) {
            return null;
        }
        TesterMethod first = rows.get(0);
        if (!StringUtils.hasText(first.getTesterNo())) {
            return null;
        }
        return new MethodHit(first.getMethodNo(), first.getTesterNo().trim());
    }

    /** 判断某检验员对某单项是否具备资质（分类规则命中 或 方法资质命中） */
    private boolean hasQualification(Sample sample, SampleItem item, String testerNo) {
        CategoryHit category = resolveCategory(sample.getSampleNo());
        if (category != null && category.testerNo().equals(testerNo)) {
            return true;
        }
        List<String> methodNos = splitMethods(item.getMethods());
        if (methodNos.isEmpty()) {
            return false;
        }
        Long count = testerMethodMapper.selectCount(new LambdaQueryWrapper<TesterMethod>()
                .in(TesterMethod::getMethodNo, methodNos)
                .eq(TesterMethod::getTesterNo, testerNo)
                .eq(TesterMethod::getQualStatus, QUAL_VALID));
        return count != null && count > 0;
    }

    /**
     * 候选检验员列表：**仅含有资质者**（AGENTS 7.4）。
     *
     * <p>来源二选：分类规则命中的共享检验员（source=CATEGORY）、
     * 以及对该样品任一单项方法有资质的检验员（source=METHOD，带命中的方法号）。</p>
     */
    private List<AssignDetailVO.Candidate> buildCandidates(Sample sample, List<SampleItem> items) {
        Map<String, AssignDetailVO.Candidate> map = new LinkedHashMap<>();

        CategoryHit category = resolveCategory(sample.getSampleNo());
        if (category != null) {
            AssignDetailVO.Candidate c = new AssignDetailVO.Candidate();
            c.setTesterNo(category.testerNo());
            c.setSource(SOURCE_CATEGORY);
            map.put(category.testerNo(), c);
        }

        Set<String> methodNos = items.stream()
                .flatMap(i -> splitMethods(i.getMethods()).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!methodNos.isEmpty()) {
            List<TesterMethod> rows = testerMethodMapper.selectList(new LambdaQueryWrapper<TesterMethod>()
                    .in(TesterMethod::getMethodNo, methodNos)
                    .eq(TesterMethod::getQualStatus, QUAL_VALID)
                    .orderByAsc(TesterMethod::getId));
            for (TesterMethod row : rows) {
                if (!StringUtils.hasText(row.getTesterNo())) {
                    continue;
                }
                String no = row.getTesterNo().trim();
                AssignDetailVO.Candidate c = map.computeIfAbsent(no, k -> {
                    AssignDetailVO.Candidate created = new AssignDetailVO.Candidate();
                    created.setTesterNo(k);
                    created.setSource(SOURCE_METHOD);
                    return created;
                });
                if (!StringUtils.hasText(c.getMatchedMethodNo())) {
                    c.setMatchedMethodNo(row.getMethodNo());
                }
            }
        }

        fillTesterNames(map.keySet());
        map.values().forEach(c -> c.setTesterName(testerNameMap.get(c.getTesterNo())));
        return new ArrayList<>(map.values());
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    /** 最近一次姓名批量查询的缓存（仅在单次方法调用内有效，避免逐条查 sys_user） */
    private Map<String, String> testerNameMap = Collections.emptyMap();

    private void fillTesterNames(Set<String> testerNos) {
        if (testerNos == null || testerNos.isEmpty()) {
            testerNameMap = Collections.emptyMap();
            return;
        }
        testerNameMap = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .in(SysUser::getUsername, testerNos))
                .stream()
                .filter(u -> StringUtils.hasText(u.getUsername()))
                .collect(Collectors.toMap(SysUser::getUsername, SysUser::getNickname, (a, b) -> a));
    }

    private void applyAssign(Long itemId, String testerNo, AssignType type, String operator, LocalDateTime now) {
        baseMapper.update(null, new LambdaUpdateWrapper<SampleItem>()
                .eq(SampleItem::getId, itemId)
                .set(SampleItem::getAssignStatus, ASSIGNED)
                .set(SampleItem::getAssignType, type.getCode())
                .set(SampleItem::getTesterNo, testerNo)
                .set(SampleItem::getAssignedAt, now)
                .set(SampleItem::getAssignedBy, operator)
                .set(SampleItem::getUpdatedBy, operator)
                .set(SampleItem::getUpdatedAt, now));
    }

    private void applyUnassign(Long itemId, String operator, LocalDateTime now) {
        baseMapper.update(null, new LambdaUpdateWrapper<SampleItem>()
                .eq(SampleItem::getId, itemId)
                .set(SampleItem::getAssignStatus, NOT_ASSIGNED)
                .set(SampleItem::getAssignType, AssignType.NONE.getCode())
                .set(SampleItem::getTesterNo, null)
                .set(SampleItem::getTesterName, null)
                .set(SampleItem::getAssignedAt, null)
                .set(SampleItem::getAssignedBy, null)
                .set(SampleItem::getUpdatedBy, operator)
                .set(SampleItem::getUpdatedAt, now));
    }

    /** 拆分 methods（{@code #} 分隔，忽略空段与首尾 #） */
    private List<String> splitMethods(String methods) {
        if (!StringUtils.hasText(methods)) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(methods.split("#"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private int countAssigned(List<SampleItem> items) {
        return (int) items.stream().filter(i -> Objects.equals(i.getAssignStatus(), ASSIGNED)).count();
    }

    private List<SampleItem> listItems(Long sampleId) {
        return baseMapper.selectList(new LambdaQueryWrapper<SampleItem>()
                .eq(SampleItem::getSampleId, sampleId)
                .orderByAsc(SampleItem::getItemOrder));
    }

    private Sample requireSample(Long sampleId) {
        if (sampleId == null) {
            throw new BizException(400, "样品ID不能为空");
        }
        Sample sample = sampleMapper.selectById(sampleId);
        if (sample == null) {
            throw new BizException(400, "样品不存在或已删除: id=" + sampleId);
        }
        return sample;
    }

    private void requireStatus(Sample sample, SampleStatus expected, String action) {
        if (sample.getStatus() != expected) {
            throw new BizException(400, "样品当前状态为「" + sample.getStatusLabel()
                    + "」，不允许" + action + "（要求「" + expected.getLabel() + "」）");
        }
    }

    private AssignDetailVO.Item toItemVO(SampleItem item) {
        AssignDetailVO.Item vo = new AssignDetailVO.Item();
        vo.setId(item.getId());
        vo.setItemOrder(item.getItemOrder());
        vo.setItemName(item.getItemName());
        vo.setMethods(item.getMethods());
        vo.setUnit(item.getUnit());
        vo.setStdValue(item.getStdValue());
        vo.setJudgeType(item.getJudgeType());
        vo.setIsReference(item.getIsReference());
        vo.setAssignStatus(item.getAssignStatus());
        vo.setAssignType(item.getAssignType());
        vo.setTesterNo(item.getTesterNo());
        vo.setTesterName(item.getTesterName());
        vo.setAssignedAt(item.getAssignedAt());
        return vo;
    }

    private AssignAutoResultVO.Detail toAutoDetail(SampleItem item, String reason) {
        AssignAutoResultVO.Detail d = new AssignAutoResultVO.Detail();
        d.setItemOrder(item.getItemOrder());
        d.setItemName(item.getItemName());
        d.setAssignStatus(item.getAssignStatus());
        d.setAssignType(item.getAssignType());
        d.setTesterNo(item.getTesterNo());
        d.setTesterName(item.getTesterName());
        d.setReason(reason);
        return d;
    }
}
