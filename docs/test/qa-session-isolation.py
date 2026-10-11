# -*- coding: utf-8 -*-
"""
QA 验收：LIMS 前端「多标签页会话隔离」改造（localStorage → sessionStorage）真实浏览器验证。

验证目标：
  · 同一 browser context 下的两个 page（= 浏览器两个独立标签页，无 opener 关系）
    各自持有独立的 sessionStorage 会话；
  · 标签页 A 登录 nj001、标签页 B 登录 nj002 可并存互不覆盖；
  · 任一标签页「退出登录」不影响另一标签页；
  · 升级前写入 localStorage 的旧令牌键（lims_access_token / lims_refresh_token）在
    模块加载时被清理；
  · AI 状态探测（改造点之一，走 sessionStorage 令牌）正常工作。

运行：系统 Python 3.12（playwright + chromium 已装）
  C:/Users/Chen/AppData/Local/Programs/Python/Python312/python.exe D:/lims/docs/test/qa-session-isolation.py

入库说明：本脚本为 2026-10-11「会话令牌迁移 sessionStorage」改造的回归验证脚本（22 断言，可重复运行），
  首次运行 22/22 PASS、主理人独立复跑 22/22 PASS；截图仍输出到 D:/lims/_logs/（证据区，不入库）。
约束：只读 frontend/src，不修改任何业务代码；不重启服务；仅写 _logs/ 下截图。
"""
import json
from playwright.sync_api import sync_playwright

BASE = "http://localhost:5173"
OUT = r"D:\lims\_logs"

# 断言结果表 + 全局（含分标签页）console 错误采集
results = []
console_errors = []  # [{page, kind, text}]


def check(name, ok, detail=""):
    results.append((name, bool(ok), detail))
    print(f"[{'PASS' if ok else 'FAIL'}] {name}  {detail}")
    return bool(ok)


def attach(page, label):
    """给单个 page 挂上 console error / pageerror 监听，标注来源标签页。"""
    page.on(
        "console",
        lambda m: console_errors.append({"page": label, "kind": m.type, "text": m.text[:300]})
        if m.type == "error"
        else None,
    )
    page.on(
        "pageerror",
        lambda e: console_errors.append({"page": label, "kind": "pageerror", "text": str(e)[:300]}),
    )


def tok_of(page):
    """读取当前标签页 sessionStorage 中的 access token（可能为 None）。"""
    return page.evaluate("() => sessionStorage.getItem('lims_access_token')")


def legacy_of(page):
    """读取 localStorage 中的两个旧键。"""
    return page.evaluate(
        "() => ({a: localStorage.getItem('lims_access_token'),"
        " r: localStorage.getItem('lims_refresh_token')})"
    )


def login(page, user, pwd):
    """在指定标签页登录：填写 → 提交 → 等到跳出 /login。"""
    page.goto(BASE + "/login", wait_until="domcontentloaded")
    page.wait_for_selector('input[placeholder="用户号（工号）"]', timeout=15000)
    page.fill('input[placeholder="用户号（工号）"]', user)
    page.fill('input[placeholder="登录密码"]', pwd)
    page.click("button.login-button")
    page.wait_for_url(lambda u: "/login" not in u, timeout=20000)
    # 等待外壳（用户菜单）渲染，确保已进入受保护布局
    page.wait_for_selector("button.user-chip", timeout=15000)
    page.wait_for_timeout(800)


def logout(page, shot_after=None):
    """在指定标签页通过顶栏用户菜单执行「退出登录 → 确认退出」，等到跳回 /login。"""
    page.click("button.user-chip")
    item = page.locator(".el-dropdown-menu__item", has_text="退出登录")
    item.wait_for(state="visible", timeout=10000)
    item.click()
    # ElMessageBox 二次确认弹窗：确认按钮文本为「退出」
    confirm_btn = page.locator(".el-message-box__btns button", has_text="退出")
    confirm_btn.wait_for(state="visible", timeout=10000)
    confirm_btn.click()
    page.wait_for_url(lambda u: "/login" in u, timeout=15000)
    if shot_after:
        page.wait_for_timeout(500)
        page.screenshot(path=shot_after)


