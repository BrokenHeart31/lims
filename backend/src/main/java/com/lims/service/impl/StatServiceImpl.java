package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.enums.SampleStatus;
import com.lims.common.exception.BizException;
import com.lims.entity.SysUser;
import com.lims.mapper.StatMapper;
import com.lims.mapper.SysUserMapper;
import com.lims.service.StatService;
import com.lims.vo.StatNameValueVO;
import com.lims.vo.StatOverviewVO;
import com.lims.vo.StatTrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 质量分析统计实现（T-803）。
 *
 * <p><b>三条实现纪律</b>：
 * <ol>
 *   <li><b>无 mock</b>：空数据就是 0 / 空列表，不编造演示值（DECISIONS 2026-09-13）；</li>
 *   <li><b>中文标签在 Java 侧翻译</b>：状态、检验员姓名都不在 SQL 里拼中文——
 *       SQL 硬编码中文会在枚举改名时静默不一致；</li>
 *   <li><b>补零月</b>：月度趋势必须补齐无数据月份，否则折线图会把 1 月与 3 月直接连起来，
 *       视觉上抹掉「2 月没做检测」这一事实。</li>
 * </ol></p>
 */
@Service
@RequiredArgsConstructor
public class StatServiceImpl implements StatService {

    /** 趋势图允许的月数范围（防止前端传 10000 造成全表扫描） */
    private static final int MIN_TREND_MONTHS = 1;
    private static final int MAX_TREND_MONTHS = 24;

    /** Top N 查询的默认上限 */
    private static final int DEFAULT_TOP_LIMIT = 10;
    private static final int MAX_TOP_LIMIT = 50;

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final StatMapper statMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    public StatOverviewVO overview() {
        StatOverviewVO vo = statMapper.selectOverview();
        if (vo == null) {
            // 表为空时 SUM 全为 null 映射成 0，但保险起见兜底一个零对象
            vo = new StatOverviewVO();
        }
        // 合格率分母刻意排除「待判定」：待判定是数据缺口而非质量结论（见 VO 注释）
        long denominator = vo.getQualifiedSamples() + vo.getUnqualifiedSamples();
        if (denominator > 0) {
            vo.setQualifiedRate(BigDecimal.valueOf(vo.getQualifiedSamples())
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                    .doubleValue());
        } else {
            // 无有效结论：返回 null（前端显示「暂无数据」），与「0%」语义不同
            vo.setQualifiedRate(null);
        }
        return vo;
    }

    @Override
    public List<StatNameValueVO> sampleStatusDistribution() {
        List<StatNameValueVO> raw = statMapper.countByStatus();
        long total = raw.stream().mapToLong(StatNameValueVO::getValue).sum();
        List<StatNameValueVO> result = new ArrayList<>(raw.size());
        for (StatNameValueVO row : raw) {
            // SQL 取回的是 status code 字符串，此处翻成中文阶段名
            Integer code = parseCode(row.getName());
            // ofNullable 直接返回枚举（可为 null），不是 Optional，故此处显式判空
            SampleStatus status = SampleStatus.ofNullable(code);
            // fail-loud：未知状态码不静默丢弃，保留原始值并标注，便于排查状态机越界写入
            String label = status != null ? status.getLabel() : "未知(" + row.getName() + ")";
            result.add(new StatNameValueVO(label, row.getValue(), percent(row.getValue(), total)));
        }
        return result;
    }

    @Override
    public List<StatNameValueVO> inspectTypeDistribution() {
        return withPercent(statMapper.countByInspectType());
    }

    @Override
    public List<StatNameValueVO> topClients(int limit) {
        return statMapper.countByClient(normalizeLimit(limit));
    }

    @Override
    public List<StatNameValueVO> categoryDistribution(int limit) {
        return withPercent(statMapper.countByCategory(normalizeLimit(limit)));
    }

