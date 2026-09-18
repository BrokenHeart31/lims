#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""扫描件 PDF 逐页 OCR 引擎（增量 ai_flow_assistant / T04，设计 §2.1 / §2.4）。

背景与用途边界（**铁律，必须遵守**）：
  · 本模块只服务「**扫描版 PDF 无文字层**」这一现实：pypdf 对扫描件 `extract_text()` 返回 0 字符
    （页面无 /Font、无 ToUnicode），物理上抽不到文本，必须走 OCR。
  · **OCR 文本只用于「条文引用与定位」**（条款号 / 标题 / 正文表述 / 适用食品类别）。
    数值权威**永远是** `product_lib_item`（系统标准库）。OCR 会把「最大」认成「最人」、
    `0.05` 认成 `0. 05`，抄错即资质事故 —— 故本模块**不做任何数字纠错**（设计 §2.3 排除项 ③），
    原文照录，交由上层以「来源标签 + 数值不取 OCR」约束消费。

形态（设计 §2.4）：
  · **逐页 checkpoint（断点续跑）**：每页 OCR 成功后立即写片段文件
    `ai/standards/.scan/<stdKey>/page-0001.txt`；重跑时已存在片段页直接跳过，只补缺失/失败页。
  · **片段文件 + 收尾拼接**（而非追加单个 txt）：片段天然幂等（存在即完成）、天然可并发
    （每页独立文件）、收尾按页号排序拼接即得正确页序。
  · **页级进度（fail-loud）**：控制台逐页/每 N 页打印 `[ocr] 130/422 页（30.8%）… 失败 1 页`；
    同时原子写 `progress.json`；失败页**必须逐页列出理由**，绝不静默丢弃。
  · **并发粒度**：默认 `workers=4`，`--workers N` 可调，**硬上限 8**（给同机 Ollama 留核）。

入参约束（实测坑，务必保留）：
  · `rapidocr-onnxruntime` 的 `RapidOCR()(img)` 接受 `str / bytes / Path / np.ndarray`，
    **不接受 `PIL.Image`**（会把 `JpegImageFile` 判为类型错误）。故本模块**直接喂页内嵌 JPEG 的 bytes**。
  · 页内嵌 JPEG 用 `pypdf` 的 `page.images[i].data` 取原始字节，不 decode 成 PIL 再传。