print("=== LIMS 多标签页会话隔离验证（sessionStorage）===")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True)
    # 关键：同一个 context 里的多个 page = 同一浏览器的多个独立标签页（共享 localStorage，
    # 但 sessionStorage 标签页私有）。这正是验证「会话隔离」的正确姿势。
    ctx = browser.new_context(viewport={"width": 1440, "height": 900})
    pgA = ctx.new_page()
    pgB = ctx.new_page()
    attach(pgA, "A")
    attach(pgB, "B")

    # ------------------------------------------------------------------ T1
    pgB.goto(BASE + "/", wait_until="domcontentloaded")
    pgB.wait_for_url(lambda u: "/login" in u, timeout=15000)
    pgB.wait_for_timeout(600)
    pgB.screenshot(path=OUT + r"\qa-session-T1-B-no-session.png")
    check("T1 新标签页无会话→重定向 /login", "/login" in pgB.url, f"url={pgB.url}")
    check("T1 新标签页 sessionStorage 无 token", tok_of(pgB) is None, f"tokB={tok_of(pgB)!r}")

    # ------------------------------------------------------------------ T2
    login(pgA, "nj001", "nj001")
    tokA = tok_of(pgA)
    check("T2 A 页登录 nj001→离开 /login", "/login" not in pgA.url, f"url={pgA.url}")
    check("T2 A 页持有 access token（非空）", bool(tokA), f"tokA.len={len(tokA) if tokA else 0}")

    # ------------------------------------------------------------------ R1 单标签页连刷两次仍在线
    pgA.reload(wait_until="domcontentloaded")
    pgA.wait_for_selector("button.user-chip", timeout=15000)
    pgA.wait_for_timeout(600)
    still1 = ("/login" not in pgA.url) and (tok_of(pgA) == tokA)
    pgA.reload(wait_until="domcontentloaded")
    pgA.wait_for_selector("button.user-chip", timeout=15000)
    pgA.wait_for_timeout(600)
    still2 = ("/login" not in pgA.url) and (tok_of(pgA) == tokA)
    check("R1 单标签页连续刷新 2 次仍在线且 token 不变", still1 and still2,
          f"after1={still1}, after2={still2}, url={pgA.url}")

    # ------------------------------------------------------------------ T3 旧 localStorage 键清理
    # 主动向 localStorage 写入旧键（模拟升级前残留），再整页刷新触发模块加载清理。
    pgA.evaluate(
        "() => { localStorage.setItem('lims_access_token','LEGACY_SENTINEL');"
        " localStorage.setItem('lims_refresh_token','LEGACY_R_SENTINEL'); }"
    )
    seeded = legacy_of(pgA)
    pgA.reload(wait_until="domcontentloaded")
    pgA.wait_for_selector("button.user-chip", timeout=15000)
    pgA.wait_for_timeout(600)
    after = legacy_of(pgA)
    check("T3 旧 localStorage 键已被清理（seed 后 reload → null）",
          seeded["a"] == "LEGACY_SENTINEL" and after["a"] is None and after["r"] is None,
          f"seed={seeded}, after={after}")
    check("T3 清理旧键不影响 sessionStorage 会话", tok_of(pgA) == tokA,
          f"tokA.len={len(tokA) if tokA else 0}")

    # ------------------------------------------------------------------ R2 AI 状态探测（改造点）
    probe = pgA.evaluate(
        """async () => {
             const t = sessionStorage.getItem('lims_access_token');
             const r = await fetch('/api/ai/status', { headers: { Authorization: 'Bearer ' + t } });
             let j = null; try { j = await r.json(); } catch (e) {}
             return { status: r.status, code: j ? j.code : null };
           }"""
    )
    check("R2 /api/ai/status 携带 sessionStorage 令牌返回 code==0",
          probe.get("code") == 0, f"probe={probe}")

    # ------------------------------------------------------------------ T4 B 页登录 nj002（关键·并存）
    login(pgB, "nj002", "nj002")
    tokB = tok_of(pgB)
    pgA.screenshot(path=OUT + r"\qa-session-T4-A-nj001.png")
    pgB.screenshot(path=OUT + r"\qa-session-T4-B-nj002.png")
    check("T4 B 页登录 nj002→到达 /dashboard", "/login" not in pgB.url, f"url={pgB.url}")
    check("T4 B 页持有 access token（非空）", bool(tokB), f"tokB.len={len(tokB) if tokB else 0}")
    check("T4 两标签页令牌互不相同（tokA != tokB）",
          bool(tokA) and bool(tokB) and tokA != tokB,
          f"tokA.len={len(tokA) if tokA else 0}, tokB.len={len(tokB) if tokB else 0}, equal={tokA == tokB}")

    # ------------------------------------------------------------------ T5 A 页不受 B 影响
    pgA.reload(wait_until="domcontentloaded")
    pgA.wait_for_selector("button.user-chip", timeout=15000)
    pgA.wait_for_timeout(600)
    check("T5 A 页刷新后仍不在 /login", "/login" not in pgA.url, f"url={pgA.url}")
    check("T5 A 页令牌未被 B 覆盖（仍 == tokA）", tok_of(pgA) == tokA,
          f"tokA.len={len(tokA) if tokA else 0}")

    # ------------------------------------------------------------------ T6 B 退出不影响 A（核心验收）
    logout(pgB, shot_after=OUT + r"\qa-session-T6-B-logged-out.png")
    check("T6 B 页退出后到达 /login", "/login" in pgB.url, f"url={pgB.url}")
    check("T6 B 页 sessionStorage token 已清空", tok_of(pgB) is None, f"tokB={tok_of(pgB)!r}")

    # ------------------------------------------------------------------ T7 A 仍旧在线（核心验收）
    pgA.reload(wait_until="domcontentloaded")
    pgA.wait_for_selector("button.user-chip", timeout=15000)
    pgA.wait_for_timeout(800)
    chip_visible = pgA.locator("button.user-chip").is_visible()
    pgA.screenshot(path=OUT + r"\qa-session-T7-A-still-online.png")
    check("T7 B 退出后 A 页刷新仍在 /dashboard（未跳 /login）",
          "/login" not in pgA.url, f"url={pgA.url}")
    check("T7 A 页渲染正常（user-chip 可见）且令牌未受影响",
          chip_visible and tok_of(pgA) == tokA,
          f"chip_visible={chip_visible}, tokA.len={len(tokA) if tokA else 0}")

    # ------------------------------------------------------------------ T8 A 也退出
    logout(pgA, shot_after=OUT + r"\qa-session-T8-A-logged-out.png")
    check("T8 A 页退出后到达 /login", "/login" in pgA.url, f"url={pgA.url}")
    check("T8 A 页 sessionStorage token 已清空", tok_of(pgA) is None, f"tokA={tok_of(pgA)!r}")

    # ------------------------------------------------------------------ R3 同账号双标签页也不牵连
    login(pgA, "nj001", "nj001")
    r3_tokA = tok_of(pgA)
    login(pgB, "nj001", "nj001")
    r3_tokB = tok_of(pgB)
    check("R3 同账号两标签页各自独立登录成功",
          ("/login" not in pgA.url) and ("/login" not in pgB.url) and bool(r3_tokA) and bool(r3_tokB),
          f"urlA={pgA.url}, urlB={pgB.url}")
    logout(pgB, shot_after=OUT + r"\qa-session-R3-B-logged-out.png")
    pgA.reload(wait_until="domcontentloaded")
    pgA.wait_for_selector("button.user-chip", timeout=15000)
    pgA.wait_for_timeout(600)
    check("R3 同账号下 B 退出，A 刷新仍在线",
          ("/login" not in pgA.url) and (tok_of(pgA) == r3_tokA),
          f"urlA={pgA.url}, tokA.len={len(r3_tokA) if r3_tokA else 0}")

    browser.close()

