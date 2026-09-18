package com.lims.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.common.enums.SampleStatus;
import com.lims.entity.Sample;
import com.lims.mapper.SampleMapper;
import com.lims.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 业务只读上下文读取器（feature A，T03，设计 §9「AI 域硬约束」）。
 *
 * <p><b>为什么单独抽一层</b>：AI 是**旁路能力**，绝不写业务。把所有业务读操作收敛到本类，
 * 使「AI 依赖的 Mapper 集合」可被单测穷举断言——{@code AiAssistantServiceImpl} 及其它 AI 类
 * **不得**直接依赖 {@code SampleItemMapper}/{@code SampleResultMapper} 等判定相关 Mapper，
 * 也不得引 {@code JudgeEngine}（T3 红线：AI 绝不写判定结论字段）。</p>
 *
 * <p><b>无任何写方法</b>：本类只做 {@code selectXxx}（只读），不出现 save/update/insert/delete。</p>
 */
@Component
@RequiredArgsConstructor
public class BusinessContextReader {

    /** 样品状态只读视图。 */
    public record SampleStatusView(String sampleNo, Integer status, String statusLabel) {
    }

    /** 唯一的业务 Mapper 依赖（只读）。 */
    private final SampleMapper sampleMapper;

    /** 按样品编号读取当前状态（只读）。 */
    public Optional<SampleStatusView> readStatus(String sampleNo) {
        if (!StringUtils.hasText(sampleNo)) {
            return Optional.empty();
        }
        Sample sample = sampleMapper.selectOne(new LambdaQueryWrapper<Sample>()
                .eq(Sample::getSampleNo, sampleNo.trim())
                .last("LIMIT 1"));
        if (sample == null) {
            return Optional.empty();
        }
        SampleStatus status = sample.getStatus();
        return Optional.of(new SampleStatusView(
                sample.getSampleNo(),
                status == null ? null : status.getCode(),
                status == null ? null : status.getLabel()));
    }

    /**
     * 当前登录用户的权限标识清单（只读）。
     *
     * <p>取自认证上下文里的 authorities（登录时由 {@code UserDetailsServiceImpl} 装配），
     * **不再查库**——既省一次 IO，也避免 AI 包引入 sys_* Mapper。</p>
     */
    public List<String> readRolePermissions() {
        return SecurityUtils.getLoginUser()
                .map(lu -> lu.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .sorted()
                        .toList())
                .orElse(List.of());
    }
}
