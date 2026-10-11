# 多标签页会话隔离：localStorage → sessionStorage（选型与验收口径）

> 2026-10-11 · 状态：**已落地并验证（QA 22/22 PASS + 主理人复跑）** · 关联：DECISIONS 2026-10-11（4 条）
> 触发问题：同一浏览器多标签页登录不同账号时状态互相覆盖；退出任一账号，所有标签页连带掉线。

## 1. 根因（一句话）

**存储作用域错配**：会话凭证是「每个会话一份」的数据，却被放进「全浏览器共享」的容器。

- `localStorage` 的作用域 = **同源**（同源内所有标签页共享同一个键空间）→
  ① 后登录者覆盖前者（多账号无法并存）；② 任一标签页 `removeItem` = 全体失去凭证（连带退出）。
- 这不是某个函数写错，而是**容器选错**——修法不是加锁/加事件同步，而是**换容器**。

## 2. 选型（含反例排除）

| 方案 | 结论 | 理由 |
|---|---|---|
| **sessionStorage**（采纳） | ✅ | 作用域 = **标签页私有**：各标签页独立副本、页内刷新保留、关标签页自动清除——天然「一标签页 = 一会话」 |
| 多命名空间键 `lims_token:{userId}` + 手动选槽位 | ❌ | 引入「当前用哪个槽位」的额外状态与切换 UI；只解决并存、不解决退出同步的心智负担 |
| localStorage + `storage` 事件主动同步 | ❌ | 仍是同一键空间：能同步「登出」却不能支持多账号并存（方向错了） |

**分治原则（值得记住）**：**按数据性质选择存储作用域**，不要「统一到一种存储」——

- 会话凭证（token）→ `sessionStorage`（隔离是特性）；
- UI 偏好（本项目的 `lims_ai_panel_pos` / AI 陪伴开关）→ `localStorage`（**跨标签共享是特性**，勿「顺手迁移」）。

## 3. 实现结构（本项目的落地）

- **唯一封装**：`frontend/src/utils/authStorage.ts`
  - `getAccessToken/setAccessToken/getRefreshToken/setRefreshToken/clearAuthTokens`（键名沿用 `lims_access_token`/`lims_refresh_token`）；
  - 模块加载时**幂等清理** localStorage 旧键；
  - 文件头写死**维护约束**：App 内禁止绕开本模块直接读写这两个键。
- **迁移的 6 个读写点**（一次改齐，缺一即残留旧容器）：
  `utils/request.ts`（请求头 / 401 清理 / 静默续期）、`stores/auth.ts`（初始化 / 登录 / 登出）、
  `api/ai.ts`、`api/exportApi.ts`、`utils/aiStream.ts`（原生 fetch/SSE 手动带 token 的三处）。
- **刻意不做旧令牌迁移**：迁移会制造「旧副本被多标签页分叉继承」的歧义；断代（升级后重登一次）语义最干净。

## 4. 验收口径（可执行；脚本已入库）

脚本：**`docs/test/qa-session-isolation.py`**（22 断言，可重复运行）。

关键姿势与断言：

1. **模拟真双标签页**：Playwright 用 **同一 browser context 的两个 `context.new_page()`**（无 opener 关系）
   ——不要用同一 page 反复 `goto`，那根本测不出隔离；
2. **并存**：A 页登 nj001、B 页登 nj002 → 两页 `sessionStorage` 令牌**各不相同**、各自可正常操作；
3. **退出互不牵连（核心）**：B 页执行退出（`button.user-chip` → 菜单「退出登录」→ ElMessageBox 确认）→
   B 回 `/login` 且令牌清空 → **A 页刷新仍在 `/dashboard`**；
4. **刷新保持**：单页连续刷新 2 次仍在线且令牌不变；
5. **旧键清理**：手动 seed localStorage 旧键 → reload → 两键为 `null`（且不影响 sessionStorage 会话）；
6. **回归**：同账号双标签页也不牵连；`/api/ai/status` 带 sessionStorage 令牌返回 `code=0`；全程 **console 0 错误**。

## 5. 边界与已知行为（避免误当缺陷）

- **关闭标签页 = 会话结束**（sessionStorage 生命周期）——多账号场景的**预期行为**；
- **`window.open`/复制标签页**：新标签页会克隆一次 opener 的 sessionStorage 快照，**之后各自独立**（退出互不影响）；
- **升级后需重新登录一次**（旧键被清理、不做迁移）；
- 令牌长度因账号权限/菜单 claim 数而异（本项目 nj001 1541 vs nj002 328 字符）——正常，非缺陷。

## 6. 踩坑速记

- 迁移必须**一次改齐所有读写点**（含原生 `fetch`/SSE 手动带 token 的地方），否则残留点会「看起来好了、偶发又串」；
  改完用 `grep -rn "lims_access_token\|lims_refresh_token\|TOKEN_KEY" src` 验零残留（预期仅 authStorage.ts）。
- 验收必须用真实浏览器双标签页；单页刷新只能测「保持」，测不出「隔离」与「退出不牵连」。
