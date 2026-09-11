package com.lims.common.enums;

import com.lims.common.exception.BizException;
import lombok.Getter;

/**
 * 检测单项指派方式（sample_item.assign_type，T-501）。
 *
 * <p>流水线语义：分类规则（编号含 NA/XA/SA）→ 方法资质 → 人工改派。
 * 未命中前两条规则时**保持 NONE**（待人工指派），禁止默认指派任意检验员（fail-loud）。</p>
 */
@Getter
public enum AssignType {

    /** 未指派 */
    NONE(0, "未指派"),
    /** 分类规则：样品编号含 NA/XA/SA → 对应共享检验员 */
    CATEGORY(1, "分类规则"),
    /** 方法资质：按 sample_item.methods 匹配 tester_method */
    METHOD(2, "方法资质"),
    /** 人工改派 */
    MANUAL(3, "人工改派");

    private final int code;
    private final String label;

    AssignType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public static AssignType of(Integer code) {
        if (code != null) {
            for (AssignType t : values()) {
                if (t.code == code) {
                    return t;
                }
            }
        }
        throw new BizException(400, "未知的指派方式: " + code);
    }
}
