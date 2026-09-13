package com.lims.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户视图（api-spec 第 13 章，T-107）。
 *
 * <p><b>安全红线（AGENTS 4.2）</b>：本 VO 不存在 password / salt 字段——不是「查询时不填充」，
 * 而是字段根本不存在，从类型层面杜绝越权泄露。若后续需要「重置密码」，走独立的
 * {@code PUT /sys/user/{id}/password}，请求体单向进入、永不回吐。</p>
 */
@Data
public class SysUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /** 登录名/工号 */
    private String username;

    private String nickname;

    private Long deptId;

    /** 部门名称（反查填充，避免前端二次请求） */
    private String deptName;

    private String email;

    private String phone;

    private String signatureUrl;

    /** 1=启用 0=停用 */
    private Integer status;

    private String remark;

    /** 已绑定角色 id 集合 */
    private List<Long> roleIds;

    /** 已绑定角色名称集合（用于列表直接展示，如「综合管理」） */
    private List<String> roleNames;

    /**
     * 创建时间。
     *
     * <p>⚠️ {@code LocalDateTime} 必须显式标注 {@code @JsonFormat}：application.yml 的
     * {@code spring.jackson.date-format} 只对 {@code java.util.Date} 生效，对 JSR-310 类型无效。
     * 不标则输出 ISO-8601（{@code 2026-09-13T14:58:39}），与项目其余 16 个 VO 字段的
     * {@code yyyy-MM-dd HH:mm:ss} 格式不一致，前端不得不用两套日期解析。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
