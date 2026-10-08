# 2026-10-07 豆包：LIMS 全流程逐步测试（S10→S90 + 24 路由 + RBAC/回退/导入/导出/AI 离线）

> 角色：豆包（B 级）。本轮**只测试、不改代码**；产出测试报告 + 优化方案交 GLM 执行。
> 主交付物：`docs/test/2026-10-07-fullflow-test-report.md`（F1~F31 分级问题清单、顺畅路径、状态精简清单、验收）。

## 1. 本轮目标（关联任务）

- 用户指令：完整理解项目功能架构与业务流程，对项目**从启动到结束的每个操作环节**逐步测试，逐一验证所有按钮/交互控件的功能逻辑与冗余设计；优化操作路径与交互体验；重点检查每步**状态显示是否准确、必要、无重复冗余**；最终输出结构化测试报告与优化方案交下一 agent。
- 验收口径：覆盖全部路由、走通 S10→S90、给出可执行（含定位文件/复现/改法/Owner）的问题清单。

## 2. 实际做法

- **环境**：MySQL 3306 库 `lims`（真实口令 123456，在 gitignore 的 application-dev.yml）；后端 8080（JDK21 + Maven 3.9.12 全路径离线 `spring-boot:run`）；前端 5173（必须 `node node_modules\vite\bin\vite.js` 直启，npm script 在本机 PATH 下异常）。本机无 Bash，全走 PowerShell（5.1，无三元运算符，控制台中文需 `[Console]::OutputEncoding=UTF8`）。
- **方法**：内置浏览器（browser-use，plane=bu）逐页 UI 操作 + PowerShell `Invoke-RestMethod` 直调 API 做状态机/权限/红线确定性验证 + 下载 xlsx 用 zip/XML 解包核验 + 后端日志取证。
- **真实走通业务链路**（以 `03_demo_flow_seed.sql` 演示样品）：
  - 结果录入：DEMO-003 草鱼 4 项，限量比较/不得检出自动判定、感官人工判定全部正确；保存后 S40→S50、进度 4/4、整体结论合格；提交不可逆确认后 S50→S60，样品从录入列表消失（范围收敛正确）。
  - 审核：DEMO-004 含 1 待判定项，approve 不带 abnormalConfirmed → 400；退回原因空 → 400；带确认放行→S70；DEMO-003→S70。
  - 签发：3 个样品 → S80；报告生成：DEMO-003/004 → S90，ReportVO 完整；打印页封面/正文/项目表专业；省平台导出解包 22 单项行、UTF-8 正常、待判定如实输出。
  - 回退：timeline/targets/失效预览/S90 拒绝(4103) API 全验；作废召回弹窗二次确认 UI 验证。
  - Excel 导入：模板文件标记防重（重传 400）、非 xlsx 400。
  - RBAC：nj002/njna000 越权 403、路由守卫跳友好 /403。
  - AI：ollama 未启动时明确离线、不造假答案。
- **覆盖**：24 条路由全部巡检（dashboard/task/sample/item/assign/result×2/report×3/print/query×4/base×2/export/sys×4/ai×2/login/403/404）。

## 3. 💡 心得与判断

- **这套系统的「红线设计」质量很高，测试重点应放在「红线之外的缝」**：判定引擎、审核逐项确认、安排待指派拒绝、回退失效预览、S90 不可回退、导入防重、AI fail-loud 都实测正确；真正出问题的是三类「缝」：
  1. **框架用法坑**（F3：EP 的 reserve-selection 必须配 row-key，否则异步数据不渲染——症状极像「接口没数据」，极易误判）；
  2. **数据迁移质量**（F17：旧库导入字符集 + 逗号切分，直接拖垮自动分派与报告可信度）；
  3. **合规闭环没走完**（F20：审核「勾选确认」只放行、不要求对待判定项落最终裁决，未决项被带进 CMA 报告）。
- **判断 F20 是本轮最需要 GLM 自裁的点**：它同时满足 AGENTS 2.6「真实两难三问」——数据/契约无唯一答案、涉及已出报告返工、有 CMA 监管含义。建议口径：审核时对每个 PENDING 项强制人工裁决合格/不合格并落库，报告生成对仍待判定项 fail-loud 拒绝。Copilot 配额仅剩 1 次，按规则由 GLM 自裁落档 DECISIONS/docs/knowledge。
- **状态冗余是真实存在的体验问题但都不阻断**：双面包屑、进度 0%+0/4、状态徽章与确认情况并排、铃铛每条重复提示——统一原则应是「同一信息只在一个位置表达」。

