package com.lims.service.ai;

import com.lims.common.exception.BizException;
import com.lims.common.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * GB 标准文件仓库（feature A，T03）—— 目录解析 / 枚举 / 落盘 / 校验和。
 *
 * <p>把「文件系统那一摊」从业务服务里抽出来，让 {@code GbIndexServiceImpl} 与
 * {@code GbIndexWorker} 共用，避免二者互相依赖形成环。</p>
 *
 * <p><b>路径解析</b>：配置的 {@code standards-dir} 是相对路径（{@code ai/standards}），
 * 而后端既可能在**仓库根**启动、也可能在 {@code backend/} 下启动。故解析时从进程 CWD 起
 * **逐级上溯**查找第一个存在的目录，找不到再回退为「相对 CWD 的绝对化路径」——
 * 让「仓库整体搬走即可用」成立，而不必强制固定启动目录。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GbStandardsStore {

    /** 可直接导入的扩展名（PDF 不在此列，见 {@link #isPdf}）。 */
    private static final Set<String> SUPPORTED = Set.of("txt", "md", "markdown", "html", "htm", "csv", "tsv");

    private final AiProperties props;

    /** 一个待导入文件的内存视图。 */
    public record StoredFile(String fileName, Path path, byte[] bytes, String ext, int sourceType) {
    }

    /**
     * PDF 轻量诊断结论（feature 增量 ai_flow_assistant，设计 §2.2）。
     *
     * <p><b>启发式边界（务必保留）</b>：{@code /Font} 探测只能把用户引向正确的命令行参数，
     * **不作为准入判定**——少数混合型 PDF（少量文字层 + 大量扫描页）可能误判；
     * 故两条文案都指向 {@code prepare-standards.py}（脚本内部逐页决定走文本还是 OCR）。</p>
     */
    public enum PdfDiagnosis {
        /** 含 /Font → 文本版 PDF（有文字层） */
        TEXT_PDF,
        /** 不含 /Font → 扫描版 PDF（无文字层） */
        SCANNED_PDF,
        /** 诊断失败/无法判定 → 回落通用文案 */
        UNKNOWN
    }

    /**
     * 零依赖轻量诊断 PDF 类型：在 PDF 字节中探测是否含 {@code /Font} 对象。
     *
     * <p>只做字节级扫描，不引 PDFBox/Tika（离线仓无）。刻意不在探测失败时放行。</p>
     */
    public PdfDiagnosis diagnosePdf(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return PdfDiagnosis.UNKNOWN;
        }
        byte[] needle = {'/', 'F', 'o', 'n', 't'};
        return contains(bytes, needle) ? PdfDiagnosis.TEXT_PDF : PdfDiagnosis.SCANNED_PDF;
    }

    /** 朴素子串搜索（bytes 已读入内存，无需额外依赖）。 */
    private boolean contains(byte[] haystack, byte[] needle) {
        int limit = haystack.length - needle.length;
        for (int i = 0; i <= limit; i++) {
            if (haystack[i] != needle[0]) {
                continue;
            }
            int j = 1;
            while (j < needle.length && haystack[i + j] == needle[j]) {
                j++;
            }
            if (j == needle.length) {
                return true;
            }
        }
        return false;
    }

    /** 是否为 PDF（后端一律 4211 拒收，fail-loud）。 */
    public static boolean isPdf(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }

    /** 扩展名是否受支持（后端本身可解析）。 */
    public boolean isSupported(String ext) {
        return ext != null && SUPPORTED.contains(ext.toLowerCase());
    }

    /**
     * 解析目录：相对路径从 CWD 逐级上溯查找。
     *
     * @param dir 显式目录（可空 → 取 {@code standards-dir/parsed}）
     */
    public Path resolveDir(String dir) {
        String d = StringUtils.hasText(dir) ? dir.trim() : (props.getStandardsDir() + "/parsed");
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

    /** 枚举目录下（或单文件）可导入的文件，按文件名排序。 */
    public List<Path> listImportable(String dirOrFile) {
        Path p = resolveDir(dirOrFile);
        if (Files.isRegularFile(p)) {
            return isSupported(extOf(p.getFileName().toString())) ? List.of(p) : List.of();
        }
        if (!Files.isDirectory(p)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(p)) {
            List<Path> out = new ArrayList<>();
            s.filter(Files::isRegularFile)
                    .filter(f -> isSupported(extOf(f.getFileName().toString())))
                    .sorted(Comparator.comparing(f -> f.getFileName().toString()))
                    .forEach(out::add);
            return out;
        } catch (IOException e) {
            throw new BizException(400, "目录读取失败：" + p + "（" + e.getMessage() + "）");
        }
    }

    /** 读取文件为内存视图。 */
    public StoredFile load(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            String name = path.getFileName().toString();
            return new StoredFile(name, path, bytes, extOf(name), sourceTypeOf(extOf(name)));
        } catch (IOException e) {
            throw new BizException(400, "文件读取失败：" + path + "（" + e.getMessage() + "）");
        }
    }

    /**
     * 保存上传文件到下盘（{@code standards-dir/parsed/}）。
     *
     * @return 落盘后的 {@link StoredFile}
     */
    public StoredFile saveUpload(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (!StringUtils.hasText(original)) {
            throw new BizException(400, "上传文件名为空");
        }
        // 只取文件名部分，防目录穿越
        String safeName = Path.of(original).getFileName().toString();
        String ext = extOf(safeName);
        if (isPdf(safeName)) {
            // PDF 仍**拒收**（解析成本约束未变，设计 §2.2），但给出**可操作分流**：
            // 文本版 → 4211（转文本）；扫描版 → 4212（跑 OCR 通道）。诊断失败回落 4211。
            throw new BizException(diagnoseUpload(file) == PdfDiagnosis.SCANNED_PDF
                    ? ResultCode.AI_PDF_SCANNED : ResultCode.AI_PDF_NOT_SUPPORTED);
        }
        if (!isSupported(ext)) {
            throw new BizException(400, "不支持的文件类型：." + ext + "（仅支持 txt/html/htm/md/csv/tsv）");
        }
        Path dir = resolveDir(null);
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(safeName);
            try (var in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            byte[] bytes = Files.readAllBytes(target);
            return new StoredFile(safeName, target, bytes, ext, sourceTypeOf(ext));
        } catch (IOException e) {
            throw new BizException(500, "上传文件保存失败：" + e.getMessage());
        }
    }

    /** 读上传字节做 PDF 诊断；读流异常回落 UNKNOWN（→ 通用 4211 文案，fail-loud 不放行）。 */
    private PdfDiagnosis diagnoseUpload(MultipartFile file) {
        try {
            return diagnosePdf(file.getBytes());
        } catch (IOException e) {
            log.warn("[ai-kb] PDF 诊断失败，回落通用文案：{}", e.getMessage());
            return PdfDiagnosis.UNKNOWN;
        }
    }

    /** 文件内容 sha256（幂等导入键）。 */
    public String sha256(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(500, "SHA-256 不可用");
        }
    }

    /** 扩展名 → 来源类型（1=TXT 2=HTML 3=MD 4=CSV）。 */
    public static int sourceTypeOf(String ext) {
        if (ext == null) {
            return 1;
        }
        return switch (ext.toLowerCase()) {
            case "html", "htm" -> 2;
            case "md", "markdown" -> 3;
            case "csv", "tsv" -> 4;
            default -> 1;
        };
    }

    private static String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
