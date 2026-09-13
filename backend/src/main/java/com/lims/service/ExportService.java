package com.lims.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 导出域服务（T-802 省平台上报 + T-603 检验员任务）。
 *
 * <p>两个导出均为「查询行 + EasyExcel 流式写响应流」，故方法直接把结果写入 {@link HttpServletResponse}，
 * 不返回数据体（下载接口响应是二进制流，不是统一 {@code R} 结构）。</p>
 */
public interface ExportService {

    /**
     * 省平台上报导出（一行 = 样品 × 检测单项，status>=80）。
     *
     * @param taskNo 任务编号筛选；为空则导出全部已完成样品
     */
    void exportProvince(String taskNo, HttpServletResponse response);

    /** 检验员任务导出（一行 = 样品 × 已指派检测单项，status>=40；R100 导出全部） */
    void exportMyTasks(HttpServletResponse response);
}
