package com.lims.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 采样单导入结果（T-301，api-spec 样品域）。
 *
 * <p>部分失败不回滚：合法行入库（S10），错误行逐条报「行号 + 原因」，供登记员修正后重导。</p>
 */
@Data
public class SampleImportResultVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 解析到的数据行数（成功 + 失败） */
    private int total;

    /** 成功入库行数 */
    private int successCount;

    /** 失败行数 */
    private int failCount;

    /** 失败明细（行号 = Excel 实际行号，从 3 起） */
    private List<ErrorRow> errors = new ArrayList<>();

    /**
     * 记一条成功。
     */
    public void addSuccess() {
        this.total++;
        this.successCount++;
    }

    /**
     * 记一条成功（批量，按条数累加）。
     */
    public void addSuccess(int count) {
        this.total += count;
        this.successCount += count;
    }

    /**
     * 记一条失败。
     *
     * @param rowNum    Excel 行号（1 基）
     * @param sampleNo  样品编号（可为空）
     * @param message   失败原因
     */
    public void addFailure(int rowNum, String sampleNo, String message) {
        this.total++;
        this.failCount++;
        this.errors.add(new ErrorRow(rowNum, sampleNo, message));
    }

    /** 失败行明细 */
    @Data
    public static class ErrorRow implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** Excel 行号（1 基，含表头） */
        private final int rowNum;

        /** 样品编号（行内可解析到时回填，便于定位） */
        private final String sampleNo;

        /** 失败原因 */
        private final String message;

        public ErrorRow(int rowNum, String sampleNo, String message) {
            this.rowNum = rowNum;
            this.sampleNo = sampleNo;
            this.message = message;
        }
    }
}
