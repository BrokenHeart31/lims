package com.lims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 采样单导入批次（sample_import_batch，T-301）。
 *
 * <p>业务来源：说明书「A1 列自定义 Excel 文件标记，防止重复导入」。
 * 导入成功后登记 A1 标记（唯一），再次导入同一文件整文件拒绝。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sample_import_batch")
public class SampleImportBatch extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 采样单 A1 单元格文件标记（唯一） */
    private String fileMarker;

    /** 上传文件名（仅追溯用） */
    private String fileName;

    /** 本批成功导入行数 */
    private Integer successCount;

    /** 本批失败行数 */
    private Integer failCount;
}
