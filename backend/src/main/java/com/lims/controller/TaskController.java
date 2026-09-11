package com.lims.controller;

import com.lims.common.PageResult;
import com.lims.common.R;
import com.lims.common.exception.BizException;
import com.lims.dto.SuperviseTaskSaveDTO;
import com.lims.entity.SuperviseTask;
import com.lims.service.SuperviseTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监抽任务接口（api-spec 任务域 /api/task/*，T-201）。
 * 按钮级权限 @PreAuthorize 与前端 v-permission 共用同一组标识（AGENTS 8.2 task:*）。
 */
@Validated
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class TaskController {

    private final SuperviseTaskService superviseTaskService;

    /** 分页查询 */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('task:list')")
    public R<PageResult<SuperviseTask>> page(
            @RequestParam @Min(1) long pageNum,
            @RequestParam @Min(1) @Max(500) long pageSize,
            @RequestParam(required = false) String taskNo,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String status) {
        return R.ok(PageResult.of(
                superviseTaskService.pageQuery(pageNum, pageSize, taskNo, taskName, status)));
    }

    /** 详情 */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('task:list')")
    public R<SuperviseTask> detail(@PathVariable Long id) {
        SuperviseTask task = superviseTaskService.getById(id);
        if (task == null) {
            throw new BizException(400, "任务不存在或已删除: id=" + id);
        }
        return R.ok(task);
    }

    /** 新建 */
    @PostMapping
    @PreAuthorize("hasAuthority('task:add')")
    public R<SuperviseTask> create(@Valid @RequestBody SuperviseTaskSaveDTO dto) {
        return R.ok(superviseTaskService.createTask(dto));
    }

    /** 更新 */
    @PutMapping
    @PreAuthorize("hasAuthority('task:edit')")
    public R<Void> update(@Valid @RequestBody SuperviseTaskSaveDTO dto) {
        superviseTaskService.updateTask(dto);
        return R.ok();
    }

    /** 删除（逻辑删除） */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('task:remove')")
    public R<Void> delete(@PathVariable Long id) {
        if (!superviseTaskService.removeById(id)) {
            throw new BizException(400, "任务不存在或已删除: id=" + id);
        }
        return R.ok();
    }
}
