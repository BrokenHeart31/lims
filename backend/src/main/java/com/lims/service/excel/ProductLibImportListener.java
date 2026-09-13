package com.lims.service.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.dto.excel.ProductLibImportRow;
import com.lims.entity.ProductLib;
import com.lims.entity.ProductLibItem;
import com.lims.mapper.ProductLibItemMapper;
import com.lims.mapper.ProductLibMapper;
import com.lims.service.ProductLibService;
import com.lims.service.result.ResultEntryPolicy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目标准库 Excel 导入监听器（T-106，EasyExcel SAX 流式）。
 *
 * <p>依 .agents/skills/excel-import/SKILL.md：批大小 {@value #BATCH_COUNT}、逐行校验失败不中断、
 * 行号 = {@code rowIndex + 1}、每文件一实例。</p>
 *
 * <p><b>与 T-105 监听器的关键差异：本表是「一对多」结构</b>
 * （一个产品编号对应多条检测单项），故导入策略是
 * <b>先按产品编号在文件内分组 → 对出现的每个产品执行覆盖式写入</b>：
 * 该产品的旧明细全部逻辑删除，再按本文件的行重建。
 * 这样「表格里删掉的一行」在系统里也会消失——增量合并会让已被标准淘汰的项目永远残留。</p>
 *
 * <p>校验失败的行被跳过，但**不回滚该产品已写入的合法行**：与 T-301 采样单部分失败不回滚一致，
 * 业务方可修正后重导（覆盖式保证重导是幂等的）。</p>
 */
public class ProductLibImportListener extends AnalysisEventListener<ProductLibImportRow> {

    public static final int BATCH_COUNT = 1000;

    private final ProductLibMapper productLibMapper;
    private final ProductLibItemMapper productLibItemMapper;

    private final List<PendingRow> cache = new ArrayList<>(BATCH_COUNT);
    private final List<String> errors = new ArrayList<>();
    private int productCount;
    private int productUpdated;
    private int itemCount;
    private int failCount;

    public ProductLibImportListener(ProductLibMapper productLibMapper, ProductLibItemMapper productLibItemMapper) {
        this.productLibMapper = productLibMapper;
        this.productLibItemMapper = productLibItemMapper;
    }

    @Override
    public void invoke(ProductLibImportRow row, AnalysisContext context) {
        int rowNum = context.readRowHolder().getRowIndex() + 1;
        if (isFullyBlank(row)) {
            return;
        }
        cache.add(new PendingRow(rowNum, row));
        if (cache.size() >= BATCH_COUNT) {
            flush();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        flush();
    }

    private void flush() {
        if (cache.isEmpty()) {
            return;
        }
        // 按产品编号分组（保持文件内首次出现顺序，LinkedHashMap）
        Map<String, List<PendingRow>> byProduct = new LinkedHashMap<>();
        for (PendingRow row : cache) {
            String code = trim(row.row.getProductCode());
            if (code.isEmpty()) {
                failCount++;
                errors.add("第 " + row.rowNum + " 行：产品编号不能为空");
                continue;
            }
            byProduct.computeIfAbsent(code, k -> new ArrayList<>()).add(row);
        }

        for (Map.Entry<String, List<PendingRow>> entry : byProduct.entrySet()) {
            importOneProduct(entry.getKey(), entry.getValue());
        }
        cache.clear();
    }

    /** 导入单个产品：校验全部行 → 覆盖式写入明细 */
    private void importOneProduct(String productCode, List<PendingRow> rows) {
        // 逐行校验，收集合法行
        List<ProductLibItem> valid = new ArrayList<>();
        String productName = null;
        String category = null;
        boolean anyError = false;
        int order = 0;

        for (PendingRow pending : rows) {
            ProductLibImportRow row = pending.row;
            List<String> rowErrors = new ArrayList<>();
            String itemName = trim(row.getItemName());
            String stdValue = trim(row.getStdValue());
            Integer judgeType = parseJudgeType(row.getJudgeType(), rowErrors);
            Integer isReference = parseIsReference(row.getIsReference(), rowErrors);

            if (itemName.isEmpty()) {
                rowErrors.add("检测项目名称不能为空");
            } else if (itemName.length() > 255) {
                rowErrors.add("检测项目名称长度不能超过 255");
            }
            if (stdValue.length() > 50) {
                rowErrors.add("限量值长度不能超过 50");
            }
            if (judgeType != null) {
                String consistency = checkJudgeConsistency(judgeType, stdValue);
                if (consistency != null) {
                    rowErrors.add(consistency);
                }
            }

            if (!rowErrors.isEmpty()) {
                failCount++;
                anyError = true;
                errors.add("第 " + pending.rowNum + " 行：" + String.join("；", rowErrors));
                continue;
            }

            // 产品级信息取文件中第一条非空值（同一产品多行会重复填写）
            if (productName == null) {
                productName = trim(row.getProductName());
            }
            if (category == null) {
                category = trim(row.getCategory());
            }

            ProductLibItem item = new ProductLibItem();
            item.setItemOrder(++order);
            item.setItemName(itemName);
            item.setUnit(trimToNull(row.getUnit()));
            item.setBasisCode(trimToNull(row.getBasisCode()));
            item.setMethods(trimToNull(row.getMethods()));
            item.setStdValue(trimToNull(stdValue));
            item.setJudgeType(judgeType);
            item.setIsReference(isReference == null ? 0 : isReference);
            item.setLowerLimit(trimToNull(row.getLowerLimit()));
            item.setMethodNote(trimToNull(row.getMethodNote()));
            valid.add(item);
        }

        if (valid.isEmpty()) {
            // 该产品一行都没活下来：不新建空产品（空产品在套库时表现为「匹配到但零项目」，比报错更难发现）
            if (anyError) {
                errors.add("产品 " + productCode + " 的全部行均校验失败，已跳过该产品");
            }
            return;
        }

        ProductLib lib = productLibMapper.selectOne(new LambdaQueryWrapper<ProductLib>()
                .eq(ProductLib::getProductCode, productCode)
                .last("limit 1"));
        if (lib == null) {
            lib = new ProductLib();
            lib.setProductCode(productCode);
            lib.setProductName(productName);
            lib.setCategory(category);
            productLibMapper.insert(lib);
            productCount++;
        } else {
            // 仅在产品名/大类为空、或文件提供了新值时才更新（避免文件留空把已有信息抹掉）
            boolean needUpdate = false;
            if (productName != null && !productName.isEmpty()
                    && !productName.equals(lib.getProductName())) {
                lib.setProductName(productName);
                needUpdate = true;
            }
            if (category != null && !category.isEmpty() && !category.equals(lib.getCategory())) {
                lib.setCategory(category);
                needUpdate = true;
            }
            if (needUpdate) {
                productLibMapper.updateById(lib);
                productUpdated++;
            }
        }

        // 覆盖式：先逻辑删除旧明细，再重建（幂等重导）
        productLibItemMapper.delete(new LambdaQueryWrapper<ProductLibItem>()
                .eq(ProductLibItem::getProductLibId, lib.getId()));
        for (ProductLibItem item : valid) {
            item.setProductLibId(lib.getId());
            productLibItemMapper.insert(item);
            itemCount++;
        }
    }

    public ProductLibService.ImportResult result() {
        return new ProductLibService.ImportResult(productCount, productUpdated, itemCount, failCount, errors);
    }

    // ------------------------------------------------------------------ 工具

    private static Integer parseJudgeType(String raw, List<String> rowErrors) {
        String v = trim(raw);
        if (v.isEmpty()) {
            rowErrors.add("判定类型不能为空（限量比较/不得检出/文本，或 1/2/3）");
            return null;
        }
        switch (v) {
            case "1", "限量比较", "限量" -> {
                return ResultEntryPolicy.JUDGE_TYPE_LIMIT;
            }
            case "2", "不得检出", "不得使用", "不得检出/不得使用" -> {
                return ResultEntryPolicy.JUDGE_TYPE_NOT_DETECTED;
            }
            case "3", "文本", "感官", "文本/感官人工", "人工" -> {
                return ResultEntryPolicy.JUDGE_TYPE_TEXT;
            }
            default -> {
                rowErrors.add("判定类型无法识别: " + v + "（应为 限量比较/不得检出/文本 或 1/2/3）");
                return null;
            }
        }
    }

    private static Integer parseIsReference(String raw, List<String> rowErrors) {
        String v = trim(raw);
        if (v.isEmpty()) {
            return 0;
        }
        return switch (v) {
            case "1", "是", "Y", "y" -> 1;
            case "0", "否", "N", "n" -> 0;
            default -> {
                rowErrors.add("是否参考项只能为 是/否 或 1/0: " + v);
                yield null;
            }
        };
    }

    /** 与 ProductLibServiceImpl#checkJudgeConsistency 同口径（此处为静态副本，避免监听器依赖 Service 实例） */
    private static String checkJudgeConsistency(Integer judgeType, String stdValue) {
        if (judgeType == null) {
            return null;
        }
        String v = stdValue == null ? "" : stdValue.trim();
        if (judgeType == ResultEntryPolicy.JUDGE_TYPE_TEXT) {
            return null;
        }
        if (judgeType == ResultEntryPolicy.JUDGE_TYPE_LIMIT) {
            if (v.isEmpty()) {
                return "判定类型为「限量比较」时限量值不能为空";
            }
            if (v.contains("不得检出") || v.contains("不得使用")) {
                return "判定类型为「限量比较」时限量值不能是「不得检出/不得使用」，应改为判定类型 2";
            }
            String cleaned = v.replace("≤", "").replace("≥", "").replace("<", "")
                    .replace(">", "").replace("=", "").trim();
            try {
                Double.parseDouble(cleaned);
            } catch (NumberFormatException e) {
                return "判定类型为「限量比较」时限量值必须是数值（如 0.5 或 ≤0.5），当前为「" + v + "」";
            }
            return null;
        }
        if (judgeType == ResultEntryPolicy.JUDGE_TYPE_NOT_DETECTED) {
            if (v.isEmpty()) {
                return "判定类型为「不得检出/不得使用」时判定标准不能为空";
            }
            if (!v.contains("不得检出") && !v.contains("不得使用")) {
                return "判定类型为「不得检出/不得使用」时判定标准须为 不得检出 或 不得使用，当前为「" + v + "」";
            }
            return null;
        }
        return null;
    }

    private static boolean isFullyBlank(ProductLibImportRow row) {
        return trim(row.getProductCode()).isEmpty() && trim(row.getItemName()).isEmpty()
                && trim(row.getProductName()).isEmpty() && trim(row.getStdValue()).isEmpty()
                && trim(row.getBasisCode()).isEmpty();
    }

    private static String trim(String v) {
        return v == null ? "" : v.trim();
    }

    private static String trimToNull(String v) {
        String t = trim(v);
        return t.isEmpty() ? null : t;
    }

    private record PendingRow(int rowNum, ProductLibImportRow row) {
    }
}