"""

from __future__ import annotations

import concurrent.futures as _futures
import hashlib
import json
import multiprocessing as _mp
import os
import os
import re
import sys
import time
from functools import partial
from pathlib import Path

# ---------------------------------------------------------------------------
# 常量
# ---------------------------------------------------------------------------

#: 并发硬上限（护栏，写死；给同机 Ollama 与系统留核，设计 §2.4）
MAX_WORKERS = 8
#: 默认并发数（本机 32 核：4 worker OCR ≈ 15~20min，同时留 24 核给 Ollama）
DEFAULT_WORKERS = 4
#: 一页 `extract_text()` 字符数低于此值即视为「无文字层」→ 走 OCR
MIN_TEXT_CHARS = 20

#: 顶层预留核数（给同机 Ollama 推理与系统）。OCR 是离线批处理，不该独吞整机。
RESERVED_CORES = 4
#: 单个 OCR 进程允许使用的最大线程数（钉线程用，见 `_pin_threads`）
MAX_THREADS_PER_WORKER = 8
#: `_pin_threads` 实际设定的「每 worker 线程数」（0 = 尚未设定）
_THREADS_PER_WORKER = 0

#: 标准号识别（GB 2763-2021 / NY/T 761-2008 / GB/T 5009.19-2008 等）
_STD_KEY_RE = re.compile(
    r"(GB|NY|SN|SB|HJ|DB|JJF|JJG)\s*/?\s*(T|Z)?\s*(\d{2,6})(?:[.．\-](\d{2,4}))?",
    re.IGNORECASE,
)

#: 片段文件命名
_FRAG_FMT = "page-{page:04d}.txt"


# ---------------------------------------------------------------------------
# 标准号归一化（stdKey / stdNo）
# ---------------------------------------------------------------------------

def normalize_std_key(name: str) -> str:
    """把文件名/正文里的标准号归一化为 stdKey（如 `GB-2763-2021`）。

    规则：`<PREFIX>[-[T|Z]]-<NUM>[-<YEAR>]`，全大写、段间用 `-`。
    未识别到标准号时用**文件名 stem** 兜底（仍保证目录名稳定、可复用）。
    """
    if not name:
        return "UNKNOWN"
    m = _STD_KEY_RE.search(name)
    if not m:
        # 兜底：文件名 stem 清洗非法字符
        return re.sub(r"[^0-9A-Za-z\u4e00-\u9fff_-]", "_", Path(name).stem)[:80] or "UNKNOWN"
    prefix = m.group(1).upper()
    sub = m.group(2).upper() if m.group(2) else ""
    num = m.group(3)
    year = m.group(4)
    parts = [prefix]
    if sub:
        parts.append(sub)
    parts.append(num)
    if year:
        parts.append(year)
    return "-".join(parts)


def std_no_from_key(std_key: str) -> str:
    """把 stdKey（`GB-2763-2021`）还原为人类可读标准号（`GB 2763-2021`）。"""
    if not std_key:
        return ""
    parts = std_key.split("-")
    if len(parts) >= 3 and parts[1] in ("T", "Z"):
        return f"{parts[0]}/{parts[1]} {parts[2]}" + (f"-{parts[3]}" if len(parts) > 3 else "")
    if len(parts) >= 2:
        return f"{parts[0]} {parts[1]}" + (f"-{parts[2]}" if len(parts) > 2 else "")
    return std_key


# ---------------------------------------------------------------------------
# 原子写（progress.json / 片段文件）
# ---------------------------------------------------------------------------

def atomic_write_text(path: Path, text: str) -> None:
    """写文件用「临时文件 + os.replace」原子替换，避免后端读到半截内容。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(text, encoding="utf-8")
    os.replace(tmp, path)


def atomic_write_json(path: Path, obj: dict) -> None:
    """写 JSON 用原子替换。"""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, path)


def fingerprint_file(path: Path) -> str:
    """文件内容 sha256（前 16 位 + 前缀，够去重/校验）。"""
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return "sha256:" + h.hexdigest()


# ---------------------------------------------------------------------------
# OCR 引擎（惰性加载，每进程一份）
# ---------------------------------------------------------------------------

_ENGINE = None


