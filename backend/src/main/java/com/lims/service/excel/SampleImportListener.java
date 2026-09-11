package com.lims.service.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.enums.SampleStatus;
import com.lims.dto.SampleImportDTO;
import com.lims.entity.Sample;
import com.lims.entity.SuperviseTask;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SuperviseTaskMapper;
import com.lims.vo.SampleImportResultVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 采样单 Excel 导入监听器（T-301，EasyExcel SAX 流式）。
 *
 * <p>要点（依 .agents/skills/excel-import/SKILL.md）：
 * <ul>
 *   <li>批大小 {@value #BATCH_COUNT}，满批即刷盘后清空，避免全量进内存；</li>
 *   <li>行号 = {@code readRowHolder().getRowIndex() + 1}（数据自第 3 行起）；</li>
 *   <li>逐行业务校验，失败收集「行号 + 原因」，<b>不中断、不整批回滚</b>；</li>
 *   <li>落库状态统一 S10（{@link SampleStatus}），禁魔法数字；</li>
 *   <li>监听器每文件一实例，<b>不做 Spring 单例</b>，Mapper 由构造器注入。</li>
 * </ul>
 * </p>
 */
public class SampleImportListener extends AnalysisEventListener<SampleImportDTO> {

    /** 批处理条数（与 skill 约定一致） */
    public static final int BATCH_COUNT = 1000;

    /** 采样单末行终止标记（说明书「最后一行A列：以下空白」） */
    private static final String TERMINATOR = "以下空白";

    private final SampleMapper sampleMapper;
    private final SuperviseTaskMapper superviseTaskMapper;

    /** 导入结果（成功/失败/错误明细） */
    private final SampleImportResultVO result = new SampleImportResultVO();

    /** 待入库缓存（携带 Excel 行号，便于失败定位） */
    private final List<PendingRow> cachedList = new ArrayList<>(BATCH_COUNT);

    /** 文件内样品编号去重（跨批次保留） */
    private final Set<String> seenSampleNos = new HashSet<>();

    public SampleImportListener(SampleMapper sampleMapper, SuperviseTaskMapper superviseTaskMapper) {
        this.sampleMapper = sampleMapper;
        this.superviseTaskMapper = superviseTaskMapper;
    }

    @Override
    public void invoke(SampleImportDTO dto, AnalysisContext context) {
        int rowNum = context.readRowHolder().getRowIndex() + 1;
        String sampleNo = trim(dto.getSampleNo());

        // 空白行 / 「以下空白」终止行 → 静默忽略，不计入统计
        if (isFullyBlank(dto) || TERMINATOR.equals(sampleNo)) {
            return;
        }
        cachedList.add(new PendingRow(rowNum, dto));
        if (cachedList.size() >= BATCH_COUNT) {
            flush();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
    }

    /**
     * 刷盘：批量查询去重/关联数据 → 逐行校验 → 合格行入库（S10）。
     */
    private void flush() {
        if (cachedList.isEmpty()) {
            return;
        }
        Set<String> fileBatchNos = new HashSet<>();
        Set<String> taskNos = new HashSet<>();
        for (PendingRow row : cachedList) {
            String no = trim(row.dto.getSampleNo());
            if (!no.isEmpty()) {
                fileBatchNos.add(no);
            }
            String taskNo = trim(row.dto.getTaskNo());
            if (!taskNo.isEmpty()) {
                taskNos.add(taskNo);
            }
        }

        // 已存在样品编号（DB 查重，一次 IN 查询）
        Set<String> existingNos = fileBatchNos.isEmpty() ? Set.of()
                : sampleMapper.selectList(new LambdaQueryWrapper<Sample>()
                        .select(Sample::getSampleNo)
                        .in(Sample::getSampleNo, fileBatchNos))
                .stream().map(Sample::getSampleNo).collect(Collectors.toSet());

        // 有效任务编号（task_no 必须存在于 supervise_task）
        Set<String> validTaskNos = taskNos.isEmpty() ? Set.of()
                : superviseTaskMapper.selectList(new LambdaQueryWrapper<SuperviseTask>()
                        .select(SuperviseTask::getTaskNo)
                        .in(SuperviseTask::getTaskNo, taskNos))
                .stream().map(SuperviseTask::getTaskNo).collect(Collectors.toSet());

        for (PendingRow row : cachedList) {
            SampleImportDTO dto = row.dto;
            List<String> errors = new ArrayList<>();
            String sampleNo = trim(dto.getSampleNo());

            validateRequired(sampleNo, "样品编号", 50, errors);
            validateRequired(trim(dto.getSampleName()), "样品名称", 255, errors);
            validateLength(trim(dto.getClientName()), 255, "受检单位", errors);
            validateLength(trim(dto.getSamplingAddress()), 255, "抽样地址", errors);
            validateLength(trim(dto.getPayee()), 50, "收款人", errors);
            validateLength(trim(dto.getSampleQuantity()), 50, "样品数量", errors);
            validateLength(trim(dto.getProjectName()), 100, "项目名称", errors);
            validateLength(trim(dto.getRemark()), 500, "备注", errors);
            validateLength(trim(dto.getSampler()), 50, "采样者", errors);
            validateLength(trim(dto.getManufacturer()), 255, "生产单位", errors);
            validateLength(trim(dto.getSamplingBase()), 50, "抽样基数", errors);
            validateLength(trim(dto.getSampleState()), 50, "样品状态", errors);
            validateLength(trim(dto.getSpec()), 100, "规格型号", errors);
            validateLength(trim(dto.getBrand()), 100, "商标", errors);
            validateLength(trim(dto.getGrade()), 50, "样品等级", errors);
            validateLength(trim(dto.getOriginalNo()), 100, "原编号或生产日期", errors);
            validateLength(trim(dto.getInspectType()), 50, "检验类别", errors);
            validateLength(trim(dto.getTaskBatchNo()), 50, "任务批号", errors);

            // 样品编号查重：文件内 + 数据库内
            if (!sampleNo.isEmpty()) {
                if (seenSampleNos.contains(sampleNo)) {
                    errors.add("样品编号在本文件内重复: " + sampleNo);
                } else if (existingNos.contains(sampleNo)) {
                    errors.add("样品编号已存在: " + sampleNo);
                }
            }

            // 任务编号必填且必须存在
            String taskNo = trim(dto.getTaskNo());
            if (taskNo.isEmpty()) {
                errors.add("任务编号不能为空");
            } else if (taskNo.length() > 50) {
                errors.add("任务编号长度不能超过 50");
            } else if (!validTaskNos.contains(taskNo)) {
                errors.add("任务编号不存在: " + taskNo);
            }

            // 日期 / 费用解析
            LocalDate samplingDate = parseDate(trim(dto.getSamplingDate()), "日期", errors);
            LocalDate requireCompleteDate = parseDate(trim(dto.getRequireCompleteDate()), "要求完成日期", errors);
            BigDecimal fee = parseFee(trim(dto.getFee()), errors);

            if (!errors.isEmpty()) {
                result.addFailure(row.rowNum, sampleNo.isEmpty() ? null : sampleNo, String.join("；", errors));
                continue;
            }

            Sample entity = toEntity(dto, samplingDate, requireCompleteDate, fee);
            sampleMapper.insert(entity);
            seenSampleNos.add(sampleNo);
            result.addSuccess();
        }
        cachedList.clear();
    }

    /** DTO → Entity（状态固定 S10：采样单导入成功即「已登记」） */
    private Sample toEntity(SampleImportDTO dto, LocalDate samplingDate, LocalDate requireCompleteDate, BigDecimal fee) {
        Sample s = new Sample();
        s.setSampleNo(trim(dto.getSampleNo()));
        s.setSampleName(trim(dto.getSampleName()));
        s.setClientName(trim(dto.getClientName()));
        s.setSamplingAddress(trim(dto.getSamplingAddress()));
        s.setPayee(trim(dto.getPayee()));
        s.setFee(fee);
        s.setSampleQuantity(trim(dto.getSampleQuantity()));
        s.setProjectName(trim(dto.getProjectName()));
        s.setSamplingDate(samplingDate);
        s.setRemark(trim(dto.getRemark()));
        s.setSampler(trim(dto.getSampler()));
        s.setManufacturer(trim(dto.getManufacturer()));
        s.setSamplingBase(trim(dto.getSamplingBase()));
        s.setSampleState(trim(dto.getSampleState()));
        s.setSpec(trim(dto.getSpec()));
        s.setBrand(trim(dto.getBrand()));
        s.setGrade(trim(dto.getGrade()));
        s.setOriginalNo(trim(dto.getOriginalNo()));
        s.setInspectType(trim(dto.getInspectType()));
        s.setRequireCompleteDate(requireCompleteDate);
        s.setTaskNo(trim(dto.getTaskNo()));
        s.setTaskBatchNo(trim(dto.getTaskBatchNo()));
        s.setStatus(SampleStatus.S10);
        return s;
    }

    // ------------------------------------------------------------------ 校验工具

    private static void validateRequired(String value, String field, int max, List<String> errors) {
        if (value == null || value.isEmpty()) {
            errors.add(field + "不能为空");
        } else if (value.length() > max) {
            errors.add(field + "长度不能超过 " + max);
        }
    }

    private static void validateLength(String value, int max, String field, List<String> errors) {
        if (value != null && value.length() > max) {
            errors.add(field + "长度不能超过 " + max);
        }
    }

    /** 宽松日期解析：支持 yyyy.M.d / yyyy-M-d / yyyy/M/d / yyyy年M月d日，空白返回 null */
    private static LocalDate parseDate(String raw, String field, List<String> errors) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String normalized = raw.split("\\s+")[0]
                .replace('年', '.')
                .replace('月', '.')
                .replace("日", "")
                .replace('/', '.')
                .replace('-', '.');
        try {
            return LocalDate.parse(normalized, DateTimeFormatter.ofPattern("yyyy.M.d"));
        } catch (Exception e) {
            errors.add(field + "格式不正确: " + raw + "（应为 yyyy.M.d / yyyy-MM-dd）");
            return null;
        }
    }

    /** 宽松金额解析：去掉千分位与「元」，空白返回 null */
    private static BigDecimal parseFee(String raw, List<String> errors) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String cleaned = raw.replace(",", "").replace("元", "").trim();
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            errors.add("费用格式不正确: " + raw);
            return null;
        }
    }

    private static boolean isFullyBlank(SampleImportDTO dto) {
        return isBlank(dto.getSampleNo()) && isBlank(dto.getSampleName()) && isBlank(dto.getClientName())
                && isBlank(dto.getSamplingAddress()) && isBlank(dto.getPayee()) && isBlank(dto.getFee())
                && isBlank(dto.getSampleQuantity()) && isBlank(dto.getProjectName()) && isBlank(dto.getSamplingDate())
                && isBlank(dto.getRemark()) && isBlank(dto.getSampler()) && isBlank(dto.getManufacturer())
                && isBlank(dto.getSamplingBase()) && isBlank(dto.getSampleState()) && isBlank(dto.getSpec())
                && isBlank(dto.getBrand()) && isBlank(dto.getGrade()) && isBlank(dto.getOriginalNo())
                && isBlank(dto.getInspectType()) && isBlank(dto.getRequireCompleteDate()) && isBlank(dto.getTaskNo())
                && isBlank(dto.getTaskBatchNo());
    }

    private static boolean isBlank(String v) {
        return v == null || v.trim().isEmpty();
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    public SampleImportResultVO getResult() {
        return result;
    }

    /** 待入库行（Excel 行号 + 原始 DTO） */
    private record PendingRow(int rowNum, SampleImportDTO dto) {
    }
}
