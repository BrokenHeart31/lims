# 2026-10-08 全流程测试缺陷修复批次（T-930~T-940）—— 主理人 / GLM

> 任务来源：用户指令「读全量治理文档 → 完成项目优化以及剩余全部工作」；
> 输入为豆包 2026-10-07 全流程测试报告（F1~F31，`docs/test/2026-10-07-fullflow-test-report.md`）。
> 组织方式：SoftwareCompany 团队 SOP（主理人 orchestration + 工程师寇豆码实施 + QA 严过关独立回归）。

## 1. 本轮目标

按 P0→P3 修复 2026-10-07 全流程测试的全部 31 项缺陷（T-930~T-939）+ 数据修复落地 + T-940 回归，
并完成治理收口（DECISIONS/STATUS/HANDOFF/TODO/日记/overview）与 git 合并推送。

## 2. 实际做法（按批次）

### 批次 1：P0 + P1 前端（T-930/931/932）
- **F3**：`item/index.vue`、`assign/index.vue` 的 el-table 补 `row-key="id"`（`reserve-selection` 必须搭配，
  否则异步数据下表体 0 行）；`calc(100vh - Npx)` 高度改**响应式数值 max-height**（resize 监听 + onUnmounted 清理）。
- **F1/F10**：`element-override.css` 集中式修复——固定列**逐行态**实色背板（双层 background：EP 半透明行态色渐变
  叠实色卡片层，全程令牌化，顺手替换 3 处硬编码色值）；撤掉 EP 固定列内投影（鬼影来源之一）；对
  `el-table--scrollable-x` 常驻显示可拖拽横向滚动条（EP 内部 scrollbar 默认仅 hover 显现 → 用户「看不到也抓不住」）。
  task/sample 等含弹窗页面 `append-to-body`（层级加固）。
- **F2**：样品登记「批量确认」改**专用核对弹窗**（逐条列编号+名称+受检单位+状态）；选择条常驻
  「含筛选外 X 条」+ 一键清空；reserve-selection 语义保留。
- 工程师 Playwright 三档 33/33 断言；主理人复跑 vue-tsc/eslint/build 全绿后签收。

### 批次 2：F20 合规（T-934，S 级）
- **后端**：`AuditApproveDTO` 增加 `adjudications`（itemId=检测单项 id / conclusion∈{1,2} / reason 必填 ≤200）；
  `AuditServiceImpl.approve` 校验顺序 = 旧红线（未勾选 400）→ **未录入硬阻断** → 裁决完整性（缺条/重复/越集/非法值均 400）
  → 裁决落库（`conclusion`/`conclusion_source=2`/`judge_basis` 追加「｜【审核人工裁决】…」截断 250）
  → **重算整体结论**（抽 `OverallConclusionPolicy` 共享纯函数，录入域同步委托）→ 状态乐观 UPDATE → 流水。
- **报告门禁**：`ReportGenerateServiceImpl.generate` 增加 `assertAllItemsDecided`——任一单项（含参考项）
  未录入或结论∈{待判定,null} → 400（fail-loud）。
- **前端**：审核抽屉待判定项逐条 radio+说明输入；未录齐 / 未裁决 / 未勾选 三种情形按钮禁用并给可读原因。
- 契约 7.4/8.3 增量；单测 +9（261/261）。

### 批次 3：P2 + P3 + 数据脚本（T-935/936/937 前端 + T-933/938 脚本）
- **F8**：`request.ts` 重写——单飞锁静默续期 + 重放一次（`_retry` 防环）+ 认证端点直登出；`lims_refresh_token` 键落库/清理。
- **F4**：面包屑**单源=顶栏**（菜单树驱动、二级分组、首项「工作台」）；18 页 `#breadcrumb` 全移除。
- **F5/F6/F7**：工作台最近任务只取 S40~S70；去「已交付/真实统计」等术语；铃铛提示只留一处。
- **P3 19 条**：F9 表单分组+等级下拉、F11 登记台默认收敛（后端 additive `statuses`）、F12 checkbox 方形（3px）、
  F13/F18/F21/F23/F25/F26/F27/F28/F29/F30/F31 逐条落地。
- **数据脚本**：V1 切分重写+三条 fail-loud 断言；新增 V12 定向修复；seed 03 补 22 条演示资质；新增 seed 05 数据卫生。

### 执行阶段（主理人）
1. 备份（`_logs/backup-pre-fix-2026-10-08.sql`，43 表）→ 执行 V12（坏签名 22→0）→ seed 03（DEMO 重置+资质 60）→ seed 05（F22/F24 落地）；
2. 后端以新代码重启 + 数据核验；
3. **F17 全链路实走**：DEMO-001 登记确认→套库 62 项→保存→分解确认→自动分派 = **36/62 命中**（修复前 23/62），
   证据 `_logs/assign-demo001-after.json`（无乱码）；**T-939 复测** save 130ms / confirm 19ms / assign 112ms（非缺陷）；
4. **F22 导出核验**：省平台导出解包，菌落总数行 = 标准值 50000 / 结果 **1200** / 合格（「色泽正常」只剩河蟹「色泽」项的合法值）；
5. **F8 真机 E2E**（临时 35s TTL）：过期 → `/auth/refresh` 200 → 原请求重放 200、token 轮换、未被登出；
   refresh 失效 → 回 `/login?redirect=`；证据 `_logs/f8-*.png`。

## 3. 💡 心得与判断（本轮最值钱的部分）

