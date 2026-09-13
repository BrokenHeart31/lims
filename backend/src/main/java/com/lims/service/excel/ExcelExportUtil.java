package com.lims.service.excel;

import com.alibaba.excel.EasyExcel;
import com.lims.common.exception.BizException;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Excel 导出公共工具（T-802 / T-603）。
 *
 * <p>把「响应头 + 文件名编码 + EasyExcel 流式写出」收敛到一处，供省平台上报与检验员任务两个导出复用，
 * 避免两处各写一套而漂移。</p>
 *
 * <p><b>为什么不落中间文件</b>：EasyExcel 直接写 {@link HttpServletResponse#getOutputStream()}，
 * 边写边发给客户端（SAX 式逐行写），不产生临时文件、不在内存里拼整份二进制。</p>
 *
 * <p><b>文件名编码</b>：中文文件名按 RFC 5987 用 {@code filename*=UTF-8''<urlencoded>} 形式给出，
 * 规避 HTTP 头非 ASCII 的兼容问题；{@code +} 会被替换为 {@code %20}，因为部分客户端把 {@code +} 当字面加号。</p>
 */
public final class ExcelExportUtil {

    /** xlsx MIME 类型 */
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 文件名时间戳格式：yyyyMMddHHmmss */
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ExcelExportUtil() {
    }

    /** 生成带时间戳的文件名（含 .xlsx），如 {@code 系统导出数据20260912153000.xlsx} */
    public static String timestampedFileName(String prefix) {
        return prefix + LocalDateTime.now().format(TS_FORMAT) + ".xlsx";
    }

    /** 设置下载响应头（MIME + 附件文件名，UTF-8 编码） */
    public static void prepareDownload(HttpServletResponse response, String rawFileName) {
        response.setContentType(XLSX_CONTENT_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String encoded = URLEncoder.encode(rawFileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encoded);
    }

    /**
     * 把行数据流式写为单个 Sheet。
     *
     * @param response  下载响应
     * @param sheetName Sheet 名
     * @param clazz     带 {@code @ExcelProperty} 的行模型类
     * @param rows      行数据（可为空列表，将输出仅表头的空表）
     */
    public static <T> void writeSheet(HttpServletResponse response, String sheetName,
                                      Class<T> clazz, List<T> rows) {
        try {
            EasyExcel.write(response.getOutputStream(), clazz)
                    .sheet(sheetName)
                    .doWrite(rows);
        } catch (IOException e) {
            // 写出阶段异常（客户端中断等）统一转业务异常，交由全局异常处理记录
            throw new BizException(500, "导出文件写出失败：" + e.getMessage());
        }
    }
}
