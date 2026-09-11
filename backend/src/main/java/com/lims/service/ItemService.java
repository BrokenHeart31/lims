package com.lims.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lims.dto.ItemSaveDTO;
import com.lims.entity.SampleItem;
import com.lims.vo.ItemMatchVO;
import com.lims.vo.ItemPendingVO;

import java.util.List;

/**
 * 项目分解服务（T-401）。
 *
 * <p>业务依据：说明书「五、样品检验明细项目分解（自动套用项目库）」——
 * 标准库自动加载检测单项初稿，允许人工增删调整，「确认保存」后流转 S20→S30。</p>
 */
public interface ItemService extends IService<SampleItem> {

    /**
     * 套库预览：按样品名匹配产品标准库，返回检测单项初稿（不落库）。
     *
     * @param sampleId 样品ID（须存在）
     */
    ItemMatchVO match(Long sampleId);

    /**
     * 查询样品已保存的分解明细（deleted=0，按 itemOrder 升序）。
     *
     * @return 明细列表；样品不存在时抛 BizException
     */
    List<SampleItem> listBySampleId(Long sampleId);

    /**
     * 保存分解（覆盖式，全量替换）。要求样品状态为 S20。
     *
     * @return 保存后的明细数
     */
    int saveDecompose(ItemSaveDTO dto);

    /**
     * 分解确认：S20 → S30（要求已有分解明细 + 状态机白名单校验 + 乐观条件 UPDATE）。
     *
     * @return 流转后样品状态 code（30）
     */
    int confirm(Long sampleId);

    /**
     * 分页查询待分解样品（status=S20）。
     */
    Page<ItemPendingVO> pagePending(long pageNum, long pageSize, String sampleNo, String sampleName);
}
