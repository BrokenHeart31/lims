#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""GB 标准 PDF → 纯文本 预处理脚本（LIMS 本地 AI 助手，feature A / T04；增量 ai_flow_assistant）。

背景与设计取舍（见 docs/design/2026-09-17-arch-ai-assistant-and-rollback.md §2.10 /
docs/design/2026-09-18-arch-ai-flow-assistant.md §2.1~§2.4）：
  本系统的 GB 标准知识库**只接收纯文本**（txt/html/htm/md/csv），**不接收 PDF**——
  原因：① PDF 解析进 Java 需引入额外依赖（违背「零新增依赖」约定）；② Python 侧的
  pypdf 成熟且可离线安装，把「格式转换」这件一次性预处理放到仓库内脚本做，最省耦合。
  故：PDF 必须先经本脚本转成 `.txt`，再投入 `ai/standards/parsed/`，最后调后端建索引。

两条通道（**逐页自动分流**）：
  · **文本版 PDF**（有文字层）→ pypdf 抽取文字 → `parsed/<stem>.txt`（**首选**：无 OCR 误差）；
  · **扫描版 PDF**（无 /Font、无 ToUnicode、文字层为零）→ 逐页 OCR（rapidocr-onnxruntime）
    → `parsed/<stem>.ocr.txt`（**现实兜底**，约 8~10 秒/页）。

⚠️ OCR 用途边界（铁律）：OCR 文本**只用于条文引用与定位**；**数值权威永远是系统标准库
   `product_lib_item`**。故本脚本**不做任何数字纠错**，原文照录。

