package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.ProductLibItemSaveDTO;
import com.lims.dto.ProductLibSaveDTO;
import com.lims.vo.ProductLibItemVO;
import com.lims.vo.ProductLibVO;

import java.io.InputStream;
import java.util.List;

/**
 * 项目标准库服务（api-spec 第 12 章，T-106）。
 *
 * <p>业务定位（说明书第二(3)节）：「项目标准库设置的目的是项目检测单项分解时系统根据项目标准库
 * 自动加载相关产品需要检测的检测单项，包括检测依据、检验方法、判定标准。
 * 选择『导入新的项目库』添加新的项目库数据。」</p>
 *
 * <p><b>与 T-401/T-601 的关系（不可违反）</b>：本域是标准库的**唯一写入方**；
 * 项目分解（T-401）只读本域做「快照下沉」，判定引擎（T-601）只读 `sample_item` 快照
 * （见 DECISIONS 2026-09-11「快照下沉」与 D5 裁决）。
 * 因此本域修改**不会、也不应**回溯影响已分解样品——报告必须固化检验当时的判定依据。</p>
 */
public interface ProductLibService {

    // ---------- 产品库 ----------

    PageResult<ProductLibVO> pageProduct(long current, long size, String productCode, String productName,
                                         String category);

    /** 产品详情（含检测单项列表），供维护页的「展开/编辑」使用 */
    ProductLibVO detail(Long id);

    Long createProduct(ProductLibSaveDTO dto);

    void updateProduct(ProductLibSaveDTO dto);

    /**
     * 逻辑删除产品：**先校验其下无检测单项**（有则 fail-loud）。
     *
     * <p>理由：`product_lib_item` 通过 `product_lib_id` 悬挂，删掉产品会让 3000+ 条明细变成孤儿数据，
     * 而 T-401 套库匹配按产品编号检索、不会报错，只会「静默查不到项目」——比报错更难排查。
     * 强制业务方先显式清理明细，是一个有意识的决定。</p>
     */
    void removeProduct(Long id);

    // ---------- 检测单项 ----------

    List<ProductLibItemVO> listItems(Long productLibId);

    Long createItem(ProductLibItemSaveDTO dto);

    void updateItem(ProductLibItemSaveDTO dto);

    void removeItem(Long id);

    /**
     * 替换某产品的全部检测单项（覆盖式，用于「导入新的项目库」后的整体校准）。
     *
     * <p>覆盖式而非增量：Excel 是业务方对某产品的**完整定义**，增量合并会让「表格里删掉的一行」
     * 在系统里残留。与 T-401「保存分解为覆盖式」同一决策逻辑。</p>
     *
     * @return 写入的明细条数
     */
    int replaceItems(Long productLibId, List<ProductLibItemSaveDTO> items);

    /**
     * Excel 导入项目库（说明书「导入新的项目库」）。
     *
     * <p>模板列（见 `docs/knowledge/` 与前端下载模板）：产品编号/产品名称/食品大类/顺序号/检测项目/
     * 单位/判定依据标准号/检验方法/限量值/判定类型/是否参考项/最低检出限/方法备注。</p>
     *
     * <p>策略：**按产品编号分组后覆盖式写入**（同一产品在文件内多次出现则合并）；
     * 逐行校验失败不回滚整批，返回「行号 + 原因」。</p>
     */
    ImportResult importExcel(InputStream in);

    /**
     * 导入结果。
     *
     * @param productCount 新建产品数
     * @param productUpdated 更新产品数
     * @param itemCount    写入明细数（覆盖后的有效总数）
     * @param failCount    失败行数
     * @param errors       逐行错误（「第 N 行：原因」）
     */
    record ImportResult(int productCount, int productUpdated, int itemCount, int failCount,
                        java.util.List<String> errors) {
    }
}
