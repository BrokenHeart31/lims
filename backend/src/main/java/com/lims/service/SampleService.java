package com.lims.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lims.dto.SampleUpdateDTO;
import com.lims.entity.Sample;
import com.lims.vo.SampleImportResultVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 样品登记服务（T-301）。
 */
public interface SampleService extends IService<Sample> {

    /**
     * 采样单 Excel 导入（落库状态 S10）。
     *
     * @param file 采样单 .xls/.xlsx
     * @return 成功/失败统计 + 错误行明细（部分失败不回滚）
     */
    SampleImportResultVO importSamples(MultipartFile file);

    /**
     * 分页查询样品。
     *
     * @param status 状态 code（可空）
     */
    Page<Sample> pageQuery(long pageNum, long pageSize,
                           String sampleNo, String sampleName, String taskNo, Integer status);

    /**
     * 登记信息维护（仅 S10 可改）。
     */
    void updateSample(SampleUpdateDTO dto);

    /**
     * 登记确认：S10 → S20（批量），白名单校验 + 乐观条件更新。
     *
     * @return 实际确认条数
     */
    int confirmSamples(List<Long> ids);
}
