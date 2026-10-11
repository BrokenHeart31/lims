# 2026-10-11（追加）多标签页会话隔离改造（localStorage → sessionStorage）（GLM + 团队）

## 1. 本轮目标

- 用户报告：同一浏览器多标签页登录不同账号，状态相互覆盖；退出任一账号，**所有标签页连带掉线**。
- 目标：让每个账号/标签页的会话相互隔离、互不影响；分析根因 + 给出方案 + 实现并验证。
- 性质：缺陷修复 + 存储层改造（无新增业务范围）。

## 2. 实际做法

### 2.1 根因定位（主理人，先于团队）

- 全仓搜索 `localStorage`：token 存储在 `lims_access_token` / `lims_refresh_token` 两个**固定键**；
- 读写点共 6 处：`utils/request.ts`（请求头附加 / 401 清理 / 静默续期）、`stores/auth.ts`（初始化 / 登录写 / 登出清）、
  `api/ai.ts`、`api/exportApi.ts`、`utils/aiStream.ts`（原生 fetch/SSE 手动带 token）；路由守卫走 store 不直接读存储。
- **根因**：localStorage 作用域 = 同源共享（全部标签页一个键空间）→ ① 后登录覆盖前者（无法并存）；② 任一页清键 = 全体失效（连带退出）。

### 2.2 方案与分工（SoftwareCompany 流程）

- 方案：token 改存 **sessionStorage**（标签页私有：独立副本 / 页内刷新保留 / 关页即清）+ 抽 `utils/authStorage.ts` **唯一封装**；
  旧 localStorage 键幂等清理；**刻意不做迁移**（升级后重登一次，语义干净）。
- 分工：主理人侦察 + 设计 → 工程师（寇豆码）实现 8 文件 + 三门禁 → QA（严过关）真实浏览器 22 断言 →
  主理人复核（核对实际文件 + 独立复跑 QA 脚本 + 复跑三门禁）。

### 2.3 实现与验证（数字）

- 工程师：新增 `authStorage.ts`（87 行，含 try/catch 稳健化）；迁移 `request.ts`/`auth.ts`/`ai.ts`/`exportApi.ts`/`aiStream.ts`；
  两处注释措辞更新（`stores/aiAssistant.ts`、`AiFloatingAssistant.vue`）；**三门禁全绿**（vue-tsc 0 / eslint 0 / build ✓ 15.6s）；
  grep 验证：除 authStorage.ts 定义外零残留。
- QA：`_logs/qa-session-isolation.py` **22/22 PASS**——T4 A(nj001)/B(nj002) 令牌互不相同（1541 vs 328）；
  **T6/T7 B 退出 → A 刷新仍在线（核心）**；T3 主动 seed 旧键后 reload 验证被清理；
  R3 同账号双标签页也不牵连；T9 console 0 错误。7 张截图 + 结果 JSON。
- 主理人复核：读实际文件确认与汇报一致；**独立复跑 QA 脚本 22/22 PASS**；复跑三门禁全绿（27s）。

## 3. 💡 心得与判断

1. **根因是「存储作用域错配」**：会话凭证是「每个会话一份」的数据，却被放进「全浏览器共享」的容器——
   不是某个函数写错，而是容器选错。修法不是加锁/加事件同步，而是**换容器**（sessionStorage）。
2. **反例排除（为什么不做多命名空间键）**：`lims_token:{userId}` + 手动切换能「支持多账号」，
   但引入「当前槽位」状态与选择 UI，还解决不了「退出同步」之外的心智负担；sessionStorage 零 UI、零状态、语义天然。
3. **「不做迁移」是刻意决定**：迁移会制造「旧副本被多标签页分叉继承」的歧义；断代（重登一次）最干净。落档为决策防后人「好心」加迁移。
4. **按数据性质分治存储**：UI 偏好（AI 面板位置）继续 localStorage（跨标签共享是特性）；会话令牌走 sessionStorage（隔离是特性）。
   「统一到一种存储」是错误的整洁目标。
5. **验收要模拟真实的多标签场景**：Playwright 用「同 context 独立 page」才是真的双标签页（无 opener）；
   用同一 page 反复 goto 根本测不出隔离。

## 4. ⚠️ 踩坑记录（现象 → 根因 → 处理）

1. nj001 令牌（1541 字符）明显长于 nj002（328）——**非缺陷**：权限/菜单 claim 数量差异，符合预期（QA 已注明）。
2. 承接上轮：PowerShell 工具本会话不可用 → 全程 Git Bash + Python；浏览器验证用系统 py312 playwright（已就绪）。

## 5. 📊 进度

- 项目总进度：**99% → 99%**（缺陷修复 + 质量提升轮，无新增业务范围）。
- 门禁：前端三门禁两轮全绿；QA 22/22（两轮）；console 0 错误。

## 6. 可复用结论（沉淀）

- **会话隔离三件套**：① 按数据性质选择存储作用域（会话 → sessionStorage / 偏好 → localStorage）；
  ② 令牌读写收敛到唯一封装模块（文件头写维护约束，防绕开）；③ 验收 = 真浏览器「同 context 双标签页」
  （建 A/B、双登不同账号、B 退出、A 刷新仍在线）。
- 测试脚本可复用：`_logs/qa-session-isolation.py`（22 断言模板）。
