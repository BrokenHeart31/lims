package com.lims.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.exception.ExcelAnalysisException;
import com.alibaba.excel.exception.ExcelAnalysisStopException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lims.common.enums.SampleStatus;
import com.lims.common.enums.SampleStatusTransition;
import com.lims.common.exception.BizException;
import com.lims.dto.SampleImportDTO;
import com.lims.dto.SampleUpdateDTO;
import com.lims.entity.Sample;
import com.lims.entity.SampleImportBatch;
import com.lims.mapper.SampleImportBatchMapper;
import com.lims.mapper.SampleMapper;
import com.lims.mapper.SuperviseTaskMapper;
import com.lims.security.SecurityUtils;
import com.lims.service.SampleService;
import com.lims.service.excel.SampleImportListener;
import com.lims.vo.SampleImportResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 样品登记服务实现（T-301）。
 *
 * <p>状态流转唯一入口：登记确认 S10→S20 经
 * {@code SampleStatusTransition.assertTransition} 白名单校验，
 * 落库用乐观条件 UPDATE（WHERE status=旧值）防并发双击跳态。</p>
 */
@Service
@RequiredArgsConstructor
public class SampleServiceImpl extends ServiceImpl<SampleMapper, Sample> implements SampleService {

    /** 采样单数据行起始行号（说明书：数据自第 3 行开始，第 1 行文件标记、第 2 行列头） */
    private static final int HEAD_ROW_NUMBER = 2;

    private final SuperviseTaskMapper superviseTaskMapper;
    private final SampleImportBatchMapper sampleImportBatchMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SampleImportResultVO importSamples(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(400, "请选择要导入的采样单文件");
        }
        String fileName = file.getOriginalFilename();
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (!lower.endsWith(".xls") && !lower.endsWith(".xlsx")) {
                throw new BizException(400, "采样单仅支持 .xls / .xlsx 格式");
            }
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BizException(400, "读取上传文件失败，请重试");
        }

        // 防重复导入：说明书「A1 列自定义 Excel 文件标记，防止重复导入」
        String marker = readFileMarker(bytes);
        if (StringUtils.hasText(marker) && batchMarkerExists(marker)) {
            throw new BizException(400, "该采样单已导入过（文件标记：" + marker + "），禁止重复导入");
        }

        SampleImportListener listener = new SampleImportListener(baseMapper, superviseTaskMapper);
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            EasyExcel.read(in, SampleImportDTO.class, listener)
                    .headRowNumber(HEAD_ROW_NUMBER)
                    .sheet()
                    .doRead();
        } catch (IOException e) {
            throw new BizException(400, "采样单解析失败：" + e.getMessage());
        }
        SampleImportResultVO result = listener.getResult();

        if (StringUtils.hasText(marker) && result.getSuccessCount() > 0) {
            SampleImportBatch batch = new SampleImportBatch();
            batch.setFileMarker(marker);
            batch.setFileName(fileName);
            batch.setSuccessCount(result.getSuccessCount());
            batch.setFailCount(result.getFailCount());
            sampleImportBatchMapper.insert(batch);
        }
        return result;
    }

    @Override
    public Page<Sample> pageQuery(long pageNum, long pageSize,
                                  String sampleNo, String sampleName, String taskNo, Integer status) {
        SampleStatus statusEnum = SampleStatus.ofNullable(status);
        if (status != null && statusEnum == null) {
            throw new BizException(400, "非法的样品状态编码: " + status);
        }
        LambdaQueryWrapper<Sample> wrapper = new LambdaQueryWrapper<Sample>()
                .likeRight(StringUtils.hasText(sampleNo), Sample::getSampleNo, sampleNo)
                .like(StringUtils.hasText(sampleName), Sample::getSampleName, sampleName)
                .eq(StringUtils.hasText(taskNo), Sample::getTaskNo, taskNo)
                .eq(statusEnum != null, Sample::getStatus, statusEnum)
                .orderByDesc(Sample::getId);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSample(SampleUpdateDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(400, "更新时 id 不能为空");
        }
        Sample existing = getById(dto.getId());
        if (existing == null) {
            throw new BizException(400, "样品不存在或已删除: id=" + dto.getId());
        }
        if (existing.getStatus() != SampleStatus.S10) {
            throw new BizException(400, "样品当前为「" + existing.getStatusLabel() + "」，仅「已登记」状态可维护登记信息");
        }
        assertSampleNoUnique(dto.getSampleNo(), dto.getId());

        Sample patch = new Sample();
        BeanUtils.copyProperties(dto, patch);
        updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int confirmSamples(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BizException(400, "请至少选择一条样品进行登记确认");
        }
        String operator = SecurityUtils.getUsername().orElse("system");
        LocalDateTime now = LocalDateTime.now();
        int confirmed = 0;
        for (Long id : ids) {
            Sample sample = getById(id);
            if (sample == null) {
                throw new BizException(400, "样品不存在或已删除: id=" + id);
            }
            // 状态机白名单校验（AGENTS 7.2 / common/enums）
            SampleStatusTransition.assertTransition(sample.getStatus(), SampleStatus.S20);

            Sample patch = new Sample();
            patch.setStatus(SampleStatus.S20);
            patch.setConfirmedBy(operator);
            patch.setConfirmedAt(now);
            // 乐观条件更新：WHERE id=? AND status=旧值，防并发双击跳态
            boolean ok = update(patch, new LambdaUpdateWrapper<Sample>()
                    .eq(Sample::getId, sample.getId())
                    .eq(Sample::getStatus, sample.getStatus()));
            if (!ok) {
                throw new BizException(400, "样品状态已变更，请刷新后重试：" + sample.getSampleNo());
            }
            confirmed++;
        }
        return confirmed;
    }

    /** 读取 A1 单元格文件标记（无模型 Map 读，仅取首行首列） */
    private String readFileMarker(byte[] bytes) {
        MarkerListener listener = new MarkerListener();
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            EasyExcel.read(in)
                    .headRowNumber(0)
                    .registerReadListener(listener)
                    .sheet()
                    .doRead();
        } catch (ExcelAnalysisException e) {
            // 仅取 A1 后主动中断属正常流程；其余解析异常原样上报
            if (!(e instanceof ExcelAnalysisStopException)) {
                throw new BizException(400, "采样单读取失败：" + e.getMessage());
            }
        } catch (IOException e) {
            throw new BizException(400, "采样单读取失败：" + e.getMessage());
        }
        return listener.marker;
    }

    private boolean batchMarkerExists(String marker) {
        return sampleImportBatchMapper.selectCount(new LambdaQueryWrapper<SampleImportBatch>()
                .eq(SampleImportBatch::getFileMarker, marker)) > 0;
    }

    /** sample_no 全局唯一（excludeId 用于更新时排除自身） */
    private void assertSampleNoUnique(String sampleNo, Long excludeId) {
        long count = count(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSampleNo, sampleNo)
                .ne(excludeId != null, Sample::getId, excludeId));
        if (count > 0) {
            throw new BizException(400, "样品编号已存在: " + sampleNo);
        }
    }

    /** 文件标记监听器：读首行首列后立即中断（防重复导入用） */
    private static final class MarkerListener extends AnalysisEventListener<Map<Integer, String>> {

        private String marker;

        @Override
        public void invoke(Map<Integer, String> data, AnalysisContext context) {
            if (context.readRowHolder().getRowIndex() == 0) {
                marker = data.get(0);
            }
            throw new ExcelAnalysisStopException();
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
            // 无需处理
        }
    }
}
