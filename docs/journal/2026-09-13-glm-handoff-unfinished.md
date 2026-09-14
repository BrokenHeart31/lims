# 2026-09-13 GLM — 未完成任务交接清单

## 一、本轮目标

关联任务：T-917-5、T-917-6~10，以及 UI 完成后的 Git 提交/分支同步。

本轮不继续修改业务代码，先把当前没有完成、尚未验证或存在环境阻塞的内容明确记录，供下一位 Agent 按清单接手，避免把「功能已完成」误判成「项目已全部收口」。

## 二、当前完成边界

### 已完成

- 业务遗留任务 T-105、T-106、T-107、T-603、T-803 已完成，接口、页面、权限和关键拒绝路径已有交付记录。
- 动态路由 T-916 已完成：路径注册表、中间件转换、菜单与路由同源、历史路径别名和 catch-all 顺序处理均已落档。
- T-917-1 至 T-917-4 已完成：现状分析、Design Token、Layout、公共组件和 Element Plus 主题覆盖。
- T-917-5 已完成的页面迁移批次：
  - `frontend/src/views/report/audit.vue`
  - `frontend/src/views/result/index.vue`
  - `frontend/src/views/system/user.vue`
  - `frontend/src/views/system/role.vue`
  - `frontend/src/views/system/menu.vue`
  - `frontend/src/views/system/dept.vue`
  - `frontend/src/views/base/tester-method.vue`
  - `frontend/src/views/base/product-lib.vue`
  - `frontend/src/views/query/library.vue`
  - `frontend/src/views/query/testing.vue`
  - `frontend/src/views/query/history.vue`
  - `frontend/src/views/result/my-tasks.vue`
  - `frontend/src/views/export/province.vue`
- 上述批次已统一筛选栏、高风险确认和部分 loading/empty 外壳；结果录入异常行继续消费后端结论，不在前端重算判定。
- 全局检索未发现页面直接调用 `ElMessageBox.confirm`；已发现的高风险操作均已改为 `askConfirm()`。

## 三、明确未完成任务

### 1. T-917-5：逐页 UI 迁移尚未全部完成

以下页面仍需要检查并按公共组件职责补齐筛选区、加载态、空态、错误态、表格外壳、响应式间距和横向滚动；不要为了套组件破坏现有树形表格、抽屉或业务交互：

- `frontend/src/views/assign/index.vue`
- `frontend/src/views/item/index.vue`
- `frontend/src/views/sample/index.vue`
- `frontend/src/views/task/index.vue`
- `frontend/src/views/report/generate.vue`
- `frontend/src/views/query/analysis.vue`

另外需要专项复核：

- `frontend/src/views/dashboard/index.vue`：已经消费真实统计/任务接口，并已有加载与空态，但还需做最终视觉层级、异常态和三档尺寸检查。
- `frontend/src/views/report/print.vue`：独立打印页不应套 MainLayout；需要验证 `report-print.css`、打印分页、签名占位和 CMA/CMA-CATL 两种版式。

### 2. T-917-6~10：交互、全局统一和最终验收尚未完成

按用户指定顺序继续完成：

1. 统一筛选、分页、抽屉、提交、删除、退回、审核、签发等交互反馈；
2. 复核 ECharts 与 Dashboard 的真实数据、空数据、失败数据和刷新行为；
3. 全局核对 Sidebar、Header、Card、Button、Input、Table、StatusBadge、Modal/Drawer、Loading/Empty/Error 的视觉一致性；
4. 运行用户 20 项验收 Checklist；
5. 在 1440×900、1920×1080、1366×768 下检查布局、横向溢出、固定列、抽屉和打印页。

真实浏览器自动化当前不可用，因此不能把 DOM/CSS 静态检查或构建通过写成浏览器视觉验收已完成。下一位 Agent 需要在可用浏览器中人工复核，至少覆盖登录、动态菜单、结果录入、报告审核、报告打印和质量分析页。

