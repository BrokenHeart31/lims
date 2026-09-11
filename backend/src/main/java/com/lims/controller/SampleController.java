package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.common.exception.BizException;
import com.lims.dto.SampleConfirmDTO;
import com.lims.dto.SampleUpdateDTO;
import com.lims.entity.Sample;
import com.lims.service.SampleService;
import com.lims.vo.SampleImportResultVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 样品登记接口（api-spec 样品域 /api/sample/*，T-301）。
 *
 * <p>权限标识与 seed/AGENTS 8.2 一致：sample:import / sample:confirm / sample:query，
 * 与前端 v-permission 共用（前端只显隐，安全由本层兜底）。</p>
 */
@Validated
@RestController
@RequestMapping("/sample")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    /** 采样单 Excel 导入（落库 S10「已登记」） */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('sample:import')")
    public R<SampleImportResultVO> importExcel(@RequestParam("file") MultipartFile file) {
        return R.ok(sampleService.importSamples(file));
    }

    /** 分页查询样品 */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('sample:query')")
    public R<PageResult<Sample>> page(
            @RequestParam @Min(1) long pageNum,
            @RequestParam @Min(1) @Max(500) long pageSize,
            @RequestParam(required = false) String sampleNo,
            @RequestParam(required = false) String sampleName,
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) Integer status) {
        return R.ok(PageResult.of(
                sampleService.pageQuery(pageNum, pageSize, sampleNo, sampleName, taskNo, status)));
    }

    /** 详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sample:query')")
    public R<Sample> detail(@PathVariable Long id) {
        Sample sample = sampleService.getById(id);
        if (sample == null) {
            throw new BizException(400, "样品不存在或已删除: id=" + id);
        }
        return R.ok(sample);
    }

    /** 登记信息维护（仅「已登记」S10 可改） */
    @PutMapping
    @PreAuthorize("hasAuthority('sample:import')")
    public R<Void> update(@Valid @RequestBody SampleUpdateDTO dto) {
        sampleService.updateSample(dto);
        return R.ok();
    }

    /** 登记确认：S10 → S20（批量） */
    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('sample:confirm')")
    public R<Map<String, Integer>> confirm(@Valid @RequestBody SampleConfirmDTO dto) {
        int confirmed = sampleService.confirmSamples(dto.getIds());
        return R.ok(Map.of("confirmedCount", confirmed));
    }
}
