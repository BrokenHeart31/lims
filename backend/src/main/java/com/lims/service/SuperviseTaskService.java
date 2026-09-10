package com.lims.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lims.dto.SuperviseTaskSaveDTO;
import com.lims.entity.SuperviseTask;

/**
 * 监抽任务（T-201，api-spec 任务域）
 */
public interface SuperviseTaskService extends IService<SuperviseTask> {

    /** 分页查询：taskNo 精确前缀 / taskName 模糊 / status 精确，按 id 倒序 */
    Page<SuperviseTask> pageQuery(long pageNum, long pageSize, String taskNo, String taskName, String status);

    /** 新建：task_no 唯一校验，默认状态 草稿 */
    SuperviseTask createTask(SuperviseTaskSaveDTO dto);

    /** 更新：存在性 + task_no 唯一（排除自身）校验 */
    void updateTask(SuperviseTaskSaveDTO dto);
}
