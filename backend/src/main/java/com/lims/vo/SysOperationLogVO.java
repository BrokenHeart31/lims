package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lims.entity.SysOperationLog;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志行（前端「操作日志」对话框消费）。
 *
 * <p>字段是实体的直接投影，只多一个派生的 {@code resultLabel}——
 * 前端不重复做「1/0 → 成功/失败」的翻译（与项目其余 VO 的 label 派生一致）。</p>
 */
@Data
public class SysOperationLogVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 业务模块 */
    private String module;

    /** 操作摘要（模块 · 动作 路径） */
    private String summary;

    /** HTTP 方法 */
    private String httpMethod;

    /** 请求路径 */
    private String uri;

    /** 操作人工号 */
    private String operator;

    /** 操作人姓名 */
    private String operatorName;

    /** 客户端 IP */
    private String ip;

    /** 结果：1=成功 0=失败 */
    private Integer result;

    /** 结果中文（派生，不入库） */
    private String resultLabel;

    /** HTTP 状态码 */
    private Integer statusCode;

    /** 耗时（毫秒） */
    private Long durationMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    public static SysOperationLogVO from(SysOperationLog entity) {
        SysOperationLogVO vo = new SysOperationLogVO();
        vo.setId(entity.getId());
        vo.setModule(entity.getModule());
        vo.setSummary(entity.getSummary());
        vo.setHttpMethod(entity.getHttpMethod());
        vo.setUri(entity.getUri());
        vo.setOperator(entity.getOperator());
        vo.setOperatorName(entity.getOperatorName());
        vo.setIp(entity.getIp());
        vo.setResult(entity.getResult());
        vo.setResultLabel(Integer.valueOf(1).equals(entity.getResult()) ? "成功" : "失败");
        vo.setStatusCode(entity.getStatusCode());
        vo.setDurationMs(entity.getDurationMs());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }
}
