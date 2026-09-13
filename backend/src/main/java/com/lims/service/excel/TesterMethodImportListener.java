package com.lims.service.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.enums.QualStatus;
import com.lims.dto.excel.TesterMethodImportRow;
import com.lims.entity.SysUser;
import com.lims.entity.TesterMethod;
import com.lims.mapper.SysUserMapper;
import com.lims.mapper.TesterMethodMapper;
import com.lims.service.TesterMethodService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 方法-检验员资质 Excel 导入监听器（T-105，EasyExcel SAX 流式）。
 *
 * <p>依 .agents/skills/excel-import/SKILL.md 与既有 {@link SampleImportListener} 同构：
 * <ul>
 *   <li>批大小 {@value #BATCH_COUNT}，满批刷盘后清空，避免全量进内存；</li>
 *   <li>行号 = {@code readRowHolder().getRowIndex() + 1}；</li>
 *   <li>逐行校验失败**不中断、不整批回滚**，收集「行号 + 原因」；</li>
 *   <li>同一「方法标准号 + 检验员工号」走**幂等 upsert**（已存在则覆盖），
 *       使同一份表格可反复导入而不产生重复行（表有唯一键 uk_tester_method）；</li>
 *   <li>每文件一实例，不做 Spring 单例，Mapper 由构造器注入。</li>
 * </ul>
 *
 * <p><b>为什么独立成顶层类而非 ServiceImpl 的嵌套类</b>：本监听器会引用
 * {@link TesterMethodImportRow}（带 Lombok 注解的读模型）。若把它写成
 * `TesterMethodServiceImpl` 的静态嵌套类，javac 会在编译 ServiceImpl 时
 * 通过 sourcepath 解析该读模型，而**同一编译轮次中 Lombok 生成的 getter 尚不可见**，
 * 报「字段是 private 访问控制」。独立顶层类让读模型先独立编译完成，问题消失。</p>
 */
public class TesterMethodImportListener extends AnalysisEventListener<TesterMethodImportRow> {

    /** 批处理条数（与 skill 约定一致） */
    public static final int BATCH_COUNT = 1000;

    private final TesterMethodMapper testerMethodMapper;
    private final SysUserMapper sysUserMapper;

    private final List<PendingRow> cache = new ArrayList<>(BATCH_COUNT);

    /** 文件内「方法+工号」去重（跨批次保留） */
    private final Set<String> seenKeys = new HashSet<>();

    private final List<String> errors = new ArrayList<>();
    private int successCount;
    private int updateCount;
    private int failCount;

    public TesterMethodImportListener(TesterMethodMapper testerMethodMapper, SysUserMapper sysUserMapper) {
        this.testerMethodMapper = testerMethodMapper;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public void invoke(TesterMethodImportRow row, AnalysisContext context) {
        int rowNum = context.readRowHolder().getRowIndex() + 1;
        if (isFullyBlank(row)) {
            return;
        }
        cache.add(new PendingRow(rowNum, row));
        if (cache.size() >= BATCH_COUNT) {
            flush();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
    }

    private void flush() {
        if (cache.isEmpty()) {
            return;
        }
        // 批量解析有效工号（一次 IN 查询，避免逐行查库）
        Set<String> testerNos = cache.stream()
                .map(r -> trim(r.row.getTesterNo()))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        Set<String> validTesterNos = testerNos.isEmpty() ? Set.of()
                : sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .select(SysUser::getUsername)
                        .in(SysUser::getUsername, testerNos))
                .stream().map(SysUser::getUsername).collect(Collectors.toSet());

        for (PendingRow pending : cache) {
            List<String> rowErrors = new ArrayList<>();
            TesterMethodImportRow row = pending.row;
            String methodName = trim(row.getMethodName());
            String methodNo = trim(row.getMethodNo());
            String testerNo = trim(row.getTesterNo());

            validateRequired(methodName, "检验方法名称", 255, rowErrors);
            validateRequired(methodNo, "方法标准号", 100, rowErrors);
            if (testerNo.isEmpty()) {
                rowErrors.add("检验员工号不能为空");
            } else if (testerNo.length() > 32) {
                rowErrors.add("检验员工号长度不能超过 32");
            } else if (!validTesterNos.contains(testerNo)) {
                rowErrors.add("检验员工号不存在: " + testerNo);
            }
            Integer qualStatus = parseQualStatus(row.getQualStatus(), rowErrors);
            String remark = trim(row.getRemark());
            if (remark.length() > 255) {
                rowErrors.add("备注长度不能超过 255");
            }

            // 文件内重复：同一「方法+工号」出现两次，后一条覆盖前一条会让结果不可预期
            if (!methodNo.isEmpty() && !testerNo.isEmpty() && !seenKeys.add(methodNo + "|" + testerNo)) {
                rowErrors.add("同一文件内「方法标准号 + 检验员工号」重复: " + methodNo + " / " + testerNo);
            }

            if (!rowErrors.isEmpty()) {
                failCount++;
                errors.add("第 " + pending.rowNum + " 行：" + String.join("；", rowErrors));
                continue;
            }

            TesterMethod exist = testerMethodMapper.selectOne(new LambdaQueryWrapper<TesterMethod>()
                    .eq(TesterMethod::getMethodNo, methodNo)
                    .eq(TesterMethod::getTesterNo, testerNo)
                    .last("limit 1"));
            TesterMethod entity = new TesterMethod();
            entity.setMethodName(methodName);
            entity.setMethodNo(methodNo);
            entity.setTesterNo(testerNo);
            entity.setQualStatus(qualStatus);
            entity.setRemark(remark.isEmpty() ? null : remark);
            if (exist == null) {
                testerMethodMapper.insert(entity);
                successCount++;
            } else {
                entity.setId(exist.getId());
                testerMethodMapper.updateById(entity);
                updateCount++;
            }
        }
        cache.clear();
    }

    /** 结果快照（读取时机：doAfterAllAnalysed 之后） */
    public TesterMethodService.ImportResult result() {
        return new TesterMethodService.ImportResult(successCount, updateCount, failCount, errors);
    }

    // ------------------------------------------------------------------ 工具

    private static Integer parseQualStatus(String raw, List<String> rowErrors) {
        String v = trim(raw);
        if (v.isEmpty()) {
            return QualStatus.VALID.getCode();
        }
        if ("1".equals(v) || "有效".equals(v)) {
            return QualStatus.VALID.getCode();
        }
        if ("0".equals(v) || "失效".equals(v)) {
            return QualStatus.INVALID.getCode();
        }
        rowErrors.add("资质状态只能为 有效/失效 或 1/0: " + v);
        return null;
    }

    private static void validateRequired(String value, String field, int max, List<String> errors) {
        if (value.isEmpty()) {
            errors.add(field + "不能为空");
        } else if (value.length() > max) {
            errors.add(field + "长度不能超过 " + max);
        }
    }

    private static boolean isFullyBlank(TesterMethodImportRow row) {
        return trim(row.getMethodName()).isEmpty() && trim(row.getMethodNo()).isEmpty()
                && trim(row.getTesterNo()).isEmpty() && trim(row.getQualStatus()).isEmpty()
                && trim(row.getRemark()).isEmpty();
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    /** 待入库行（Excel 行号 + 原始读模型） */
    private record PendingRow(int rowNum, TesterMethodImportRow row) {
    }
}
