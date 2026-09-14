# 2026-09-13 豆包 — 项目测试巡检与问题清单（T-917 收口前）

## 1. 本轮目标

GLM 在 T-917 UI 收口时因 Git 对象库损坏中断，HANDOFF 交下一位 Agent。本轮豆包（B 级）目标：
**不改业务代码**，把项目真正跑起来，逐页实测，找出问题并落档，给 GLM 收口提供精确清单。

## 2. 环境与启动

| 项 | 值 |
|---|---|
| git 全路径 | `C:\Users\Chen\.workbuddy\binaries\PortableGit\versions\1.2.0\cmd\git.exe` |
| node | `C:\Users\Chen\.workbuddy-ai\binaries\node\versions\22.22.2-2\node.exe` |
| maven | `C:\Users\Chen\Desktop\apache-maven-3.9.11\bin\mvn.cmd` |
| JAVA_HOME | `C:\Program Files\Java\latest\jdk-21`（Java 21.0.10） |
| MySQL | `C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe -uroot -p123456`，库 `lims` 在跑 |
| 后端 | `mvn -o -DskipTests spring-boot:run` → 8080，2.8s 启动 |
| 前端 | 5173 已在跑（PID 26952），dev server |
| 登录 | nj001/nj001（R100），53 权限 / 11 菜单 |
| 数据 | sample_info 2 条（S20×1 河蟹、S90×1 花鲢），sample_result 7 条 |

## 3. 质量门禁复测（全绿）

| 门禁 | 结果 |
|---|---|
| 后端 `mvn -o test` | **107/107 通过，BUILD SUCCESS**（8.5s） |
| 前端 `npm run lint` | ESLint 0 errors / 0 warnings |
| 前端 `npm run build`（=vue-tsc --noEmit && vite build） | vue-tsc 0 错误，vite build 37.56s 成功；analysis chunk 544KB/gzip184KB，主包 1275KB/gzip412KB（与记录一致） |

> 注：PowerShell 把 mvn/vite 的 stderr（WARN/chunk 体积警告）当错误，退出码非 0，但 BUILD SUCCESS / ✓ built 均打印，实际通过。

## 4. API 实测（nj001，全部 code=0）

正确路径下全部正常（初测时我误用了 `/query/testing`、`/stat/conclusion-pie` 等猜测路径，返回 404→500，核对源码后确认是**我猜错路径**，非后端 bug）：

- `/query/{testing,history,library,my-tasks}/page` ✓
- `/stat/{overview,sample-status,inspect-type,top-clients,category,tester-workload,dept,unqualified-items,monthly-trend}` ✓
- `/task/page?pageNum&pageSize`、`/sample/page?pageNum&pageSize` ✓（历史双轨分页，T-911 已定稿）
- `/sys/{user,role,menu,dept}` ✓、`/base/{tester-method,lib}` ✓
- `/export/province` 返回 Excel 流 ✓

## 5. 🔴 发现的问题（按严重度）

### P1 — PageHeader 标题被挤成竖排（CSS bug，影响约 8 页）

**现象**：当 PageHeader 右侧有 2 个以上操作按钮时，标题文字逐字竖排换行。
- 复现：样品登记（样/品/登/记）、项目标准库（项/目/标/准/库）、结果录入（结/果/录/入）、报告审核签发（报告审核签/发）。
- **根因**：`components/common/PageHeader.vue` 第 117 行 `.page-header__title` 是 flex 子项但未设 `flex-shrink: 0`；右侧 `.page-header__actions` 多按钮时把 h1 压到一字宽，中文按字换行。
- **修复建议**（GLM A 级）：`.page-header__title { flex-shrink: 0; white-space: nowrap; }`，或给 `.page-header__title-row` 的 h1 加 `min-width: max-content`。

### P2 — 双面包屑重复

**现象**：顶栏 MainLayout 面包屑显示「LIMS / {分组} / {当前页}」，页面内 PageHeader 又渲染一条「工作台 / {分组} / {当前页}」，最后一段（当前页名）出现两次。例：顶栏「LIMS / 结果录入 / 结果录入」。
- 根因：MainLayout 已有全局面包屑，各页面 PageHeader 又手写面包屑 slot。HANDOFF 早已列「路由 meta.breadcrumb 自动注入消除冗余」为待办，本轮实证仍在。
- **建议**：二选一——要么 PageHeader 不再渲染面包屑（顶栏已够），要么顶栏不渲染（页面内自带）。当前是两套并存。