## 4. ⚠️ 踩坑记录（现象 → 根因 → 处理）

1. **F3 定位（最耗时）**：分解/安排页「共 N 条但表体 0 行」。
   - 现象：工具栏/分页 total 正常，tbody 0 个 tr；API、setupState.tableData、ElTable props.data、内部 store.states.data 逐层都有 N 条；doLayout/resize/重赋数据无效。
   - 根因：el-table 有 `type="selection"` + `reserve-selection` 但**缺 row-key**（EP 强制搭配，异步数据到达后不渲染表体）。
   - 处理：用临时 A/B（加 row-key 即渲染、去掉即复现）锁定唯一变量，随后**全部还原并 grep 验证无残留**；正确范例是 sample/index.vue:482。
2. **F17 双重损坏**：
   - 现象：鲜食玉米 62 项 itemName 出现「2」、methods 乱码「åç§NY/T 1434」，39 项待指派。
   - 根因①：V1 迁移用第一个逗号切「名称,单位」，但农药名「2,4-滴…」自带逗号；根因②：lims.sql 导入时连接字符集非 utf8mb4，中文双重编码（V1 的 SET NAMES 救不回已坏的源表）。
   - 处理：只取证与给方案（迁移内容设计归 GLM、重导执行归豆包），不擅改 db 设计。
3. **PowerShell 控制台中文乱码是假象**：Get-Content 读 UTF-8 文件在 PS5.1 控制台显示乱码，文件本身正常；读源码/治理文件一律用 Read 工具，避免被乱码误导。
4. **前端必须直启 vite**：`npm run dev` 在本机环境异常，`node node_modules\vite\bin\vite.js` 稳定。
5. **浏览器自动化**：`bu.find("结果录入")` 同时命中侧栏菜单与行按钮（菜单排前），须取表格区 ref；el-select 点 `.el-select__wrapper`；导航后等 2~3 秒；后期 webview 渲染面黑屏但 DOM/refs/JS 仍可用（截图/snapshot 不可靠，改走 API 与 DOM 读取取证）。
6. **JWT 硬过期（F8）**：20:09 过期、20:16 触发，前端直接登出、录入内容丢失；后端明明签发了 refreshToken 且有 /auth/refresh，前端全仓从未调用——典型的「后端能力齐备、前端没接」。

## 5. 📊 进度

- **项目总进度：99% → 99%**（本轮为全流程测试与缺陷梳理，未新增业务范围；业务功能零缺口的结论维持，但暴露出 1 个 P0 页面阻断、4 个 P1 与数据/合规问题，修复完成前「用户人工体验终验」不能算通过）。
- 本轮前：无系统级全流程测试报告；本轮后：形成 F1~F31 可执行问题清单（1 P0 / 4 P1 / 4 P2 / 20 P3 / 2 性能观察）+ 顺畅路径 + 状态精简清单 + 验收清单，GLM 可直接照单修复。

## 6. 可复用结论（值得沉淀）

- **Element Plus 排障要点**：`reserve-selection` 必须配 `row-key`，否则异步数据下表体不渲染但 total 正常；el-table 的 `height` 不识别 `calc()`，用数值或外层 CSS。可补进 `.agents/skills/vue3-crud-page/SKILL.md`。
- **旧库迁移标准动作**：导入旧 SQL 必须 `--default-character-set=utf8mb4` 并先抽查中文；「名称,单位」类切分对自带分隔符的名称要按**单位枚举白名单/最后一个分隔符**切，并在迁移脚本里写数据质量断言（fail-loud）。可补进 excel-import / 数据迁移相关 skill。
- **CMA 类报告系统的通用红线**：对外报告正文不允许出现「待判定」；审核放行异常项必须把「人工最终裁决」落库，而不是只记一个「我确认了」的布尔位。
- **测试方法论**：状态机类系统用「API 确定性验证红线 + UI 验证交互与状态显示」组合效率最高；UI 渲染层黑屏时 DOM/fetch 仍可取证，不必依赖截图。

## 7. 数据副作用与还原

- DEMO 样品 18→S30、19→S40、20→S90（CMA 报告）、21→S90（CMA 报告，含放行待判定项）、22→S80。
- 还原：重跑 `db/seed/03_demo_flow_seed.sql`（幂等）。
- 证据留存于 `D:\lims\_logs\`（assign18.json、backend.log、export-inspect2.txt、模板与导出 xlsx）。
