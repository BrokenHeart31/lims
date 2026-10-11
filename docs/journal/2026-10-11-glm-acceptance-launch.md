# 2026-10-11 验收收尾 + 全服务启动（GLM）

## 1. 本轮目标

- 用户指令：「按开工步骤读全量治理文档 → 完成项目优化以及剩余全部工作 → 启动让我验收」。
- 关联：上一轮如实说明的 4 条遗留（F10 / F8 表单暂存 / T-921① / T-922；GCM 坑已回写技能库）。
- 性质：**收尾轮**，无新增业务范围；目标是「项目达到可验收状态 + 服务全部启动」。

## 2. 实际做法

### 2.1 开工 + 现状核对

- 读 STATUS / TODO / HANDOFF / DECISIONS（含 2026-10-08 全部段落）+ `.workbuddy/memory/` + 上轮日记；
- `git status`：工作区 0 项未提交；三分支 `agent/glm = develop = main = 24ae1c5`；
- **`ls-remote` 复核远端同名分支 = `24ae1c5`**（本地 `git branch -v` 显示 `[ahead 1]` 系本地跟踪 ref 未刷新的显示假象，以 ls-remote 为准）；
- 数据：MySQL 在跑；DEMO 五项 = S10/S30/S40/S60/S70（seed03 标准态），样品总数 7。

### 2.2 遗留项逐条复核收口

| # | 项 | 动作 |
|---|---|---|
| 1 | T-921① rgba 令牌化 | 复核统计（`rgba\(255,\s*255,\s*255` 宽松口径 = **63 处**；分布 element-override 21 / tokens 14 / MainLayout 8 / base 5 / dashboard 4 / ai 组件 11）→ **明确不做** → TODO 登记 + DECISIONS 落档 |
| 2 | F10 | 维持「未复现 + append-to-body 加固」如实表述；DECISIONS 补「不升级措辞」条目 |
| 3 | F8 表单暂存 | 保持不做（T-935 静默续期后场景消失）；DECISIONS 补记录 |
| 4 | T-922 RECALL 白名单 | 继续挂起（业务口径涉 CMA 治理）；DECISIONS 补「按用户指示保持」记录 |

### 2.3 质量门禁复跑（以 24ae1c5）

- 后端：`bash /tmp/lims-mvn.sh -o test` → **261/261 BUILD SUCCESS**（37s）；
- 前端：直调 node 二进制（`.bin` 为空的老坑）→ `vue-tsc` 0 / `eslint` 0 / `vite build` ✓（25.85s；
  analysis 分包 gzip 183.89 kB、主包 gzip 413.03 kB，与历史一致）。

### 2.4 全服务启动 + 冒烟

- 重建 `/tmp/lims-mvn.sh`（**必须含 `-Dmaven.multiModuleProjectDirectory`**，缺它 Maven 只输出一行警告后静默退出）；
- 三服务 Bash 后台托管：后端 `spring-boot:run`（8080）/ vite（5173）/ `ollama serve`（11434，OLLAMA_MODELS 指向 `ai/models`）；
- API 冒烟：nj001 登录 ✓（**61 权限 / 12 菜单**）；`sample/item/assign/result/report/stat/rollback` 7 接口全 `code=0`；
  AI `/api/ai/status` = `online=true / modelPresent=true / kbReady=true`；
- 浏览器核查（系统 py312 + playwright，脚本 `_logs/accept-check-2026-10-11.py`）：
  **6/6 PASS / console 0 错误**——登录页 / 工作台 / 样品登记（表体有行）/ 报告审核页 / AI 悬浮球。

## 3. 💡 心得与判断

1. **「明确不做」也是一种交付**。T-921① 的正确处置不是「做掉」而是「复核后登记不做」：63 处里多数是设计层（tokens/element-override）的合法半透明定义，全量令牌化属纯样式重构，风险却落在刚修复的固定列/弹窗层级区域。**把「不做的理由」落档，比让后人猜「为什么没做」有价值**。
2. **诚实性纪律要落到措辞**。F10「未复现 + 加固」若写成「已修复」，验收时一旦被复现，整个交付的可信度都会塌。DECISIONS 专门补了「不升级措辞」条目，把这条纪律固化成可检查项。
3. **「ahead 1」≠ 未推送**。本地 `refs/remotes/origin/*` 会被沙箱吞更新，`git branch -v` 的 ahead/behind 拿本地跟踪 ref 比、不可信；**推送状态一律以 `ls-remote` 为准**（本轮再次实证）。
4. **启动本身就是一次小规模验收**。三服务起来后做 7 接口 + 6 项浏览器核查，本质是 smoke test——它把「代码全绿」与「系统真能跑」之间的缝隙补上了。

## 4. ⚠️ 踩坑记录（现象 → 根因 → 处理）

1. **PowerShell 工具静默失败**：任意命令（含 `Write-Output "PS-OK"`）返回 exit 1 且无输出。
   → 本轮所有服务启动改用 Bash `run_in_background` 托管（成功）。
   ⚠️ 影响：**历史上「停进程唯一可靠手段」是 PowerShell 工具**（Git Bash 的 taskkill 不行）——本轮无法验证停进程路径，记入 memory 待下轮复核。
2. **Maven 直启脚本缺 `-Dmaven.multiModuleProjectDirectory` 静默退出**：只输出 "system property is not set." 一行、exit 0、不报错不运行。
   → 补上该参数（指向 Maven home）即恢复。
3. **`sample/page` 用 `current/size` 返回 400**：该域是历史兼容写法（`pageNum/pageSize`，T-911 裁决保留双轨，api-spec 0.3 有明文）。
   → 冒烟脚本按契约参数名调用。
4. **`curl -o /tmp/xxx` 偶发不落文件**（与历史上 exit 23 write error 同族）：输出重定向到 shell 变量（`BODY=$(curl -s ...)`）最稳。

## 5. 📊 进度

- 项目总进度：**99% → 99%**（收尾轮；剩余 = 用户人工体验终验，由用户执行）。
- 质量门禁：后端 261/261；前端三门禁全绿；API + 浏览器冒烟全过。

## 6. 可复用结论（沉淀）

- **验收收尾五步法**（已沉淀为技能 `.agents/skills/lims-local-launch/`）：
  ①治理文件全量读 + git/数据现状核对 → ②遗留项逐条复核（做/不做/保持，**全部落档**）→ ③门禁全量复跑 → ④全服务启动 + 双冒烟（API + 浏览器）→ ⑤治理收口 + 提交推送。
- 冒烟脚本模板：`_logs/accept-check-2026-10-11.py`（playwright：登录→工作台→业务页→AI 球→console 采集）。
