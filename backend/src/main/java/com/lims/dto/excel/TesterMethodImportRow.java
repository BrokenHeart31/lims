package com.lims.dto.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 方法-检验员资质导入行（T-105，EasyExcel 读模型）。
 *
 * <p>列位置按索引绑定（{@code index}）而非表头名：业务方常直接改模板表头文字，
 * 按名绑定会因「方法标准号」写成「标准号」而整列读空——按索引绑定更抗改。</p>
 */
@Data
public class TesterMethodImportRow implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** A 列：检验方法名称 */
    @ExcelProperty(index = 0)
    private String methodName;

    /** B 列：方法标准号（如 GB 5009.268-2016） */
    @ExcelProperty(index = 1)
    private String methodNo;

    /** C 列：检验员工号（sys_user.username） */
    @ExcelProperty(index = 2)
    private String testerNo;

    /** D 列：资质状态（有效/失效，或 1/0；留空默认有效） */
    @ExcelProperty(index = 3)
    private String qualStatus;

    /** E 列：备注 */
    @ExcelProperty(index = 4)
    private String remark;
}
