package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 检验员分类资质（旧表 `user_method`）—— T-501「分类规则」的**数据源**。
 *
 * <p>// [LEGACY] 旧表过渡，禁止新代码依赖：本表来自旧系统导出，**无审计四字段、无逻辑删除**，
 * 字段名为 `usergh`（工号）/`method`（分类代码）。新代码只**读取**本表，禁止在此基础上写入扩展。</p>
 *
 * <p>语义：一行 = 某工号具备某分类（`NA`=农残 / `XA`=畜残 / `SA`=水产）的检验资质，
 * 对应 AGENTS 7.4 第一条规则「样品编号含 NA/XA/SA → 对应共享检验员」。</p>
 *
 * <p>退出路径：基础数据域（`/api/base/tester-method`）落地后，方法级资质由新表
 * {@link TesterMethod} 承载；本表仅保留为分类规则的兜底映射（缺行时回退到 AGENTS 7.4 约定工号）。</p>
 */
@Data
@TableName("user_method")
public class UserMethod {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /** 工号（对应 sys_user.username，如 njna000） */
    private String usergh;

    /** 分类代码：NA=农残 / XA=畜残 / SA=水产 */
    private String method;
}
