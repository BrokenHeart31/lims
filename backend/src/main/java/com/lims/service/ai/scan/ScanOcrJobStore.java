package com.lims.service.ai.scan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lims.common.ResultCode;
import com.lims.common.exception.BizException;
import com.lims.service.ai.AiProperties;
import com.lims.vo.ScanOcrJobVO;
import com.lims.vo.ScanOcrRetryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 扫描件 OCR 侧车位读写器（feature 增量 ai_flow_assistant，T03，设计 §3.3 / §5.1）。
 *
 * <p><b>文件即接口</b>：只读 {@code ai/standards/.scan/<stdKey>/progress.json}，把逐页进度
 * 暴露给 UI；{@code retry} **只复位失败页为待跑**（删对应片段标记）+ 返回**待执行命令**，
 * **不代跑 OCR**（长任务、离线，见设计 §2.4 末条）。</p>
 *
 * <p>写入用「临时文件 + 原子替换」，避免后端读到半截 JSON。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScanOcrJobStore {

    /** 片段文件名（与 ai/scripts/ocr_engine.py 契约一致）。 */
    private static final String FRAG_FMT = "page-%04d.txt";

    private final AiProperties props;
    private final ObjectMapper objectMapper;

    /** 侧车位根目录（相对路径从 CWD 逐级上溯，兼容从仓库根或 backend/ 启动）。 */
    public Path scanRoot() {
        String d = StringUtils.hasText(props.getScanDir()) ? props.getScanDir().trim() : "ai/standards/.scan";
        Path p = Path.of(d);
        if (p.isAbsolute()) {
            return p;
        }
        Path base = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && base != null; i++) {
            Path cand = base.resolve(d);
            if (Files.isDirectory(cand)) {
                return cand;
            }
            base = base.getParent();
        }
        return Path.of(d).toAbsolutePath();
    }

    /** 列出全部 OCR 任务（读侧车位；无目录则空列表）。 */
    public List<ScanOcrJobVO> list() {
        Path root = scanRoot();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(root)) {
            List<ScanOcrJobVO> out = new ArrayList<>();
            s.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(dir -> readProgress(dir).ifPresent(out::add));
            return out;
        } catch (IOException e) {
            log.warn("[ai-scan] 读取侧车位失败：{}（{}）", root, e.getMessage());
            return List.of();
        }
    }

    /** 单个任务详情；不存在抛 4204。 */
    public Optional<ScanOcrJobVO> get(String stdKey) {
        if (!StringUtils.hasText(stdKey)) {
            return Optional.empty();
        }
        Path dir = scanRoot().resolve(stdKey.trim());
        return readProgress(dir);
    }

    /**
     * 复位失败页为待跑（删对应片段标记）并返回待执行命令（**不代跑**）。
     *
     * @throws BizException 4204（任务不存在）/ 4205（任务进行中）
     */
    public ScanOcrRetryVO resetFailedToPending(String stdKey) {
        if (!StringUtils.hasText(stdKey)) {
            throw new BizException(ResultCode.AI_OCR_JOB_NOT_FOUND);
        }
        Path dir = scanRoot().resolve(stdKey.trim());
        Optional<ScanOcrJobVO> job = readProgress(dir);
        if (job.isEmpty()) {
            throw new BizException(ResultCode.AI_OCR_JOB_NOT_FOUND);
        }
        ScanOcrJobVO vo = job.get();
        if ("running".equals(vo.getStatus()) || "pending".equals(vo.getStatus())) {
            throw new BizException(ResultCode.AI_OCR_JOB_BUSY);
        }
        List<Integer> pending = new ArrayList<>();
        for (ScanOcrJobVO.FailedPage fp : vo.getFailedPages()) {
            if (fp.getPage() == null) {
                continue;
            }
            pending.add(fp.getPage());
            Path frag = dir.resolve(String.format(FRAG_FMT, fp.getPage()));
            try {
                Files.deleteIfExists(frag); // 删片段标记 → 重跑时该页会重新 OCR
            } catch (IOException e) {
                log.warn("[ai-scan] 删除失败页片段失败：{}（{}）", frag, e.getMessage());
            }
        }
        // 回写 progress.json：清空失败页（原子替换）
        rewriteFailedPages(dir, List.of());

        ScanOcrRetryVO retry = new ScanOcrRetryVO();
        retry.setStdKey(stdKey.trim());
        retry.setPendingPages(pending);
        retry.setCommandToRun("python ai/scripts/prepare-standards.py --ocr --workers 4");
        retry.setNote("OCR 为离线长任务，请在上图终端执行该命令；本系统不代替运行。"
                + "已存在片段页会自动跳过（断点续跑），仅补跑缺失/失败页。");
        return retry;
    }

    // =====================================================================
    // 内部
    // =====================================================================

    private Optional<ScanOcrJobVO> readProgress(Path dir) {
        Path file = dir.resolve("progress.json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readString(file));
            ScanOcrJobVO vo = new ScanOcrJobVO();
            vo.setStdKey(text(root, "stdKey", dir.getFileName().toString()));
            vo.setSourceFile(text(root, "sourceFile", null));
            vo.setStdNo(text(root, "stdNo", null));
            vo.setMode(text(root, "mode", "ocr"));
            int total = root.path("totalPages").asInt(0);
            int done = root.path("donePages").asInt(0);
            vo.setTotalPages(total);
            vo.setDonePages(done);
            vo.setStatus(text(root, "status", "pending"));
            vo.setOutputTxt(text(root, "outputTxt", null));
            vo.setUpdatedAt(text(root, "updatedAt", null));
            vo.setPercent(total > 0 ? Math.round(done * 1000.0 / total) / 10.0 : 0.0);
            List<ScanOcrJobVO.FailedPage> fails = new ArrayList<>();
            for (JsonNode n : root.path("failedPages")) {
                fails.add(new ScanOcrJobVO.FailedPage(n.path("page").asInt(0), n.path("reason").asText("")));
            }
            vo.setFailedPages(fails);
            return Optional.of(vo);
        } catch (Exception e) {
            log.warn("[ai-scan] progress.json 解析失败：{}（{}）", file, e.getMessage());
            return Optional.empty();
        }
    }

    private void rewriteFailedPages(Path dir, List<?> failed) {
        Path file = dir.resolve("progress.json");
        try {
            ObjectNode root = (ObjectNode) objectMapper.readTree(Files.readString(file));
            root.putArray("failedPages");
            Path tmp = dir.resolve("progress.json.tmp");
            Files.writeString(tmp, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("[ai-scan] 回写 progress.json 失败：{}（{}）", file, e.getMessage());
        }
    }

    private String text(JsonNode root, String field, String def) {
        JsonNode n = root.path(field);
        return n.isMissingNode() || n.isNull() || n.asText().isEmpty() ? def : n.asText();
    }
}