1. **报告的「根因」也可能是错的——证据链必须自己走一遍**。F17 报告断言「lims.sql 导入时双重编码、源表已损坏」，
   但字节级取证（HEX 扫 C1 控制符签名）证明 lib/basisname/product_lib_item **全部干净**，乱码只在 `sample_item`
   的 61 行、且时间戳精确落在 2026-10-07 19:40:52（测试会话写入）。签名 `C3A5C28F…` 是**UTF-8 被 Latin-1 误读后重编码**，
   属 PowerShell 5.1 `Invoke-RestMethod` 对无 charset 声明 JSON 的经典坑。若照报告盲目重导（改名/清场/重迁三步 +
   3728 行 id 漂移），风险实存而收益为零。
2. **修「症状描述的相邻物」而不是症状本身，是修复轮最容易犯的错**。F17 的验收写着「命中率较 23/62 显著提升」，
   但实测**修乱码后命中率仍是 23/62**（受影响串「参照X/无指定」本就不在资质表内）——真正的瓶颈是
   `tester_method` 覆盖缺口。补 22 条演示资质后 SQL 模拟=36/62、实走=36/62。**验收数字要能解释来源，不能只填靶子**。
3. **合规修复要考虑「流程死角」**。F20 若只在报告生成处 fail-loud，未录入样品会卡死在 S80（无回退边）——
   所以必须同时在审核口硬阻断。**fail-loud 的位置决定了失败者还能不能回到路网内**。
4. **「逐条裁决落库」比「加一个确认布尔」强在哪**：布尔位只记录「人看过」，不产生数据；裁决把人工判断变成
   `conclusion + conclusion_source + judge_basis`，**报告实时聚合的事实自此就是确定值**，导出/列表/打印全链路一致。
5. **回归保护的验收要防「同一语义两套形态」**：面包屑这类「两处都有」的 UI 债，正确的收敛方向是**同源生成的一处**
   （顶栏=菜单树驱动），而不是按页面逐个复制层级；否则页面增删菜单必然漂移。

## 4. ⚠️ 踩坑记录（现象 → 根因 → 处理）

1. **沙箱后台进程被杀**：`nohup cmd &` 起的服务端会在工具调用结束后被杀（日志空、端口无监听）。
   → 正解：Bash 工具 `run_in_background=true` 直接跑命令（不加 nohup/&）。本轮前后端均如此托管。
2. **从 Bash 调 PowerShell 被安全策略拦截**（"Invoking PowerShell from Bash bypasses security checks"）：
   停进程必须用 PowerShell 工具直接发 `Stop-Process -Id <pid> -Force`（Git Bash 的 taskkill/`powershell.exe -Command` 都不行）。
3. **`curl -o /dev/null` 在本沙箱偶发 exit 23（write error）并中断 `&&` 链**：探测端口/接口时不要用 `-o /dev/null` 串链，
   用 `| head -c` 或分开执行。
4. **Excel 报告导出核验的「假阳性」**：搜到「色泽正常」≠ F22 未修复——河蟹样品的**「色泽」感官项**结果本来就是
   「色泽正常」（合法值）；要按「样品 × 项目」对定位（菌落总数=1200 才是验收点）。
5. **F10（固定列浮于弹窗遮罩）本机未能复现**：工程师用真实浏览器测得 overlay 父级=页面根、z=2011、fixed 单元格 z=2、
   `elementFromPoint` 命中遮罩、click 被拦截。处置：如实记录「未复现」，并按报告建议做 `append-to-body` 加固
   （保证 overlay 永远挂 body，任何祖先 transform/filter/backdrop-filter 都困不住它），**不把「加固」包装成「修复」**。

## 5. 📊 进度

- 项目总进度：**99% → 99%**（缺陷修复与质量提升轮；无新增业务范围）。
- 缺陷侧：P0 1/1、P1 4/4、P2 4/4、P3 20/20 全部落地（含数据修复 2 项）；性能观察 2/2 复测定性（非缺陷）。
- 质量门禁：后端 **261/261**（新增 9）；前端 vue-tsc / eslint / vite build 全绿；E2E 证据见 `_logs/`。

## 6. 可复用结论（值得沉淀）

- **旧库迁移质检三板斧**：①字节级扫描（HEX 正则 C1 控制符签名 C2 80~C2 9F，比 LIKE '%å%' 更可靠，不受客户端显示影响）；
  ②「最后分隔符 + 白名单」切分 + 未命中落 NULL（不静默错切）；③迁移脚本末尾 SIGNAL fail-loud 断言。
- **「同值多义」检测**：`CHAR_LENGTH(TRIM(name))=1 AND unit LIKE '%,%'` 这种**组合签名**能精确定位「首分隔符切分」类缺陷。
- **前端 token 静默续期的可测法**：临时把 `lims.jwt.access-token-ttl` 压到 35s 重启后端 → Playwright 等待过期 →
  操作 → 用 page.on('response') 断言 `/auth/refresh` 200 + 原请求重放 200 + URL 未变；负向：写坏 refreshToken → 断言回登录页。
- **服务端「未命中路径」的异常形态**：Boot 3.x 静态资源映射下是 `NoResourceFoundException`（Spring 6.1+），
  不是 `NoHandlerFoundException`；不单列处理就会落 `Exception` 兜底变成 500+堆栈。
- **PowerShell 5.1 直调 JSON API 的字符集坑**：响应头无 charset 时按 ISO-8859-1 解码 → 中文写回即双重编码。
  造数/调接口必须用 curl / Node / Python（本轮 `_logs/walk-demo001.mjs` 即可复用模板）。
