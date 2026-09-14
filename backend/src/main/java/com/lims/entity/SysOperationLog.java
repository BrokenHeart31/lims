package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统操作日志（`sys_operation_log`）。
 *
 * <p>写入方是 {@code config/OperationLogInterceptor}——处于 MVC 层，
 * <b>不侵入任何业务 Service</b>；因此这张表记录的是「接口级动作」：
 * 谁、何时、调用了哪个写接口、成功与否、耗时多久。</p>
 *
 * <p><b>只追加</b>：业务代码不提供修改/删除入口。审计流水的价值在于不可篡改。</p>
 *
 * <p>时间使用基类的 {@code createdAt}（由 {@code AuditMetaObjectHandler} 填充），
 * 不再单开 `operation_at` 列——同一张表里两个「发生时间」必然漂移。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_operation_log")
public class SysOperationLog extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务模块（由请求路径前缀推导，如「结果录入」） */
    private String module;

    /** HTTP 方法：POST / PUT / DELETE */
    private String httpMethod;

    /** 请求路径（相对 context-path /api，不含查询串） */
    private String uri;

    /** 人类可读摘要：「模块 · 动作 路径」 */
    private String summary;

    /** 操作人工号 */
    private String operator;

    /** 操作人姓名（冗余存储，列表页免联表） */
    private String operatorName;

    /** 客户端 IP */
    private String ip;

    /** 结果：1=成功 0=失败 */
    private Integer result;

    /** HTTP 状态码 */
    private Integer statusCode;

    /** 处理耗时（毫秒） */
    private Long durationMs;
}
