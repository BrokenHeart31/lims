package com.lims.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lims.common.exception.BizException;
import com.lims.dto.SuperviseTaskSaveDTO;
import com.lims.entity.SuperviseTask;
import com.lims.mapper.SuperviseTaskMapper;
import com.lims.service.SuperviseTaskService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SuperviseTaskServiceImpl extends ServiceImpl<SuperviseTaskMapper, SuperviseTask>
        implements SuperviseTaskService {

    private static final String DEFAULT_STATUS = "草稿";

    @Override
    public Page<SuperviseTask> pageQuery(long pageNum, long pageSize,
                                         String taskNo, String taskName, String status) {
        LambdaQueryWrapper<SuperviseTask> wrapper = new LambdaQueryWrapper<SuperviseTask>()
                .likeRight(StringUtils.hasText(taskNo), SuperviseTask::getTaskNo, taskNo)
                .like(StringUtils.hasText(taskName), SuperviseTask::getTaskName, taskName)
                .eq(StringUtils.hasText(status), SuperviseTask::getStatus, status)
                .orderByDesc(SuperviseTask::getId);
        return page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public SuperviseTask createTask(SuperviseTaskSaveDTO dto) {
        assertTaskNoUnique(dto.getTaskNo(), null);

        SuperviseTask task = new SuperviseTask();
        BeanUtils.copyProperties(dto, task);
        task.setId(null);
        if (!StringUtils.hasText(task.getStatus())) {
            task.setStatus(DEFAULT_STATUS);
        }
        save(task);
        return task;
    }

    @Override
    public void updateTask(SuperviseTaskSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(400, "更新时 id 不能为空");
        }
        SuperviseTask existing = getById(dto.getId());
        if (existing == null) {
            throw new BizException(400, "任务不存在或已删除: id=" + dto.getId());
        }
        assertTaskNoUnique(dto.getTaskNo(), dto.getId());

        SuperviseTask task = new SuperviseTask();
        BeanUtils.copyProperties(dto, task);
        if (!StringUtils.hasText(task.getStatus())) {
            task.setStatus(existing.getStatus());
        }
        updateById(task);
    }

    /** task_no 全局唯一（excludeId 用于更新时排除自身） */
    private void assertTaskNoUnique(String taskNo, Long excludeId) {
        long count = count(new LambdaQueryWrapper<SuperviseTask>()
                .eq(SuperviseTask::getTaskNo, taskNo)
                .ne(excludeId != null, SuperviseTask::getId, excludeId));
        if (count > 0) {
            throw new BizException(400, "任务编号已存在: " + taskNo);
        }
    }
}