### P3 — 表格列宽不足导致文字截断

| 页面 | 被截断列 | 现象 |
|---|---|---|
| 监抽任务 | 任务来源 | 只显示首字「省/市/省」（应为"省畜牧兽医局"等） |
| 报告生成 | 检验类别 | 表头只显示「检」 |
| 样品登记 | 抽样地址 | "通州区十总镇新..." 省略号截断 |

- **建议**：给这些列设 `min-width` 或 `show-overflow-tooltip`，长文本悬浮显示完整内容。

### P4 — 查询筛选区按钮对齐不一致

- 在检查询/历史查询/项目库查询：「查询/重置」按钮在筛选卡片**左下**；
- 监抽任务/样品登记/系统管理：按钮在筛选卡片**右侧**。
- **建议**：统一 DataFilter 内按钮对齐方式（建议右对齐，与 T-917 设计系统一致）。

### P5 — 每页面 favicon 404

- `frontend/public/` 无 favicon.ico，浏览器每页自动请求 `/favicon.ico` 返 404（console 每页 3 条 Failed to load resource）。
- 无功能影响，但 console 不干净。**建议**：放一个 `public/favicon.ico` 或在 index.html 加 `<link rel="icon">`。

## 6. 🟡 Git 仓库状态（GLM 必须处理，豆包不动手）

- `D:\lims\.git` **对象库损坏**：缺 tree `3c168259069967b619abcd0e561105bc51dfad37`，`git status`/`git branch` 报 `fatal: unable to read tree`；HEAD=b206f780。
- 临时副本 `C:\Users\Chen\AppData\Local\Temp\lims_work_ui` 在 **main** 分支（非 agent/glm），已暂存 **38 文件 / +1814 / -560**，无删除项、无构建产物；`.shots/` 未跟踪（应排除）。
- 这些是 GLM T-917 step4~5 的成果（DataFilter/DataTable/AppLoading/AppSkeleton/ProgressBar + 13 页面迁移），尚未推远程。
- 合并路径仍须 `agent/glm → develop → main`（AGENTS 0.3）。

## 7. ✅ 验证无问题项

- 登录/JWT/动态路由/菜单树驱动侧栏正常，无「点击正常 F5 404」。
- 工作台 6 KPI + 8 阶段时间线 + 月度趋势真实数据。
- 质量分析页 7 个 ECharts 全部渲染（状态分布/检验类别/送检单位 Top/大类/检验员工作量/部门/不合格项 Top/月度趋势），无 mock。
- 报告打印页：CMA 封面（证书号 181004090030）+ 7 条注意事项 + 检验项目表（孔雀石绿待判定、镉/恩诺沙星不合格标红、参考项带*）正确。
- 审核页空态/双页签/异常项清单正常。
- 部门树表保留树形交互（未被 DataTable 破坏）。
- 全代码库无 `ElMessageBox.confirm` 裸调用（仅 utils/confirm.ts 封装）。
- 无硬编码色值（仅注释和 CSS 变量 fallback）。
- R100 越权接口、数据范围收敛未回归。

## 8. 进度

项目总进度：**95% → 95%**（本轮只测试找问题，未改代码）。
剩余工作量：P1~P5 为纯前端 CSS/布局微调（约 0.5~1 人时），Git 恢复与推送为 GLM 专属。

## 9. 可复用结论

- **测接口先读 Controller 的 @GetMapping 路径**，不要凭猜测打 URL——本轮初测 9 个 500 全是路径猜错（`/query/testing` vs `/query/testing/page`、`/stat/conclusion-pie` vs `/stat/inspect-type`）。
- **PowerShell 跑 mvn/vite 时退出码不可信**：stderr 有 WARN 就报非 0，必须看输出里的 `BUILD SUCCESS` / `✓ built` 字样。
- **PageHeader 这类 flex 标题组件，h1 必须 `flex-shrink: 0`**，否则右侧按钮一多中文标题逐字竖排——这是暗色后台模板的高频坑。