### 3. 统一质量门禁尚需重新执行并留证

最近记录中 `npm run lint` 和 `vue-tsc --noEmit` 已通过；20:27 查询/导出批次的统一 build 尚未在交接记录中形成新的完整证据。下一位 Agent 应在 `frontend/` 重新执行并记录：

```bash
npm run lint
npm exec vue-tsc -- --noEmit
LIMS_BUILD_OUTDIR=dist-step5-final npm run build
```

后端本轮没有新增业务代码，但交付前应按当前基线确认 `mvn test` 仍通过，并避免把构建目录、`node_modules`、`target` 或 `.class` 纳入提交。

### 4. Git 提交与分支同步尚未收口

- `D:\lims\.git` 仍存在 broken tree，禁止在该对象库上执行高风险 `reset`、`stash` 或直接提交。
- 临时副本 `C:\Users\Chen\AppData\Local\Temp\lims_work_ui` 当前已暂存 38 个治理、公共组件和前端文件；暂存区未发现删除项。
- `.shots/` 仍为未跟踪目录，必须在提交前明确排除或加入忽略规则，不能进入提交。
- 继续提交前必须逐项执行并审阅：

```bash
git status --short
git diff --cached --stat
git diff --cached --name-status
git diff --cached --name-status | findstr /R /B "D"
```

确认无误后，在临时副本创建符合 Conventional Commits 的 `refactor:` 或 `docs:` 提交；再按唯一路径 `agent/glm -> develop -> main` 推进。每次 Git 写操作后都要重新检查 commit object、`HEAD`、`agent/glm` ref，并最终执行：

```bash
git ls-remote origin "refs/heads/*"
```

不要把 PAT 写入 remote、文件、提交信息或日志。

## 四、当前判断与排除方案

- 不把剩余 UI 工作拆成数据库或 API 改造：用户明确要求保留既有 API、权限、认证、Pinia Store 和状态机。
- 不强行把 `system/menu.vue`、`system/dept.vue` 的树形表格包装成普通 `DataTable`：这样可能破坏树展开、row-key 和 tree-props。
- 不把当前页异常 KPI 当作全库 KPI：需要全库统计时应新增后端聚合接口，不能由前端分页数据推导。
- 不在前端重算检验结论：未录入、待判定和不合格的颜色只消费后端结果。
- 不把「lint/typecheck/build 通过」等同于「浏览器视觉验收通过」；两者必须分开记录。

## 五、交接后的建议顺序

1. 先处理临时副本 `.shots/` 和暂存区逐项核对，不要盲目提交。
2. 若需要继续改 UI，先完成上列 6 个剩余页面，再统一跑前端门禁。
3. 做浏览器人工验收并记录 20 项 Checklist 的逐项结果；发现问题时按页面小步修复。
4. 更新 `TODO.md`、`STATUS.md`、`HANDOFF.md` 和本目录索引后，再提交并推送。
5. 最终交接时明确区分：业务功能完成、UI 迁移完成、视觉验收完成、Git 远程同步完成。

## 六、进度

本轮前：治理文件只记录到 20:27 查询/导出批次，未把剩余页面、最终验收和 Git 临时副本状态集中列清。

本轮后：新增本交接清单，并同步 `TODO.md`、`STATUS.md`、`HANDOFF.md` 和日记索引；T-917-5 继续进行，T-917-6~10 保持待办，Git 提交/推送仍未完成。

项目总进度：约 95% → 约 95%。本轮没有新增业务功能，增量来自未完成项的边界澄清和可复现交接记录。

## 七、可复用结论

交接日志必须同时记录「已完成页面」「尚未迁移页面」「尚未验证项目」和「环境阻塞」，并把代码门禁、浏览器视觉验收、Git 远程同步分成三个独立状态；否则下一位 Agent 很容易把构建通过误认为项目收口，或在损坏 Git 对象库上重复提交事故。
