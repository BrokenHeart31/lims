package com.lims.service;

import com.lims.common.PageResult;
import com.lims.dto.TesterMethodSaveDTO;
import com.lims.vo.TesterMethodVO;

import java.io.InputStream;

/**
 * 方法-检验员资质服务（api-spec 第 11 章，T-105）。
 *
 * <p>业务语义（说明书第二(2)节）：一行 = 某检验员对某方法标准号具备资质；
 * T-501 自动分配第三级规则按本表匹配「方法 → 有资质的检验员」。
 * 本表在 T-501 落地时**实测 0 行**（数据缺口），故本域的首要目的是让业务方能自行补录。</p>
 *
 * <p>唯一键 {@code uk_tester_method(method_no, tester_no)}：同一「方法+检验员」只能有一行，
 * 重复录入须走更新（改资质状态/备注）而非新增——否则 T-501 的按 id 升序取首条会取到失效行。</p>
 */
public interface TesterMethodService {

    /** 分页查询：methodNo 前缀 / testerNo 精确 / methodName 模糊 / qualStatus 精确，按 id 倒序 */
    PageResult<TesterMethodVO> pageQuery(long current, long size, String methodNo, String testerNo,
                                         String methodName, Integer qualStatus);

    /** 新建：唯一键冲突、工号不存在、工号非检验员角色均 fail-loud */
    Long create(TesterMethodSaveDTO dto);

    /** 更新：存在性 + 唯一键（排除自身）校验 */
    void update(TesterMethodSaveDTO dto);

    /** 逻辑删除 */
    void remove(Long id);

    /**
     * Excel 批量导入（说明书「导入新的项目库」同类能力；资质导入模板见 public/templates）。
     *
     * <p>导入策略与 T-301 采样单一致：**部分失败不回滚**——合法行入库，错误行逐条报「行号 + 原因」；
     * 已存在的「方法+检验员」行按 DTO 覆盖（idempotent upsert），使同一份表格可反复导入。</p>
     *
     * @return 导入结果（成功/更新/失败条数与逐行错误）
     */
    ImportResult importExcel(InputStream in, String operator);

    /**
     * 导入结果。
     *
     * @param successCount 新增行数
     * @param updateCount  覆盖更新行数（已存在的「方法+检验员」）
     * @param failCount    失败行数
     * @param errors       逐行错误（「第 N 行：原因」）
     */
    record ImportResult(int successCount, int updateCount, int failCount, java.util.List<String> errors) {
    }
}