def _pin_threads(workers: int) -> int:
    """把「每 worker 的线程数」钉住，返回本次设定的值。

    **为什么必须钉**：`onnxruntime` 的 intra-op 线程数与 OpenMP 线程数**默认取逻辑核数**。
    本机 32 核时 `workers=4` 会让 4×32 = **128 个线程抢 32 个核**，实测结果是
    **总吞吐反而低于单进程**（4 并发 5.29 页/分钟 vs 单进程 7.14 页/分钟），
    条件更差时（同机还在跑 `mvn test`）会进入**活锁**：CPU 满速占用但零产出
    （实测 162 页后停滞 20+ 分钟，每 worker 占约 12 个核）。

    修法：令 `workers × 每-worker 线程数 ≲ (核数 − RESERVED_CORES)`。

    ⚠️ **必须在 `import onnxruntime` / 构造 `RapidOCR` 之前调用** ——
    这些库在**导入期**就确定线程池，事后设置无效。
    多进程（Windows 默认 spawn）下父进程的 `os.environ` 不保证被 worker 继承，
    故本函数在 **worker 的初始化函数**里也会被显式调用一次。
    """
    global _THREADS_PER_WORKER
    if _THREADS_PER_WORKER:
        return _THREADS_PER_WORKER
    cpu = os.cpu_count() or 4
    budget = max(1, cpu - RESERVED_CORES)
    threads = max(1, min(MAX_THREADS_PER_WORKER, budget // max(1, workers)))
    for key in (
        "OMP_NUM_THREADS",
        "OPENBLAS_NUM_THREADS",
        "MKL_NUM_THREADS",
        "NUMEXPR_NUM_THREADS",
        "VECLIB_MAXIMUM_THREADS",
    ):
        os.environ[key] = str(threads)
    _THREADS_PER_WORKER = threads
    return threads


def _load_engine():
    """惰性加载 RapidOCR；缺失时给出可操作指引（fail-loud）。"""
    global _ENGINE
    if _ENGINE is not None:
        return _ENGINE
    # 兜底：任何进入本函数的路径（含单进程内直接调用）都先钉线程
    _pin_threads(max(1, DEFAULT_WORKERS))
    try:
        from rapidocr_onnxruntime import RapidOCR  # type: ignore
    except ImportError as exc:  # pragma: no cover - 依赖缺失路径
        print(
            "[ocr] 缺少依赖 rapidocr-onnxruntime。请先执行：\n"
            "    pip install -r ai/scripts/requirements.txt\n"
            "（离线环境：在有网机器 pip download 后拷贝 whl 再 pip install）",
            file=sys.stderr,
        )
        raise SystemExit(1) from exc
    _ENGINE = RapidOCR()
    return _ENGINE


def ocr_image_bytes(data: bytes) -> list[str]:
    """对单张图片字节做 OCR，返回文本行列表。

    ⚠️ 直接喂 `bytes`（不接受 PIL.Image）。
    """
    engine = _load_engine()
    result, _elapse = engine(data)
    lines: list[str] = []
    if not result:
        return lines
    for item in result:
        # RapidOCR 结果为 [box, text, score]
        if isinstance(item, (list, tuple)) and len(item) >= 2 and isinstance(item[1], str):
            txt = item[1].strip()
            if txt:
                lines.append(txt)
    return lines


def _init_worker(workers: int = DEFAULT_WORKERS) -> None:
    """子进程初始化：**先钉线程数，再**预加载 OCR 引擎（避免每页重复初始化）。

    ⚠️ 顺序不可颠倒：`_pin_threads` 必须在 `_load_engine()`（其内部会 import
    onnxruntime）**之前**执行，否则线程池已按逻辑核数建好，钉不住。
    """
    threads = _pin_threads(workers)
    # 子进程里没有 process_pdf 的局部 log，只能用 print（保持可见性，便于排查并发问题）
    print(f"[ocr]   worker 就绪（每进程线程数={threads}，并发={workers}）", flush=True)
    _load_engine()


def _ocr_page_worker(args: tuple[int, list[bytes]]) -> tuple[int, str, str | None]:
    """子进程任务：OCR 一页（可能含多张图片，全部识别后按顺序拼接）。

    @return (page_no, text, error_reason)；error_reason 非空表示该页失败。
    """
    page_no, images = args
    try:
        lines: list[str] = []
        for img in images:
            lines.extend(ocr_image_bytes(img))
        return page_no, "\n".join(lines), None
    except Exception as exc:  # noqa: BLE001 —— 单页失败不应中断整批
        return page_no, "", f"{type(exc).__name__}: {exc}"


# ---------------------------------------------------------------------------
# 侧车位（.scan/<stdKey>/）
# ---------------------------------------------------------------------------

class ScanStore:
    """`.scan/<stdKey>/` 侧车位：片段文件 + progress.json（文件即接口）。"""

    def __init__(self, scan_root: Path, std_key: str):
        self.dir = Path(scan_root) / std_key
        self.dir.mkdir(parents=True, exist_ok=True)
        self.progress_path = self.dir / "progress.json"

    def fragment_path(self, page_no: int) -> Path:
        return self.dir / _FRAG_FMT.format(page=page_no)

    def has_fragment(self, page_no: int) -> bool:
        return self.fragment_path(page_no).exists()

    def write_fragment(self, page_no: int, text: str) -> None:
        atomic_write_text(self.fragment_path(page_no), text)

    def count_fragments(self) -> int:
        return sum(1 for _ in self.dir.glob("page-*.txt"))

    def read_fragments_ordered(self, total_pages: int) -> list[tuple[int, str]]:
        """按页号升序读取全部片段（缺失页跳过）。"""
        out: list[tuple[int, str]] = []
        for p in range(1, total_pages + 1):
            fp = self.fragment_path(p)
            if fp.exists():
                out.append((p, fp.read_text(encoding="utf-8", errors="replace")))
        return out


# ---------------------------------------------------------------------------
# 主流程
# ---------------------------------------------------------------------------

def _extract_text_layer(page) -> str:
    """尽力抽取页面文字层；失败返回空串（不抛）。"""
    try:
        return (page.extract_text() or "").strip()
    except Exception:  # noqa: BLE001
        return ""


def _extract_page_images(page) -> list[bytes]:
    """抽取页内嵌图片的**原始字节**（直接喂 OCR，不 decode 成 PIL）。"""
    out: list[bytes] = []
    try:
        images = page.images
    except Exception:  # noqa: BLE001
        return out
    for im in images:
        try:
            data = im.data  # pypdf ImageFile.data → bytes
        except Exception:  # noqa: BLE001
            data = None
        if data:
            out.append(bytes(data))
    return out


def process_pdf(
    pdf_path: Path,
    scan_root: Path,
    out_dir: Path,
    workers: int = DEFAULT_WORKERS,
    force: bool = False,
    limit: int = 0,
    quiet: bool = False,
) -> dict:
    """对单个 PDF 执行「探测文字层 → 逐页 OCR/文本抽取 → 收尾拼接」。

    @param pdf_path  待处理 PDF
    @param scan_root 侧车位根目录（`ai/standards/.scan`）
    @param out_dir   产物目录（`ai/standards/parsed`，输出 `<stem>.ocr.txt`）
    @param workers   并发进程数（1..MAX_WORKERS，超出即钳制）
    @param force     忽略已有片段，全部重跑
    @param limit     仅处理前 N 页（用于校准计时，0=全部）
    @param quiet     是否精简控制台输出
    @return 结果摘要 dict
    """
    from pypdf import PdfReader  # 惰性导入，缺依赖时给出上层可读错误

    workers = max(1, min(int(workers), MAX_WORKERS))
    threads = _pin_threads(workers)
    # 注意：此处必须用 print，不能用下面第 ~370 行才定义的局部 log（否则 UnboundLocalError）
    if not quiet:
        print(
            f"[ocr] 并发策略：{workers} 进程 × 每进程 {threads} 线程"
            f"（合计约 {workers * threads} 线程；本机核数 {os.cpu_count()}，预留 {RESERVED_CORES}）"
            f" —— 钉线程是必须的，否则线程超订会导致活锁（见 _pin_threads 注释）",
            flush=True,
        )
    pdf_path = Path(pdf_path)
    std_key = normalize_std_key(pdf_path.stem)
    std_no = std_no_from_key(std_key)
    store = ScanStore(scan_root, std_key)

    reader = PdfReader(str(pdf_path))
    total_pages = len(reader.pages)
    scan_pages = total_pages if limit <= 0 else min(limit, total_pages)

    fp = fingerprint_file(pdf_path)
    progress = {
        "stdKey": std_key,
        "sourceFile": pdf_path.name,
        "stdNo": std_no,
        "mode": "ocr",
        "fingerprint": fp,
        "totalPages": total_pages,
        "donePages": store.count_fragments(),
        "failedPages": [],
        "outputTxt": (pdf_path.stem + ".ocr.txt"),
        "status": "running",
        "startedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
        "updatedAt": time.strftime("%Y-%m-%d %H:%M:%S"),
    }

    def _flush() -> None:
        progress["donePages"] = store.count_fragments()
        progress["updatedAt"] = time.strftime("%Y-%m-%d %H:%M:%S")
        atomic_write_json(store.progress_path, progress)

    _flush()
    log = (lambda *a: None) if quiet else (lambda *a: print(*a, flush=True))

    # --- 阶段一：逐页探测文字层；无文字层的页收集图片字节待 OCR -------------
    pending: list[tuple[int, list[bytes]]] = []
    finished = 0
    failed: list[dict] = []
    text_pages = 0

    for idx in range(1, scan_pages + 1):
        if (not force) and store.has_fragment(idx):
            finished += 1
            continue
        page = reader.pages[idx - 1]
        text = _extract_text_layer(page)
        if len(text) >= MIN_TEXT_CHARS:
            # 有文字层 → 直接文本抽取，不做 OCR（更准、更快，设计 §2.2）
            store.write_fragment(idx, text)
            text_pages += 1
            finished += 1
            continue
        images = _extract_page_images(page)
        if not images:
            failed.append({"page": idx, "reason": "页内无图像对象（疑似空页或矢量页）"})
            store.write_fragment(idx, "")  # 空片段占位，避免重跑反复探测
            failed[-1]["page"] = idx
            continue
        pending.append((idx, images))

    _flush()
    log(
        f"[ocr] {std_key}：共 {total_pages} 页，本次待 OCR {len(pending)} 页"
        f"（已续跑 {finished}，文字层 {text_pages}，无图像失败 {len(failed)}）"
    )

    # --- 阶段二：并发 OCR（ProcessPoolExecutor，worker 预加载引擎） ----------
    done = finished
    failed_pages = list(failed)
    t0 = time.time()
    if pending:
        ctx = _mp.get_context("spawn")
        with _futures.ProcessPoolExecutor(
            max_workers=workers, mp_context=ctx, initializer=partial(_init_worker, workers)
        ) as pool:
            futures = {pool.submit(_ocr_page_worker, task): task[0] for task in pending}
            for i, fut in enumerate(_futures.as_completed(futures), start=1):
                page_no, text, err = fut.result()
                if err:
                    failed_pages.append({"page": page_no, "reason": err})
                    log(f"[ocr]   页 {page_no} 失败：{err}")
                else:
                    store.write_fragment(page_no, text)
                    done += 1
                progress["failedPages"] = failed_pages
                _flush()
                if (i % 10 == 0) or i == len(pending):
                    pct = 100.0 * (i / len(pending))
                    log(
                        f"[ocr] {i}/{len(pending)} 页（{pct:.1f}%）… 失败 {len(failed_pages)} 页"
                    )

    elapsed = time.time() - t0

    # --- 阶段三：按页号拼接 → parsed/<stem>.ocr.txt ------------------------
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_txt = out_dir / (pdf_path.stem + ".ocr.txt")
    frags = store.read_fragments_ordered(scan_pages)
    parts = [f"# source: {pdf_path.name}（OCR 通道；数值请以系统标准库为准）"]
    for page_no, text in frags:
        parts.append(f"\n===== page {page_no} =====\n{text.strip()}")
    atomic_write_text(out_txt, "\n".join(parts).strip() + "\n")

    failed_sorted = sorted(failed_pages, key=lambda d: d["page"])
    if failed_sorted:
        progress["status"] = "partial_failed" if len(failed_sorted) < scan_pages else "done"
    else:
        progress["status"] = "done"
    if failed_sorted and len(failed_sorted) >= scan_pages:
        progress["status"] = "partial_failed"
    progress["donePages"] = store.count_fragments()
    progress["failedPages"] = failed_sorted
    _flush()

    log(
        f"[ocr] {std_key} 完成：片段 {progress['donePages']}/{total_pages} 页，"
        f"失败 {len(failed_sorted)} 页，耗时 {elapsed:.1f}s → {out_txt}"
    )
    if failed_sorted:
        log(f"[ocr] 失败页清单：{failed_sorted}")
        log(f"[ocr] 断点续跑：修复后重跑本命令，已存在片段页会自动跳过。")

    return {
        "stdKey": std_key,
        "stdNo": std_no,
        "totalPages": total_pages,
        "donePages": progress["donePages"],
        "failedPages": failed_sorted,
        "outputTxt": str(out_txt),
        "status": progress["status"],
        "elapsedSec": round(elapsed, 1),
    }


if __name__ == "__main__":  # pragma: no cover - 手动运行入口（正常由 prepare-standards.py 调用）
    import argparse

    ap = argparse.ArgumentParser(description="扫描件 PDF 逐页 OCR（离线，LIMS 本地 AI 助手）")
    ap.add_argument("pdf", help="待 OCR 的 PDF 路径")
    ap.add_argument("--scan-root", default="ai/standards/.scan")
    ap.add_argument("--out", default="ai/standards/parsed")
    ap.add_argument("--workers", type=int, default=DEFAULT_WORKERS)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--force", action="store_true")
    a = ap.parse_args()
    print(process_pdf(Path(a.pdf), Path(a.scan_root), Path(a.out), a.workers, a.force, a.limit))
