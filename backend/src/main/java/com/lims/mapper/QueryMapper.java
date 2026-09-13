package com.lims.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lims.dto.QueryLibraryDTO;
import com.lims.dto.QuerySampleDTO;
import com.lims.dto.excel.MyTaskExportRow;
import com.lims.dto.excel.ProvinceExportRow;
import com.lims.vo.HistoryQueryVO;
import com.lims.vo.LibraryQueryVO;
import com.lims.vo.TestingQueryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 查询域 / 导出域只读 Mapper（T-801 + T-802）。
 *
 * <p><b>为什么单独立一个 Mapper 而不挂到 SampleMapper</b>：这些查询是「跨表读取 + 只读投影」，
 * 与样品登记的写模型无耦合；独立 Mapper 便于把复杂 JOIN / 聚合 SQL 收进一个 {@code QueryMapper.xml}
 * 集中维护，也避免污染既有实体的 BaseMapper。</p>
 *
 * <p><b>逻辑删除提醒</b>：自定义 XML SQL 不受 MyBatis-Plus 逻辑删除自动追加，
 * 故每条 SQL 都显式带 {@code deleted = 0}（见 QueryMapper.xml）。</p>
 */
@Mapper
public interface QueryMapper {

    /**
     * 在检样品分页（status 10..70）。返回的 VO 仅含表头与原始时间字段，
     * 进度 / 处理人 / 停留时长由 Service 依据同批 items、results 补齐。
     */
    IPage<TestingQueryVO> pageTesting(IPage<TestingQueryVO> page, @Param("q") QuerySampleDTO q);

    /** 历史样品分页（status 80/90）。 */
    IPage<HistoryQueryVO> pageHistory(IPage<HistoryQueryVO> page, @Param("q") QuerySampleDTO q);

    /** 项目库（产品）分页，带出检测单项数。 */
    IPage<LibraryQueryVO> pageLibrary(IPage<LibraryQueryVO> page, @Param("q") QueryLibraryDTO q);

    /**
     * 省平台上报导出数据（一行 = 样品 × 检测单项，status>=80，可选按任务编号筛选）。
     * 单条 JOIN 查询直接查平，避免 N+1。
     */
    List<ProvinceExportRow> listProvinceRows(@Param("taskNo") String taskNo);

    /**
     * 检验员任务导出（一行 = 样品 × 已指派检测单项，status>=40）。
     *
     * @param testerNo 检验员工号；为 {@code null} 时不按检验员过滤（R100 综合管理导出全部）
     */
    List<MyTaskExportRow> listMyTasks(@Param("testerNo") String testerNo);
}
