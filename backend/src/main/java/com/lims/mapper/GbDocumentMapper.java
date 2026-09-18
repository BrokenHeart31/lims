package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.GbDocument;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * GB 标准文档 Mapper（feature A，T03）。
 */
public interface GbDocumentMapper extends BaseMapper<GbDocument> {

    /**
     * 库内已收录（已生效）的标准号，供「用户点名的标准是否收录」判定与诚实提示用。
     *
     * <p>原生 {@code @Select} 不会被 MP 自动追加 {@code deleted = 0}，故显式写出。</p>
     */
    @Select("SELECT DISTINCT std_no FROM gb_document WHERE deleted = 0 AND status = 1 AND std_no IS NOT NULL")
    List<String> selectIndexedStdNos();

    /**
     * 物理删除文档（删除索引时用，见设计 §2.10：GB 索引表允许物理重建）。
     *
     * <p><b>为什么不走 MP 逻辑删除</b>：{@code gb_document.checksum} 是唯一键，若软删（置 deleted=1）
     * 则行仍在、唯一键仍占用，同文件再导入会撞 {@code uk_gb_doc_checksum}（1062）。
     * GB 索引是**派生数据**，删除即物理清掉，与其条款一起重建。</p>
     */
    @Delete("DELETE FROM gb_document WHERE id = #{id}")
    int deletePhysicallyById(@Param("id") Long id);
}
