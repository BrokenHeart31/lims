package com.lims.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lims.common.PageResult;
import com.lims.common.exception.BizException;
import com.lims.dto.ProductLibItemSaveDTO;
import com.lims.dto.ProductLibSaveDTO;
import com.lims.dto.excel.ProductLibImportRow;
import com.lims.entity.ProductLib;
import com.lims.entity.ProductLibItem;
import com.lims.mapper.ProductLibItemMapper;
import com.lims.mapper.ProductLibMapper;
import com.lims.service.ProductLibService;
import com.lims.service.excel.ProductLibImportListener;
import com.lims.service.result.ResultEntryPolicy;
import com.lims.vo.ProductLibItemVO;
import com.lims.vo.ProductLibVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目标准库服务实现（T-106）。
 *
 * <p><b>本类是标准库的唯一写入方</b>（见 DECISIONS 2026-09-11「快照下沉」）：
 * T-401 项目分解只读本域做快照复制，T-601 判定引擎只读 `sample_item`。
 * 因此本域的任何修改**不会回溯**已分解样品——这是特性不是缺陷，
 * 目的是让已出报告固化「检验当时的判定依据」（国标更新不追溯篡改历史结论）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductLibServiceImpl implements ProductLibService {

    private final ProductLibMapper productLibMapper;
    private final ProductLibItemMapper productLibItemMapper;

    // =========================================================================
    // 产品库
    // =========================================================================

    @Override
    public PageResult<ProductLibVO> pageProduct(long current, long size, String productCode, String productName,
                                                String category) {
        LambdaQueryWrapper<ProductLib> wrapper = new LambdaQueryWrapper<ProductLib>()
                .likeRight(StringUtils.hasText(productCode), ProductLib::getProductCode, productCode)
                .like(StringUtils.hasText(productName), ProductLib::getProductName, productName)
                .like(StringUtils.hasText(category), ProductLib::getCategory, category)
                .orderByAsc(ProductLib::getProductCode);

        IPage<ProductLib> page = productLibMapper.selectPage(new Page<>(current, size), wrapper);
        List<ProductLib> rows = page.getRecords();

        // itemCount 批量统计（一次 GROUP BY，避免逐行 count 造成 N+1）
        Map<Long, Long> countById = countItems(rows.stream().map(ProductLib::getId).toList());

        return PageResult.of(page, e -> toVO(e, countById.getOrDefault(e.getId(), 0L), null));
    }

    @Override
    public ProductLibVO detail(Long id) {
        ProductLib lib = requireProduct(id);
        List<ProductLibItemVO> items = listItems(id);
        return toVO(lib, (long) items.size(), items);
    }

    @Override
    public Long createProduct(ProductLibSaveDTO dto) {
        ensureProductCodeUnique(dto.getProductCode(), null);
        ProductLib entity = new ProductLib();
        entity.setProductCode(dto.getProductCode().trim());
        entity.setProductName(trimToNull(dto.getProductName()));
        entity.setCategory(trimToNull(dto.getCategory()));
        entity.setRemark(trimToNull(dto.getRemark()));
        productLibMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateProduct(ProductLibSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(400, "更新时 id 不能为空");
        }
        requireProduct(dto.getId());
        ensureProductCodeUnique(dto.getProductCode(), dto.getId());

        ProductLib entity = new ProductLib();
        entity.setId(dto.getId());
        entity.setProductCode(dto.getProductCode().trim());
        entity.setProductName(trimToNull(dto.getProductName()));
        entity.setCategory(trimToNull(dto.getCategory()));
        entity.setRemark(trimToNull(dto.getRemark()));
        productLibMapper.updateById(entity);
    }

    @Override
    public void removeProduct(Long id) {
        requireProduct(id);
        Long itemCount = productLibItemMapper.selectCount(new LambdaQueryWrapper<ProductLibItem>()
                .eq(ProductLibItem::getProductLibId, id));
        if (itemCount != null && itemCount > 0) {
            throw new BizException(400,
                    "该产品下仍有 " + itemCount + " 条检测单项，请先清理明细后再删除产品"
                            + "（否则这些明细会成为无法通过产品检索到的孤儿数据）");
        }
        productLibMapper.deleteById(id);
    }

    // =========================================================================
    // 检测单项
    // =========================================================================

    @Override
    public List<ProductLibItemVO> listItems(Long productLibId) {
        if (productLibId == null) {
            throw new BizException(400, "产品库ID不能为空");
        }
        return productLibItemMapper.selectList(new LambdaQueryWrapper<ProductLibItem>()
                        .eq(ProductLibItem::getProductLibId, productLibId)
                        .orderByAsc(ProductLibItem::getItemOrder)
                        .orderByAsc(ProductLibItem::getId))
                .stream().map(this::toItemVO).toList();
    }

    @Override
    public Long createItem(ProductLibItemSaveDTO dto) {
        if (dto.getProductLibId() == null) {
            throw new BizException(400, "产品库ID不能为空");
        }
        requireProduct(dto.getProductLibId());
        validateJudgeConsistency(dto.getJudgeType(), dto.getStdValue(), dto.getItemName());

        ProductLibItem entity = new ProductLibItem();
        applyItem(entity, dto);
        entity.setProductLibId(dto.getProductLibId());
        productLibItemMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateItem(ProductLibItemSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException(400, "更新时 id 不能为空");
        }
        ProductLibItem exist = productLibItemMapper.selectById(dto.getId());
        if (exist == null) {
            throw new BizException(400, "检测单项不存在或已删除: id=" + dto.getId());
        }
        validateJudgeConsistency(dto.getJudgeType(), dto.getStdValue(), dto.getItemName());

        ProductLibItem entity = new ProductLibItem();
        entity.setId(dto.getId());
        applyItem(entity, dto);
        // 不允许改挂到不存在的产品
        if (dto.getProductLibId() != null && !dto.getProductLibId().equals(exist.getProductLibId())) {
            requireProduct(dto.getProductLibId());
            entity.setProductLibId(dto.getProductLibId());
        }
        productLibItemMapper.updateById(entity);
    }

    @Override
    public void removeItem(Long id) {
        if (id == null) {
            throw new BizException(400, "id 不能为空");
        }
        if (productLibItemMapper.selectById(id) == null) {
            throw new BizException(400, "检测单项不存在或已删除: id=" + id);
        }
        productLibItemMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int replaceItems(Long productLibId, List<ProductLibItemSaveDTO> items) {
        requireProduct(productLibId);
        List<ProductLibItemSaveDTO> list = items == null ? List.of() : items;

        // 先全量校验，再动库——避免「删完旧的才发现新数据非法」，留下空产品
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            ProductLibItemSaveDTO dto = list.get(i);
            int line = i + 1;
            if (!StringUtils.hasText(dto.getItemName())) {
                errors.add("第 " + line + " 项：检测项目名称不能为空");
                continue;
            }
            if (dto.getJudgeType() == null || !ResultEntryPolicy.isValidJudgeType(dto.getJudgeType())) {
                errors.add("第 " + line + " 项：判定类型只能为 1/2/3");
                continue;
            }
            String err = checkJudgeConsistency(dto.getJudgeType(), dto.getStdValue());
            if (err != null) {
                errors.add("第 " + line + " 项（" + dto.getItemName() + "）：" + err);
            }
        }
        if (!errors.isEmpty()) {
            throw new BizException(400, "保存被拒绝，存在 " + errors.size() + " 处问题：" + String.join("；", errors));
        }

        // 覆盖式：先逻辑删除旧明细，再全量重建（与 T-401「保存分解为覆盖式」同一决策逻辑）
        productLibItemMapper.delete(new LambdaQueryWrapper<ProductLibItem>()
                .eq(ProductLibItem::getProductLibId, productLibId));

        int order = 0;
        for (ProductLibItemSaveDTO dto : list) {
            ProductLibItem entity = new ProductLibItem();
            applyItem(entity, dto);
            entity.setProductLibId(productLibId);
            // 顺序号以数组下标为准（前端已重排为连续 1..N），忽略传入的 itemOrder 以避免跳号
            entity.setItemOrder(++order);
            productLibItemMapper.insert(entity);
        }
        return order;
    }

    // =========================================================================
    // Excel 导入
    // =========================================================================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importExcel(InputStream in) {
        ProductLibImportListener listener = new ProductLibImportListener(productLibMapper, productLibItemMapper);
        EasyExcel.read(in, ProductLibImportRow.class, listener).sheet().doRead();
        return listener.result();
    }

    // =========================================================================
    // 内部工具
    // =========================================================================

    private ProductLib requireProduct(Long id) {
        if (id == null) {
            throw new BizException(400, "产品库ID不能为空");
        }
        ProductLib lib = productLibMapper.selectById(id);
        if (lib == null) {
            throw new BizException(400, "产品不存在或已删除: id=" + id);
        }
        return lib;
    }

    private void ensureProductCodeUnique(String productCode, Long excludeId) {
        LambdaQueryWrapper<ProductLib> wrapper = new LambdaQueryWrapper<ProductLib>()
                .eq(ProductLib::getProductCode, productCode.trim())
                .ne(excludeId != null, ProductLib::getId, excludeId);
        if (productLibMapper.selectCount(wrapper) > 0) {
            throw new BizException(400, "产品编号已存在: " + productCode.trim());
        }
    }

    /** 批量统计各产品的检测单项数（一次 GROUP BY，避免 N+1） */
    private Map<Long, Long> countItems(List<Long> productLibIds) {
        if (productLibIds == null || productLibIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> out = new LinkedHashMap<>();
        productLibItemMapper.selectList(new LambdaQueryWrapper<ProductLibItem>()
                        .select(ProductLibItem::getProductLibId)
                        .in(ProductLibItem::getProductLibId, productLibIds))
                .forEach(i -> out.merge(i.getProductLibId(), 1L, Long::sum));
        return out;
    }

    private void applyItem(ProductLibItem entity, ProductLibItemSaveDTO dto) {
        entity.setItemName(dto.getItemName().trim());
        entity.setItemOrder(dto.getItemOrder() == null ? 0 : dto.getItemOrder());
        entity.setUnit(trimToNull(dto.getUnit()));
        entity.setBasisCode(trimToNull(dto.getBasisCode()));
        entity.setMethods(trimToNull(dto.getMethods()));
        entity.setStdValue(trimToNull(dto.getStdValue()));
        entity.setJudgeType(dto.getJudgeType());
        entity.setIsReference(dto.getIsReference() == null ? 0 : dto.getIsReference());
        entity.setLowerLimit(trimToNull(dto.getLowerLimit()));
        entity.setMethodNote(trimToNull(dto.getMethodNote()));
    }

    /**
     * 判定类型与标准值形态一致性校验（落库前 fail-loud）。
     *
     * <p>口径来自 DECISIONS 2026-09-12「形态与判定类型矛盾时判待判定 + WARN」：
     * 矛盾数据在判定时无法得出可信结论，最终会落到「待判定 + WARN」。
     * 但**上游能拦住就不该放到下游**——标准库是所有样品的下游数据源，
     * 一行脏数据会污染所有套用该产品的样品，且排查成本远高于录入时的一次拒绝。</p>
     */
    private void validateJudgeConsistency(Integer judgeType, String stdValue, String itemName) {
        if (judgeType == null || !ResultEntryPolicy.isValidJudgeType(judgeType)) {
            throw new BizException(400, "判定类型只能为 1(限量比较)/2(不得检出或不得使用)/3(文本感官人工)");
        }
        String err = checkJudgeConsistency(judgeType, stdValue);
        if (err != null) {
            throw new BizException(400, "检测项目「" + itemName + "」" + err);
        }
    }

    /** 一致性检查；返回 null 表示通过 */
    private String checkJudgeConsistency(Integer judgeType, String stdValue) {
        if (judgeType == null) {
            return null;
        }
        String v = stdValue == null ? "" : stdValue.trim();
        // 3=文本/感官人工：标准值可以是任意文本（如「具有水产品应有色泽」），不做数值约束
        if (judgeType == ResultEntryPolicy.JUDGE_TYPE_TEXT) {
            return null;
        }
        if (judgeType == ResultEntryPolicy.JUDGE_TYPE_LIMIT) {
            if (v.isEmpty()) {
                return "判定类型为「限量比较」时标准值（限量值）不能为空（如 0.5 / ≤0.5）";
            }
            if (v.contains("不得检出") || v.contains("不得使用")) {
                return "判定类型为「限量比较」时标准值不能是「不得检出/不得使用」，应改为判定类型 2";
            }
            if (parseStdNumber(v) == null) {
                return "判定类型为「限量比较」时标准值必须是数值（如 0.5 或 ≤0.5），当前为「" + v + "」";
            }
            return null;
        }
        if (judgeType == ResultEntryPolicy.JUDGE_TYPE_NOT_DETECTED) {
            if (v.isEmpty()) {
                return "判定类型为「不得检出/不得使用」时标准值不能为空（应为 不得检出 / 不得使用）";
            }
            if (!v.contains("不得检出") && !v.contains("不得使用")) {
                return "判定类型为「不得检出/不得使用」时标准值须为 不得检出 或 不得使用，当前为「" + v
                        + "」；若为数值限量请改为判定类型 1";
            }
            return null;
        }
        return null;
    }

    /** 从标准值文本中提取数值（支持前缀 ≤ / ≥ / < / > 与空白），无法解析返回 null */
    private static Double parseStdNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.replace("≤", "").replace("≥", "").replace("<", "")
                .replace(">", "").replace("=", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ProductLibVO toVO(ProductLib entity, Long itemCount, List<ProductLibItemVO> items) {
        ProductLibVO vo = new ProductLibVO();
        vo.setId(entity.getId());
        vo.setProductCode(entity.getProductCode());
        vo.setProductName(entity.getProductName());
        vo.setCategory(entity.getCategory());
        vo.setRemark(entity.getRemark());
        vo.setItemCount(itemCount);
        vo.setItems(items);
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private ProductLibItemVO toItemVO(ProductLibItem src) {
        ProductLibItemVO vo = new ProductLibItemVO();
        vo.setId(src.getId());
        vo.setProductLibId(src.getProductLibId());
        vo.setItemOrder(src.getItemOrder());
        vo.setItemName(src.getItemName());
        vo.setUnit(src.getUnit());
        vo.setBasisCode(src.getBasisCode());
        vo.setMethods(src.getMethods());
        vo.setStdValue(src.getStdValue());
        vo.setJudgeType(src.getJudgeType());
        vo.setJudgeTypeLabel(ResultEntryPolicy.judgeTypeLabel(src.getJudgeType()));
        vo.setIsReference(src.getIsReference());
        vo.setLowerLimit(src.getLowerLimit());
        vo.setMethodNote(src.getMethodNote());
        return vo;
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