# ---------------------------------------------------------------------- T9
# 过滤掉与本次改造无关的噪声（favicon 等资源 404）。
noise_markers = ("favicon",)
t9_errors = [
    e for e in console_errors
    if not any(m in e["text"].lower() for m in noise_markers)
]
check("T9 全程两标签页 0 条 console error / pageerror", len(t9_errors) == 0,
      f"count={len(t9_errors)}")

print("\n=== console error / pageerror 明细 ===")
if t9_errors:
    for e in t9_errors[:30]:
        print(f"  [{e['page']}] {e['kind']}: {e['text']}")
else:
    print("   (none)")

# ---------------------------------------------------------------------- 汇总
passed = sum(1 for _, ok, _ in results if ok)
total = len(results)
print("\n=== 结果表 ===")
for name, ok, detail in results:
    print(f"  {'PASS' if ok else 'FAIL'}  {name}  | {detail}")
print(f"\nSUMMARY: {passed}/{total} PASS")

# 落盘一份结果（便于 team-lead 复核）
with open(OUT + r"\qa-session-isolation-result.json", "w", encoding="utf-8") as f:
    json.dump(
        {
            "passed": passed,
            "total": total,
            "results": [{"name": n, "pass": ok, "detail": d} for n, ok, d in results],
            "console_errors": t9_errors,
        },
        f,
        ensure_ascii=False,
        indent=2,
    )
