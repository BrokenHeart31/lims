package com.lims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lims.entity.AiHintLog;

/**
 * AI 助手主动提示留痕 Mapper（feature 增量 ai_flow_assistant，T02，设计 §5.1）。
 *
 * <p>AI 域**唯一**的业务写入 Mapper（追加型留痕）；不触碰任何业务表。</p>
 */
public interface AiHintLogMapper extends BaseMapper<AiHintLog> {
}