流程：
  ai/standards/inbox/*.pdf  --(本脚本)-->  ai/standards/parsed/*.txt
  扫描件 OCR 的逐页产物/进度落在侧车位 ai/standards/.scan/<stdKey>/（支持断点续跑）。

用法：
  python ai/scripts/prepare-standards.py                      # 默认扫描 inbox → parsed（文本版）
  python ai/scripts/prepare-standards.py --ocr                # 扫描件：逐页 OCR（默认 4 进程）
  python ai/scripts/prepare-standards.py --ocr --workers 2    # 指定并发（上限 8）
  python ai/scripts/prepare-standards.py --ocr --limit 20     # 仅前 20 页（校准计时用）
  python ai/scripts/prepare-standards.py --inbox X --out Y --force

依赖（ai/scripts/requirements.txt）：
  pip install -r ai/scripts/requirements.txt

退出码：0 成功；1 依赖缺失；2 输入目录不存在；3 至少一个文件转换失败（fail-loud）。
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

# 仓库根 = 本文件的 ../../（ai/scripts/prepare-standards.py → <repo>）
REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_INBOX = REPO_ROOT / "ai" / "standards" / "inbox"
DEFAULT_OUT = REPO_ROOT / "ai" / "standards" / "parsed"
DEFAULT_SCAN_ROOT = REPO_ROOT / "ai" / "standards" / ".scan"

# 直接可用的纯文本扩展名：无需转换，原样复制（已投放的 txt/md/html 不必再处理）
TEXT_EXTENSIONS = {".txt", ".md", ".html", ".htm", ".csv"}

# 一页文字层字符数低于此值视为「无文字层」，提示扫描件（与 ocr_engine.MIN_TEXT_CHARS 口径一致）
SCANNED_PAGE_THRESHOLD = 20


def get_pypdf():
    """惰性导入 pypdf；缺失时给出可操作指引（而不是抛裸 ImportError）。"""
    try:
        from pypdf import PdfReader  # type: ignore
        return PdfReader
    except ImportError:
        print(
            "[prepare-standards] 缺少依赖 pypdf。请先执行：\n"
            "    pip install -r ai/scripts/requirements.txt\n"
            "（离线环境：在有网机器 pip download 后拷贝 whl 再 pip install）",
            file=sys.stderr,
        )
        raise SystemExit(1)


def pdf_to_text(pdf_path: Path) -> str:
    """抽取 PDF 全文，保留「分页标记」便于回溯命中位置（审计可读性）。"""
    PdfReader = get_pypdf()
    reader = PdfReader(str(pdf_path))
    parts: list[str] = []
    # 首部信息头：让下游切块/检索能知道来源文件（GB 号通常就在文件名里）
    parts.append(f"# source: {pdf_path.name}")
    for i, page in enumerate(reader.pages, start=1):
        # extract_text 对扫描件（图片型 PDF）会返回空串——此处如实标记，不假装有内容
        text = page.extract_text() or ""
        parts.append(f"\n===== page {i} =====\n{text.strip()}")
    return "\n".join(parts).strip() + "\n"


def probe_text_layer(pdf_path: Path, sample_pages: int = 3) -> tuple[int, int]:
    """抽样探测 PDF 是否含文字层，返回 (抽到的非空页数, 抽样页数)。

    用于在无 `--ocr` 时给出「这是扫描件」的可操作提示（C-10）。
    """
    PdfReader = get_pypdf()
    reader = PdfReader(str(pdf_path))
    total = len(reader.pages)
    if total == 0:
        return 0, 0
    # 均匀抽样（首页封面 / 中部 / 末页各一）
    idxs = sorted({1, max(1, total // 2), total, *range(1, min(sample_pages, total) + 1)})
    nonempty = 0
    for i in idxs:
        text = reader.pages[i - 1].extract_text() or ""
        if len(text.strip()) >= SCANNED_PAGE_THRESHOLD:
            nonempty += 1
    return nonempty, len(idxs)


def run_ocr(pdf_path: Path, out_dir: Path, scan_root: Path, workers: int,
            limit: int, force: bool) -> int:
    """走 OCR 通道处理单个 PDF；返回退出码（0 成功 / 3 失败）。"""
    # 惰性导入，避免未装 rapidocr 时普通通道也报错
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    try:
        import ocr_engine  # type: ignore
    except ImportError as exc:
        print(f"[prepare-standards] OCR 模块不可用：{exc}", file=sys.stderr)
        return 3
    try:
        result = ocr_engine.process_pdf(
            pdf_path=pdf_path,
            scan_root=scan_root,
            out_dir=out_dir,
            workers=workers,
            force=force,
            limit=limit,
        )
    except SystemExit:
        raise
    except Exception as exc:  # noqa: BLE001 —— 单文件失败计数，不中断整批
        print(f"[prepare-standards] OCR 失败：{pdf_path.name}（{exc}）", file=sys.stderr)
        return 3
    if result["status"] == "partial_failed" and result["failedPages"]:
        print(f"[prepare-standards] ⚠️ {pdf_path.name} 部分页失败（见上方清单），产物已生成，可断点续跑。")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="GB 标准 PDF → TXT 预处理（LIMS 本地 AI 助手）")
    parser.add_argument("--inbox", default=str(DEFAULT_INBOX), help="输入目录（默认 ai/standards/inbox）")
    parser.add_argument("--out", default=str(DEFAULT_OUT), help="输出目录（默认 ai/standards/parsed）")
    parser.add_argument("--scan-root", default=str(DEFAULT_SCAN_ROOT),
                        help="OCR 侧车位根目录（默认 ai/standards/.scan）")
    parser.add_argument("--force", action="store_true", help="覆盖已存在的产物 / 片段")
    parser.add_argument("--ocr", action="store_true",
                        help="扫描件通道：对无文字层 PDF 逐页 OCR（约 8~10 秒/页）")
    parser.add_argument("--workers", type=int, default=4,
                        help="OCR 并发进程数（默认 4，硬上限 8）")
    parser.add_argument("--limit", type=int, default=0,
                        help="仅处理 PDF 前 N 页（0=全部；用于校准计时）")
    args = parser.parse_args()

    inbox = Path(args.inbox).resolve()
    out_dir = Path(args.out).resolve()
    scan_root = Path(args.scan_root).resolve()

    if not inbox.is_dir():
        print(f"[prepare-standards] 输入目录不存在：{inbox}", file=sys.stderr)
        return 2

    out_dir.mkdir(parents=True, exist_ok=True)

    sources = sorted([p for p in inbox.iterdir() if p.is_file() and p.name != ".gitkeep"])
    if not sources:
        print(f"[prepare-standards] 输入目录为空：{inbox}（请先投放标准文件）")
        return 0

    converted = 0
    copied = 0
    skipped = 0
    failed = 0

    for src in sources:
        ext = src.suffix.lower()

        try:
            if ext == ".pdf":
                if args.ocr:
                    # OCR 通道：产物名固定为 <stem>.ocr.txt（与文本版 <stem>.txt 区分）。
                    # ⚠️ 断点续跑在**片段级**（.scan/<stdKey>/page-XXXX.txt），故这里**不能**
                    #    因「产物已存在」而整体跳过——否则半成品会被误判为完成。process_pdf 内部
                    #    会跳过已完成片段、只补缺失/失败页。
                    if run_ocr(src, out_dir, scan_root, args.workers, args.limit, args.force) == 0:
                        converted += 1
                    else:
                        failed += 1
                else:
                    target = out_dir / (src.stem + ".txt")
                    if target.exists() and not args.force:
                        print(f"[prepare-standards] 跳过（已存在，--force 可覆盖）：{target.name}")
                        skipped += 1
                        continue
                    text = pdf_to_text(src)
                    target.write_text(text, encoding="utf-8")
                    converted += 1
                    print(f"[prepare-standards] PDF → TXT：{src.name} → {target.name}（{len(text)} 字符）")
                    # C-10：若抽样显示无文字层，主动提示「扫描件请走 OCR / 换文本版」
                    nonempty, sampled = probe_text_layer(src)
                    if sampled and nonempty == 0:
                        print(
                            f"[prepare-standards] ⚠️ 抽样 {sampled} 页文字层为空 —— 这很可能是**扫描版 PDF**。\n"
                            f"    产物可能是空的。更优路径：换一份**文本版 PDF**（无识别误差）。\n"
                            f"    若无文本版，请改走 OCR 通道：\n"
                            f"      python ai/scripts/prepare-standards.py --ocr --workers 4"
                        )
            elif ext in TEXT_EXTENSIONS:
                target = out_dir / (src.stem + ".txt")
                if target.exists() and not args.force:
                    print(f"[prepare-standards] 跳过（已存在，--force 可覆盖）：{target.name}")
                    skipped += 1
                    continue
                # 纯文本类：原样拷贝为 .txt（统一下游扩展名，避免遗漏）
                target.write_text(src.read_text(encoding="utf-8", errors="replace"), encoding="utf-8")
                copied += 1
                print(f"[prepare-standards] 复制文本：{src.name} → {target.name}")
            else:
                print(f"[prepare-standards] 跳过不支持的类型：{src.name}")
                skipped += 1
        except SystemExit:
            raise
        except Exception as exc:  # noqa: BLE001 —— 单个文件失败不应中断整批（但要如实计数）
            failed += 1
            print(f"[prepare-standards] 失败：{src.name}（{exc}）", file=sys.stderr)

    print(
        f"\n[prepare-standards] 完成：处理 {converted}、复制 {copied}、跳过 {skipped}、失败 {failed}\n"
        f"[prepare-standards] 产物目录：{out_dir}\n"
        f"[prepare-standards] 下一步：powershell -ExecutionPolicy Bypass -File ai/scripts/import-standards.ps1"
    )
    if failed > 0:
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
