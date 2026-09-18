package com.lims.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 扫描件 OCR 重试视图（feature 增量 ai_flow_assistant，T03，设计 §4.5 / A19）。
 *
 * <p>后端**只复位**失败页为待跑（删片段标记），并**返回待用户执行的命令**——**不代跑** OCR
 * （长任务、离线，见设计 §2.4 末条）。</p>
 */
@Data
public class ScanOcrRetryVO {

    /** 归一化标准号键 */
    private String stdKey;

    /** 已复位的待跑页码清单 */
    private List<Integer> pendingPages = new ArrayList<>();

    /** 待用户在图终端执行的命令（系统不代替运行） */
    private String commandToRun;

    /** 说明文案 */
    private String note;
}
