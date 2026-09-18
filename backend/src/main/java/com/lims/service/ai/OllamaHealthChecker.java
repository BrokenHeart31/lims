package com.lims.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lims.entity.GbDocument;
import com.lims.mapper.GbDocumentMapper;
import com.lims.vo.AiStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 服务健康检查（feature A，T03 / api-spec A1；增量 ai_flow_assistant 追加三就绪自检）。
 *
 * <p><b>fail-soft</b>：本方法**永不抛异常**——离线是正常状态不是错误。短超时（默认 1.5s）
 * 保证离线时快速返回，前端角标能立即显示「不可用 + 启动指引」而不卡界面。</p>
 *
 * <p><b>三就绪（C-11）</b>：{@code online}（服务在线）/ {@code modelPresent}（模型就绪）/
 * {@code kbReady}（标准索引已入库 ≥1 篇）。任一不就绪时 {@code hint} 给出具体下一步。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaHealthChecker {

    private final OllamaClient ollamaClient;
    private final AiProperties props;
    private final GbDocumentMapper gbDocumentMapper;

    /** 探测 Ollama 并在位模型，装配状态 VO。 */
    public AiStatusVO check() {
        AiStatusVO vo = new AiStatusVO();
        vo.setBaseUrl(props.getBaseUrl());
        vo.setModel(props.getModel());
        vo.setStartScript(props.getStartScript());
        fillKbReadiness(vo);

        long start = System.currentTimeMillis();
        List<String> tags;
        try {
            tags = ollamaClient.tags();
        } catch (RuntimeException e) {
            vo.setOnline(false);
            vo.setModelPresent(false);
            vo.setLatencyMs(-1);
            vo.setHint("本地模型服务未启动；业务功能不受影响。请运行 " + props.getStartScript()
                    + kbSuffix(vo));
            return vo;
        }
        vo.setOnline(true);
        vo.setLatencyMs(System.currentTimeMillis() - start);
        boolean present = tags.stream().anyMatch(this::matchesModel);
        vo.setModelPresent(present);
        if (present) {
            vo.setHint("AI 服务正常" + kbSuffix(vo));
        } else {
            vo.setHint("AI 服务在线，但模型 " + props.getModel()
                    + " 未就绪；请运行 ai/scripts/deploy-ollama.ps1 拉取模型" + kbSuffix(vo));
        }
        return vo;
    }

    /** 标准索引就绪（只读 gb_document；任何异常都不影响健康检查本身）。 */
    private void fillKbReadiness(AiStatusVO vo) {
        long count = 0;
        try {
            Long n = gbDocumentMapper.selectCount(new LambdaQueryWrapper<GbDocument>()
                    .eq(GbDocument::getStatus, GbDocument.STATUS_DONE));
            count = n == null ? 0 : n;
        } catch (RuntimeException e) {
            log.debug("[ai] 标准索引就绪探测失败（忽略）：{}", e.getMessage());
        }
        vo.setKbDocCount(count);
        vo.setKbReady(count > 0);
    }

    private String kbSuffix(AiStatusVO vo) {
        if (vo.isKbReady()) {
            return "；标准索引已就绪（" + vo.getKbDocCount() + " 篇）";
        }
        return "；标准索引为空，请在「AI 助手 → 标准库导入」导入标准文件";
    }

    /** 模型名匹配：精确、或带 tag 前缀（如配置 qwen3:4b 命中本地 qwen3:4b-instruct）。 */
    private boolean matchesModel(String tag) {
        String want = props.getModel();
        if (tag == null || want == null) {
            return false;
        }
        return tag.equals(want) || tag.startsWith(want) || want.startsWith(tag);
    }
}
