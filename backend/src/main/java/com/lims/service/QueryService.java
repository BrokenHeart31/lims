package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.QueryLibraryDTO;
import com.lims.dto.QuerySampleDTO;
import com.lims.vo.HistoryQueryVO;
import com.lims.vo.LibraryItemVO;
import com.lims.vo.LibraryQueryVO;
import com.lims.vo.TestingQueryVO;

import java.util.List;

/**
 * 查询域服务（T-801）：在检样品 / 历史样品 / 项目库，三块只读查询。
 *
 * <p>所有派生字段（进度、当前处理人、停留时长、中文标签）均在本层计算，
 * 复用既有口径类（{@code ResultEntryPolicy}）与枚举（{@code SampleStatus} / {@code ResultConclusion}），
 * 不在 SQL 里硬编码中文、不另写判定逻辑。</p>
 */
public interface QueryService {

    /** 在检样品分页（status 10..70） */
    PageResult<TestingQueryVO> pageTesting(long current, long size, QuerySampleDTO q);

    /** 历史样品分页（status 80/90） */
    PageResult<HistoryQueryVO> pageHistory(long current, long size, QuerySampleDTO q);

    /** 项目库（产品）分页 */
    PageResult<LibraryQueryVO> pageLibrary(long current, long size, QueryLibraryDTO q);

    /** 某产品的检测单项列表（按项次升序） */
    List<LibraryItemVO> listLibraryItems(Long productLibId);
}