    /**
     * 检验员任务量：把工号翻成「姓名（工号）」。
     *
     * <p>未指派的聚合行保持原样（那是业务流程缺口，不该伪装成人名）。
     * 工号在 sys_user 查不到的（如已停用/删除账号）保留工号原文并标注，
     * 而不是丢掉——数据缺口必须在图上可见（fail-loud 原则）。</p>
     */
    @Override
    public List<StatNameValueVO> testerWorkload(int limit) {
        List<StatNameValueVO> raw = statMapper.countByTester(normalizeLimit(limit));
        Set<String> testerNos = raw.stream()
                .map(StatNameValueVO::getName)
                .filter(this::isRealTesterNo)
                .collect(Collectors.toSet());
        Map<String, String> nameByNo = resolveNicknames(testerNos);
        long total = raw.stream().mapToLong(StatNameValueVO::getValue).sum();

        List<StatNameValueVO> result = new ArrayList<>(raw.size());
        for (StatNameValueVO row : raw) {
            String name = row.getName();
            if (!isRealTesterNo(name)) {
                result.add(new StatNameValueVO(name, row.getValue(), percent(row.getValue(), total)));
                continue;
            }
            String nickname = nameByNo.get(name);
            String label = StringUtils.hasText(nickname) ? nickname + "（" + name + "）" : name + "（账号已不存在）";
            result.add(new StatNameValueVO(label, row.getValue(), percent(row.getValue(), total)));
        }
        return result;
    }

    @Override
    public List<StatNameValueVO> deptDistribution() {
        return withPercent(statMapper.countByDept());
    }

    @Override
    public List<StatNameValueVO> topUnqualifiedItems(int limit) {
        return statMapper.countUnqualifiedItems(normalizeLimit(limit));
    }

    /**
     * 月度趋势：先用查询结果建索引，再按「连续月份序列」逐月取（缺则补 0）。
     *
     * <p>序列从 {@code 本月 - (months-1)} 到本月，共 {@code months} 个刻度——
     * 保证前端图表 x 轴长度固定，读者能直观看到「某月是 0」。</p>
     */
    @Override
    public List<StatTrendVO> monthlyTrend(int months) {
        int span = normalizeMonths(months);
        Map<String, StatTrendVO> byMonth = new HashMap<>();
        for (StatTrendVO row : statMapper.selectMonthlyTrend(span)) {
            byMonth.put(row.getMonth(), row);
        }
        YearMonth start = YearMonth.from(LocalDate.now()).minusMonths(span - 1L);
        List<StatTrendVO> result = new ArrayList<>(span);
        for (int i = 0; i < span; i++) {
            String key = start.plusMonths(i).format(MONTH_FORMAT);
            StatTrendVO row = byMonth.get(key);
            result.add(row != null ? row : new StatTrendVO(key, 0L, 0L));
        }
        return result;
    }

    // ==================== 内部工具 ====================

    /** 是否为真实检验员工号（排除「未指派」这类聚合占位） */
    private boolean isRealTesterNo(String name) {
        return StringUtils.hasText(name) && !"未指派".equals(name);
    }

    /** 批量工号 → 姓名（一条 IN 查询，避免 N+1） */
    private Map<String, String> resolveNicknames(Set<String> usernames) {
        Map<String, String> map = new LinkedHashMap<>();
        if (usernames.isEmpty()) {
            return map;
        }
        List<SysUser> users = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getUsername, usernames));
        for (SysUser user : users) {
            map.put(user.getUsername(), user.getNickname());
        }
        return map;
    }

    /** 给每个条目补百分比（分母 = 总和；总和为 0 时 percent 为 0.0） */
    private List<StatNameValueVO> withPercent(List<StatNameValueVO> rows) {
        long total = rows.stream().mapToLong(StatNameValueVO::getValue).sum();
        List<StatNameValueVO> result = new ArrayList<>(rows.size());
        for (StatNameValueVO row : rows) {
            result.add(new StatNameValueVO(row.getName(), row.getValue(), percent(row.getValue(), total)));
        }
        return result;
    }

    private Double percent(long value, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(value)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private Integer parseCode(String raw) {
        try {
            return raw == null ? null : Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_TOP_LIMIT;
        }
        return Math.min(limit, MAX_TOP_LIMIT);
    }

    private int normalizeMonths(int months) {
        if (months < MIN_TREND_MONTHS || months > MAX_TREND_MONTHS) {
            throw new BizException(400, "趋势月数需在 " + MIN_TREND_MONTHS + "~" + MAX_TREND_MONTHS + " 之间");
        }
        return months;
    }
}
