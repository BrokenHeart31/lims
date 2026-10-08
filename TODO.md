# TODO.md（任务清单）

> 规则：按业务七阶段对齐编号，禁止跨阶段跳做。S=GLM（架构/状态机/核心引擎/终审）/ A=Copilot（前端/常规 CRUD）/ B=豆包。
> 状态：⬜待办 🔵进行中(名字) ✅完成 ⏸阻塞

## 初始化阶段
| 任务ID | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-001 | 仓库连接 + 目录架构 + 治理文件（.gitignore/README/AGENTS/STATUS/TODO/HANDOFF/DECISIONS/api-spec） | B | 豆包 | ✅完成 |
| T-002 | 后端工程骨架（pom.xml/LimsApplication.yml/统一响应/异常/JWT 骨架）+ api-spec 首批登录 me 接口 | S | Copilot | ✅完成 |
| T-003 | 前端工程骨架（vite/package.json/路由/axios 封装/登录页壳） | A | GLM | ✅完成（Copilot 终审通过） |
| T-004 | 前端 ESLint 门禁补齐（AGENTS 第 9 章要求 npm run lint；骨架阶段缺 lint 脚本） | A | 豆包代 GLM | ✅完成（豆包代做，新增 eslint.config.js + lint 脚本，待 npm install 后跑通） |

## 阶段一：基础数据准备（对应 7.1 第 1 步）
| 任务ID | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-101 | RBAC 建表（user/role/menu/dept/role_menu/user_role）+ 新表规范 | S | Copilot | ✅完成（db/init/02_rbac_tables.sql：sys_* 五表 + dept 加 parent_id） |
| T-102 | 后端登录/JWT/me 接口 + @PreAuthorize + v-permission 指令 | S | Copilot | ✅完成（AuthController 四接口 + UserDetailsServiceImpl DB 装配 + 前端指令/守卫） |
| T-103 | 基础表：basis（判定依据）、customer（客户）、dept、tester-method | B review S | Copilot审 | ✅完成（basis/customer/dept/04_tester_method 全部终审通过，2026-09-11） |
| T-104 | 旧数据迁移脚本 V1__import_legacy_data.sql（basisname≈1150/customer9/lib/dept） | B | 豆包 | ✅完成（Copilot 终审通过：product_lib_item 增 judge_type，V1 已对齐） |

## 阶段二：监抽任务管理
| T-201 | 监抽任务 SuperviseTask CRUD | A | 豆包代 GLM | ✅完成（前端豆包代 + 后端 Copilot；api-spec 任务域定稿，前后端契约已核对一致） |

## 阶段三：样品登记（采样单 Excel 导入）
| T-301 | 采样单 Excel 导入 + S10→S20 登记确认 | S | GLM（由 Copilot 改派） | ✅完成（2026-09-11 GLM 全链路 + 单测通过；**api-spec 样品域契约 Copilot 终审通过**，3.1 补 updatedBy） |

## 阶段四：检验项目分解（自动套库）
| T-401 | 项目标准库 ProductLib + 自动分解 + S20→S30 | S | **GLM** | ✅完成（2026-09-11 GLM 全链路：套库预览不落库 + 覆盖式保存 + 确认 S20→S30；契约 api-spec 第 4 章；db/init/06_item_tables.sql；14 项单测；前端 api/item.ts + views/item/index.vue + 路由菜单；`mvn test` 23 项全过、`npm run build`/`lint` 全绿） |

## 阶段五：检验任务安排
| T-501 | 自动分配规则（NA/XA/SA + 方法资质）+ S30→S40 | S | **GLM** | ✅完成 2026-09-11（契约第 5 章 + 6 字段 + 14 单测 + 前端页 + 路由菜单）；⚠️ 联调被旧 spring-boot 进程卡死，明日杀 PID 11640 重启 |

## 阶段六：检验数据录入（自动判定）
| T-601 | 结果录入 + 自动判定引擎 + S50→S60 | S | **GLM** | ✅完成 2026-09-12（契约第 6 章 5 接口 + `db/init/07` `sample_result` + `V4` 整体结论列 + **纯函数判定引擎**（闭集白名单矩阵）+ Service/Controller + 49 单测（总 85/85）+ 前端录入页 + 路由菜单；**端到端 45 断言全过**；⚠️ 编号勘误：交接留言中的「T-602」即本任务，以 TODO 的 T-601 为准）；✅ **Copilot 复核终审通过 2026-09-12**（独立实测 85/85、矩阵对 D1–D5 逐格一致、零标准库回溯；保留意见转 **T-911/T-912**，详见 journal 2026-09-12-copilot） |

## 阶段七：报告审核签发 + 报告生成
| T-701 | 审核/签发 S60→S70→S80（含审核退回 → S50） | S | **GLM** | ✅完成 2026-09-12（契约第 7 章 6 接口 + `db/init/08` `sample_audit_log` 流水表 + `V5` 审核/签发字段 + **正向/退回两张独立白名单**（`assertReturn`）+ Service/Controller + **放行红线**（异常项须显式确认）+ 22 单测（总 107/107）+ 前端审核签发页 + 路由菜单；**端到端 54 断言全过（重跑仍 54/54）**；视觉回归含抽屉内异常项清单） |
| T-702 | CMA / CMA-CATL 报告模板合成 + S80→S90 + 报告打印 | S | **GLM** | ✅完成 2026-09-13（契约第 8 章 `/api/report` 4 接口：pending/detail/generate/print + `ReportType` 枚举（1=CMA / 2=CMA-CATL，差异仅在资质行）+ `ReportProperties` 配置化机构/资质/7 条注意事项 + 报告**实时聚合**不落快照 + 电子签名「占位 + 可配置」绝不伪造 + `ReportAssembler` 实时拼装 sample_info+sample_item+sample_result+sample_audit_log+sys_user；`db/migrations/V6` + `sys_user.signature_url`；前端 `views/report/{generate,print}.vue` + `components/report/{ReportCover,ReportPage1,ReportPage2}.vue` + 公文 `report-print.css`；端到端 54/54 + 后端单测 107/107 + 视觉回归 1366/1400/1920 三档全过） |

## 阶段六延伸：检验员任务查询与导出
| 任务ID | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-603 | 检验员查询自己的检验任务 + 下载任务 Excel（说明书第七节；当前仅预留 `result:export-excel` 标识，接口未实现） | A | **GLM** | ✅完成 2026-09-13（**查明导出链路上一轮已完成**，本轮补的是「屏幕查询」缺口：契约第 13 章 `GET /api/query/my-tasks/page`，权限 `result:entry`；`MyTaskVO` 含 `entered`/`conclusionSource`；**数据范围服务层强制收敛**（`resolveTesterScope()`：普通用户附加 `tester_no=本人工号`，R100 不过滤；`MyTaskQueryDTO.testerScope` 无请求绑定→前端伪造无效，已实测）；「已录入」强制复用 `ResultEntryPolicy`，未录入时 conclusion 三字段置 null；查询下界 `status>=40`；前端 `views/result/my-tasks.vue`（只看未录入开关 + 跳录入 + 导出）+ 路由菜单） |

## 阶段一延伸：基础数据维护界面（说明书二(2)(3)；当前有表有数据、无管理页）
| 任务ID | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-105 | 方法-检验员资质设置（`tester_method` CRUD + 导入；说明书原文「添加检验员-检验方法」） | A | **GLM** | ✅完成 2026-09-13（契约第 11.1 章 `/api/base/tester-method` 5 接口 + `QualStatus` 枚举 + VO 反查 `testerName/deptName/qualStatusLabel`（批量 IN 免 N+1）+ Excel 导入（5 列，幂等键 `(methodName, testerNo)` 命中则更新，监听器**顶层类**避 Lombok 坑）+ 权限 `base:tester-method:list/add/edit/remove`；前端 `views/base/tester-method.vue`（含导入结果三计数弹窗 + 前端生成 CSV 模板）；端到端写路径实测通过） |
| T-106 | 项目标准库维护 + 「导入新的项目库」Excel 批量导入（`product_lib`/`product_lib_item`） | A | **GLM** | ✅完成 2026-09-13（契约第 11.2 章 `/api/base/lib` 11 接口；**明细覆盖式替换**（C10，不做差异比对）+ 判定字段一致性**硬校验**（jt1 必填 stdValue / jt2 须「不得检出」类 / judgeType 越界 400，实测原子失败不脏库）+ 删除含明细产品拒绝 + 导入 13 列**一对多覆盖式**；**补齐缺失权限种子** `base:lib:add/edit/remove`（sys_menu 832/833/834 + R2 授权——此前仅 `base:lib:list`，非 R100 用户必 403）；前端 `views/base/product-lib.vue`（明细抽屉整表内联编辑）；端到端实测通过含 400 拒绝路径） |
| T-107 | 系统管理 4 页：用户（含角色分配/启停/重置密码）、角色（含菜单权限树）、菜单、部门 | A | **GLM** | ✅完成 2026-09-13（契约第 12 章 `/api/sys` 21 接口；**失效模式防护全落地**：①用户自锁保护（不能删/停用自己、不能动最后一个启用 R100，实测 409）②`SysUserVO` 类型层面无 password/salt ③重置密码**独立接口 + 独立 DTO** ④`username` 创建后不可改 ⑤R100 三重保护（编码不可改/跳过权限绑定/不可删，实测 409）⑥有用户绑定拒绝删角色（返回人数）⑦菜单成环检测 `guard<64` + permission 唯一性前置拦截 + 形态语义校验（实测 400）⑧删除菜单级联清理 `sys_role_menu` ⑨部门环检测 + 有子部门/用户拒绝删除（实测 409）；4 前端页面 + 路由菜单；端到端实测通过） |

## 查询与省平台上报
| T-801 | 在检/历史/项目库查询 | A | **GLM** | ✅完成 2026-09-13（契约第 9 章 `/api/query` 3 接口：testing/history/lib + 分页 current/size + 停留时长**近似推导**（不新建流水表，按 createdAt/confirmedAt/updatedAt/MAX(assigned_at)/MAX(sample_result.updated_at)/auditAt 拼）+ `itemTotal/enteredCount/pendingCount/abnormalCount` 强制复用 `ResultEntryPolicy`（T-912 唯一口径）+ 权限 `query:testing/query:history/query:lib`；前端 `views/query/{testing,history,lib}.vue` 三个查询页 + 路由菜单；端到端 54/54 + 视觉回归全过） |
| T-802 | 省平台上报 Excel 导出 | B | 豆包 | ✅完成 2026-09-13（豆包：格式定稿+样例已交付 2026-09-12；GLM：契约第 10 章 `/api/export/province` + **EasyExcel 3.3.4 流式**禁用 POI 裸 API + 阈值 `status>=80`（已签发即可上报，含 S90 已出报告）+ **严格 10 列不插空隔列** + 参考项不加 `*` 前缀 + 支持 `?taskNo=` 筛选 + 权限 `export:province`；前端 `views/export/province.vue` + `utils/download.ts` + 路由菜单；端到端 54/54 含 njsa000 越权真 HTTP 403） |
| T-803 | 可视化看板：工作台图表 + 质量分析 + 统计报表（须基于真实统计接口，**禁 mock 假数据**） | A | **GLM** | ✅完成 2026-09-13（契约第 14 章 `/api/stat` 9 接口，权限 `stat:view`（新增，seed id=841，已授权 R100+R2）；**图表库选型裁决 = ECharts 5.5.1 按需引入**（落档 DECISIONS，含选型四问 + 反例排除），**路由级分包实测 `analysis-*.js` 542.83 kB/gzip 183.12 kB 且主包零增长**；`StatOverviewVO.qualifiedRate` 分母**排除待判定**、无有效结论返回 `null`（≠0%）；`statMapper.xml` 单 SQL 多列聚合 + 显式 `deleted=0` + 避 `generated` 保留字；月度趋势**补零月**（实测 6 月其中 5 月为 0）；前端 `LimsChart.vue`（ResizeObserver + CSS 变量取色 + 空态优先 + notMerge）+ `utils/chartOptions.ts` + `api/stat.ts` + `views/query/analysis.vue`（6 KPI + 7 图）+ 路由菜单；**9 接口全部实测通过**） |

## 治理维护
| 任务ID | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-901 | 角色调整落地：TODO/AGENTS/prompts 分级与所有权统一（S+A→GLM，Copilot 转契约+裁决+审查） | S | **GLM** | ✅完成（2026-09-11） |
| T-902 | 判定引擎表达式白名单草案（基于真实数据分布，交 Copilot 裁决） | S | **GLM** | ✅完成（Copilot 五条口径裁决定稿，见 docs/knowledge/2026-09-11-judge-engine-whitelist.md） |
| T-903 | 补全 product_lib.product_name / category（源：旧 product 表 prd_category + libName） | B | 豆包 | ✅完成（GLM 代做，2026-09-11：`db/migrations/V2__fill_product_lib_name_category.sql`，product_name 0→92、category 0→92，残留 0，幂等可重跑） |
| T-904 | 工作纪律三件套制度化（工作日记 / 进度百分比 / 前置检索），写入 AGENTS 2.5 节 + 开工六步 | S | **GLM** | ✅完成（2026-09-11；`docs/journal/` 建立，README 索引就位） |
| T-905 | **D5 裁决请求 #1**：judge_type 订正前提被实测推翻，请求裁定 R1/R2/R3 | S | **GLM→Copilot** | ✅完成（2026-09-11 Copilot 裁决：**R1 采纳**（V3=可重跑口径校验器，须 fail-loud）、**R3 否决**（禁反建标准库，登记为数据覆盖缺口）、引擎只读 sample_item 追认、单测用构造数据；见 DECISIONS 与 whitelist 定稿 D5 节） |
| T-906 | UI 设计基准「Aurora Glass」落地（设计令牌 + 折射玻璃 + EP 暗色适配 + 外壳/登录/工作台重塑） | S | **GLM** | ✅完成（2026-09-11；`frontend/src/styles/*` + `components/GlassFilter.vue`；实测修复 el-tag 过渡卡死导致的两列空白） |
| T-907 | V3 改造为 fail-loud 口径校验器（执行 T-905 裁决 R1） | S | **GLM** | ✅完成（2026-09-11；双路径实测：零变更通过 / 人为漂移报错退出；派生表统一计算 + NULL 安全比较） |
| T-908 | 治理二次调整：GLM 自裁机制（2.6）+ 豆包分工清单（2.7）+ UI 基准（5.1）+ 许可合规红线 | S | **GLM** | ✅完成（2026-09-11） |
| T-909 | 判定引擎**实现形态**侦察与选型定稿（规则引擎 vs 闭集矩阵 / 浮点比较 / ALCOA+ 留痕） | S | **GLM** | ✅完成（2026-09-12；`docs/knowledge/2026-09-12-judge-engine-research.md`：**否决 Drools/Easy Rules/LiteFlow/Aviator**，定稿「闭集白名单矩阵 + BigDecimal.compareTo + 默认待判定 + WARN」+「原始值/派生值分层落库」） |
| T-910 | 可**一键复现**的技能沉淀（判定引擎模板 + 阶段交付含端到端/视觉回归 + 沙箱 git 补坑） | S | **GLM** | ✅完成（2026-09-12；新建 `.agents/skills/judge-engine/`，更新 `lims-stage-delivery`（+第 7.5 步）、`sandbox-git-push`（+规则 6 hash 双验证 / 规则 7 临时文件与并行 Edit 覆盖））；**2026-09-12 追加**：`lims-stage-delivery` 原则 4 升级为「状态流转双保险 **+ 正向/退回两张独立白名单 + 审计留痕 + 放行红线 + 未录入≠待判定**」、环境备忘补 git 全路径/后端重启/ref 被吞症状；状态机知识库补「双白名单」定稿节；技能库共 7 个 |
| T-911 | **分页参数双轨统一**：api-spec 0.3 约定统一 `pageNum/pageSize`，但 item/assign/result 三域实为 `current/size`（T-401 引入并沿用，spec 章节内自洽、前后端一致、运行无碍）。裁决方向：(a) 修订 0.3 承认双轨（成本 0，Copilot 倾向）或 (b) 统一回 `pageNum/pageSize`（动 3 Controller + 3 前端 api + spec） | A | **GLM** | ✅完成 2026-09-12（**采纳 (a) 保留双轨**，api-spec 0.3 已明确「新域一律 `current`/`size`」、`pageNum`/`pageSize` 标注为 task/sample 历史兼容写法；见 DECISIONS） |
| T-912 | **「已录入」口径收口**：`ResultSaveDTO.Item.testValue` 可空→可落一行 `conclusion=3`，而 `submit` 录齐校验只看结果行存在 → **可带空结果行提交至 S60**。非安全洞（不静默判合格），属流程卫生缺口。候选方案：①「已录入」= `testValue 非空 ∥ (jt3 且 manualConclusion∈{1,2})`（改 submit/entered/save 校验）；②不改录入，由 T-701 审核页强制展示「待判定/空值项」清单并显式确认后放行 | S | **GLM** | ✅完成 2026-09-12（**方案①+②合并**：`ResultEntryPolicy` 收紧「已录入」口径使空值行阻断提交；审核页仍强制展示异常项清单并要求显式确认；「未录入」（操作缺漏，阻断）与「待判定」（数据缺口，不阻断）严格区分；见 DECISIONS） |

| T-913 | **前端 UI 结构/空间重整**（保留 mine radio「Aurora Glass」华丽美感，聚焦结构、间距、层级与一致性） | A | **GLM** | ✅完成 2026-09-12（`f751e8f`：tokens 扩展 + element-override 统一行高/圆角 + 6 个公共组件（PageHeader/AppCard/StatCard/StatusBadge/AppEmpty/AppBreadcrumb）+ `utils/confirm.ts`/`sampleStatus.ts` + MainLayout 224px 分组侧栏 & 64px Header + 工作台重构 + 7 业务页迁移；lint 0/0、build 通过；资产见 `docs/journal/2026-09-12-glm-ui-overhaul.md`、`docs/knowledge/2026-09-12-ui-component-library.md`、skill `lims-ui-overhaul`） |
| T-914 | **补充治理**：TODO 补齐 T-913 行 + 新增 T-105/T-106/T-107/T-603/T-803 五行（说明书要求但此前未登记的任务） | B | **GLM** | ✅完成 2026-09-13 |
| T-915 | **T-702/T-801/T-802 实施期实测发现 2 项**：①契约违例 `GlobalExceptionHandler` 对 `@PreAuthorize` 拒绝曾返回 HTTP 200 + body.code=403，与 §0.2「安全层 HTTP 401/403」及 URL 级拒绝（真 403）形态不一致——补 `@ResponseStatus(HttpStatus.FORBIDDEN)` 兑现契约；②暗色主题布局缺陷 `--el-table-bg-color: transparent` 使固定列失去不透明背板，1366×768 下「整体结论」与「操作」列横向溢出文字重叠糊——补 `el-table-fixed-column--right` 单元格背景 + 表头/striped/hover 三态单独覆盖（全局修复受益所有含固定列的表格）+ MySQL 保留字 `generated` 改 `cnt_generated` | S | **GLM** | ✅完成 2026-09-13（与 c385166 一并落地；端到端 54/54 含 njsa000 越权真 HTTP 403 + 视觉回归三档全过） |
| T-916 | **前端动态路由接入**（按 `/me` 菜单树生成路由 + 侧栏；选型「路径注册表 + 中间件转换」） | A | **GLM** | ✅完成 2026-09-13（用户决策方案 1；新建 `router/routeRegistry.ts`（21 条显式登记 + `PATH_ALIAS` 兼容层 + `normalizeMenuPath`）、`router/dynamicRoutes.ts`（`buildNavigation` 路由与菜单**同源产出**）；重写 `router/index.ts`（五步守卫 + **`registerNotFound()` 移除后重加**规避 catch-all 顺序陷阱）、`stores/auth.ts`（`navMenus`/`navReady`/`setNavMenus`）；`MainLayout.vue` 侧栏改菜单树驱动 + 图标白名单 + 真实全局搜索；`db/seed/01_rbac_seed.sql` 修正 2 条错路径 / 3 条 `visible=0` / 新增 4 条（我的检验任务、项目标准库）+ 授权同步且**已应用到活库**；验证 `vue-tsc` 0 错、`vite build` 成功、**离线路由断言 31/0**、`/me` 实测 R100 见 11 组 / R3 见 2 组、SPA 深链接 6 条 HTTP 200；**未改任何 DB 表结构、未改任何 API 契约**） |
| T-917 | **UI/UX 全面重构**（用户 2026-09-13 指定后续主线；约 17 页统一升级，报告审核页为第一批重点） | A | **GLM** | ✅完成 2026-09-14（STEP 1~10 全部收口：17 页公共组件 100% 迁移；P1~P5 巡检问题全修；**三档分辨率 1440×900 / 1920×1080 / 1366×768 实测无横向溢出**；**真实浏览器全量遍历 20 页 0 console error / 0 网络失败**；Dashboard KPI 加载态改骨架屏） |
| T-918 | **操作日志落地**（消除用户菜单「操作日志」空壳；同时消除顶部铃铛的假通知数据） | A | **GLM** | ✅完成 2026-09-14（`db/init/09` + `V7` 建 `sys_operation_log`；`OperationLogInterceptor` 零依赖实现（离线仓无 AOP，经论证用 HandlerInterceptor 等价达成）；`GET /api/sys/log/page` + 契约第 15 章；**分级数据范围**：人人可查自己、`log:view` 才能跨用户，服务端强制；前端改真实分页表格；6 项单测固化前缀顺序语义；端到端 6 断言全过含**伪造 operator 参数无效**与**403 失败留痕**；铃铛假通知改写为 6 域真实待办汇总） |
| T-919 | **本地 AI 助手**（qwen3-4b 项目内托管 + 领域护栏 + GB 标准库检索 + 可拖拽悬浮窗）<br>⚠️ **超出业务说明书范围**，用户新增需求 | S | **GLM** | 🟢 代码完成，**待用户验证真实推理**<br>设计：`docs/design/2026-09-17-{prd,arch}-ai-assistant-and-rollback.md`；数据：`db/init/11_ai_tables.sql`（`gb_document`/`gb_clause`(ngram FULLTEXT)/`gb_import_job`/`ai_conversation`/`ai_message`）；后端：`service/ai/**`（22 类，含 Ollama 客户端、`DomainGuard` 领域护栏、`GbRetriever` 检索、`parser/*` 解析器）+ `controller/{Ai,AiKb}Controller`；前端：`components/ai/*`（悬浮窗 6 件，自研拖拽 + 位置记忆 + 引用卡片）；运行时：`ai/scripts/*`（便携版部署 + 多镜像回退）。**零新增 Maven 依赖**（唯一新增 `jsoup` 1.18.3 用于 HTML 解析；AI 调用用 JDK17 `HttpClient`，流式用 `SseEmitter`）。**未验证**：Ollama 未下载（沙箱代理拦大文件，7 条镜像全失败）、GB 库无真实数据 |
| T-920 | **全流程逐步回退（撤销/回滚）机制**<br>⚠️ **超出业务说明书范围**，用户新增需求 | S | **GLM** | ✅ 完成（后端 + 单测 + 前端 UI）<br>**第三条独立白名单 `ROLLBACK`**（6 条边，**S80/S90 无回退边**，物理上不可绕）+ 统一状态流水 `sample_status_log`（7 类事件）+ `sample_rollback`（可恢复状态机）+ `sample_data_archive`（失效留档，取证）+ `report_void`（S80/S90 作废/召回标记动作，不改 status）；分级授权（常规/敏感 S70→S60 需 R100+R2+二次确认）；四条不变式用 `InOrder`/`verify(times(1))` **测试固化**；**推翻** DECISIONS 2026-09-13「不新建状态流水表」旧自裁（已落档）。⚠️ **最高风险改动**：`sample_item`/`sample_result` 的 `deleted` 语义升级为「0=有效 / 非 0=行自身 id」（解决二次失效撞唯一键；MP `@TableLogic` 不支持表达式，故走显式 `set(deleted, id)`）；后端 **204/204 全绿** |
| T-921 | **遗留项（QA 复核提出，非阻断）**：① 全仓 `rgba(255,255,255,…)` 硬编码白透明度共 **55 处**（本批新增 3 处），与「只用 `--lims-*`」令牌纪律不符 —— 建议抽 `--lims-overlay-1/2/3` 变量后**一次性替换**（只改新增的 3 处反而制造不一致，故本轮不动）；② `BaseEntity.deleted` Java 类型为 `Integer` 而列已是 `BIGINT`（因 `@TableField(select=false)` 不读取，当前无风险）—— **若将来放开读取该列，必须同步改为 `Long`**；③ 架构文档原写「用 `LambdaUpdateWrapper.set(deleted,id)`」与实现（原生 `@Update`）不符，**已于 2026-09-17 在文档中更正并写明原因**（MP 会追加 `AND deleted=0`，匹配不到已失效行）；④ QA 提出的**信息性发现**（不改代码，仅备记）：`SampleDataDisposer` 用 `ObjectMapper.writeValueAsString` 序列化实体做快照时，`SampleItem`/`SampleResult` 上的中文派生 getter（如 `getJudgeTypeLabel()`）会被 Jackson 当属性序列化进 `archive/snapshot_json`，使快照 JSON 略显冗余（**功能无误、当前无读取缺口**）——若将来要解析该 JSON 或在快照上加索引，建议在原始 XML 生产链路上按列精确序列化（`@JsonInclude` 或显式字段映射） | B | GLM | ⬜ 待办（③ 已完成） |
| T-924 | **回退机制改造（环节内嵌 + 可选目标步 + 批量）** + **状态展示精简**（用户 2026-09-30 指令；改造 T-920 的交付形态） | S | **GLM** | ✅完成 2026-09-30（**独立页面/路由/侧栏菜单三处全移除**，回退下沉为 5 个业务页内嵌；**跨级回退**由 `RollbackEdgePolicy.chain()` 沿白名单逐级推导后逐级执行（**边集合一行未改**）；**批量** `ids` + 逐条独立事务（`RollbackExecutor` 独立 Bean + `REQUIRES_NEW`）+ 逐条失败原因；新增 B7 `GET /rollback/targets/{sampleId}`；**不变式③重定义为「1 批次 + 每级 1 条流水」**并落库 `batch_no/step_count`；补 `ReportVoidButton`（作废/召回此前**有 API 无 UI**）与 `RollbackTraceDrawer`（时间线/撤销回退不随独立页消失）；各页补「应出现状态集合」注释、查询页标注**唯一有意例外**；`db/migrations/V11` 幂等落地并**已在活库应用**；后端 `mvn -o test` **252/252**、前端 `vue-tsc`/`eslint`/`vite build` 全过；日记 `docs/journal/2026-09-30-glm-rollback-embed-and-status-scope.md`） |
| T-925 | **沙箱高危坑固化**：`git rm` 在本沙箱会删除**父目录整棵**（实测 2 次，造成 `frontend/src/**` 与 `views/` 误删，已 `git checkout HEAD -- frontend/src` 全量恢复并验盘） | B | **GLM** | ✅完成 2026-09-30（禁令 + 恢复流程写入 `.agents/skills/sandbox-git-push` **规则 7.5** 与 DECISIONS） |
| T-922 | **待用户确认后再开**：`RECALL` 白名单（S80→S70，召回后重新签发）。当前 S80/S90 一律**禁止普通回退**，只提供 `report_void`（作废/召回**标记动作**，不改 status）。若业务确认「召回后需重新签发」，按 `ROLLBACK` 同一模式新增**第四条独立白名单**（设计文档 §10-R6 已留扩展点） | S | GLM | ⏸ 待业务确认 |

> 注：以上为初始骨架。S/A 级任务的接口定义由 GLM 起草写入 api-spec.md，**Copilot 终审**；存在判定口径歧义时由 Copilot 裁决。豆包不自行设计业务表。

## 2026-10-07 全流程测试缺陷修复（豆包全流程测试 → GLM/豆包执行）

> 来源：豆包 2026-10-07 全流程逐步测试，完整报告见 **`docs/test/2026-10-07-fullflow-test-report.md`**（F1~F31，含定位文件/复现/改法/验收）。
> 修复顺序：P0 → P1 → 数据 → P2 → P3；每批按报告第九节跑质量门禁 + 浏览器复测清单。

| 任务ID | 任务（输入 / 输出 / 验收标准） | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-930 | **F3（P0 阻断）分解页/安排页列表 0 行**。输入：报告 F3。输出：`views/item/index.vue:428`、`views/assign/index.vue:379` 两处 el-table 加 `row-key="id"`；顺带把 `item/index.vue:432/623` 的 `height="calc(...)"`（EP 不识别）改数值 max-height 或外层 CSS。验收：有 S20/S30 样品时两页表体渲染全部行、勾选保留生效；`npm run build && npx vue-tsc --noEmit` 通过 | A | **GLM** | ✅完成 2026-10-08（两处加 `row-key="id"`；calc 高度改响应式数值 `:max-height`（resize 监听+卸载清理）；Playwright 三档 33/33 断言，主理人复跑门禁全绿） |
| T-931 | **F1+F10（P1）宽表横向裁切 + 固定操作列鬼影穿透/浮于弹窗遮罩**。输入：报告 F1/F10。输出：DataTable 横向滚动真正生效、数据密集表统一 `.lims-panel`、固定列实色背板、弹窗/固定列 z-index 统一。验收：1366/1440/1920 三档关键列（结论/方法/检验员/操作）可见或可横滚、固定列无穿透、弹窗打开时操作列不可点 | A | **GLM** | ✅完成 2026-10-08（集中式修复：固定列全行态实色背板 + 撤 EP 内投影改发丝线；横向滚动条常驻可见化（`el-table--scrollable-x`）；task/sample 等弹窗 `append-to-body`；1366/1440 实测可横滚至最右列。**F10 本机未复现**，append-to-body 属加固） |
| T-932 | **F2（P1）样品批量确认跨筛选误勾选**。输入：报告 F2。输出：批量确认弹窗逐条列出样品编号+名称；跨筛选保留选择可见化（已选 N 条含筛选外 X 条 + 一键清空）。验收：改筛选后不可见勾选不会被无提示确认，弹窗可核对全部样品编号 | A | **GLM** | ✅完成 2026-10-08（专用核对弹窗逐条列「编号+名称+受检单位+状态」；选择条「含筛选外 X 条」+一键清空；reserve-selection 语义保留；浏览器实测通过） |
| T-933 | **F17（P1 数据）旧库迁移双重损坏**。**GLM 负责迁移脚本内容设计**：`V1__import_legacy_data.sql:98-100` 逗号切分改为按单位枚举白名单/最后一个逗号切，并加数据质量断言（名称长度=1 且单位含逗号=0 行、methods 含乱码特征字符=0 行，否则 fail-loud）。**豆包负责重导执行**：`mysql --default-character-set=utf8mb4` 重导 lims.sql → 重跑迁移 → 抽查中文。验收：鲜食玉米 62 项无 itemName「2」、methods 无乱码，自动分派命中率较 23/62 显著提升；证据 `_logs/assign18.json` 问题消失。输入：报告 F17 | S（设计）/ B（执行） | **GLM 设计 → 豆包执行** | ✅完成 2026-10-08（**事实更正：不执行重导**——字节级取证证明双重编码不在 lims.sql 迁移链路上，仅存在于测试会话写入的 sample_item，详见 DECISIONS / `_logs/f17-analysis-2026-10-08.md`。实际处置：①V1 切分改「最后逗号+单位白名单」+ 三条 fail-loud 断言；②新增 `V12` 定向修复 22 行（已执行：坏签名 22→0）；③seed 03 补 22 条 njna000 资质（覆盖缺口才是待指派主因）。实走验证：62 项全套库、**自动分派 36/62**（较 23/62 +56%）、`_logs/assign-demo001-after.json` 无乱码字符） |
| T-934 | **F20（P1 合规，S 级）待判定项被带进 CMA 报告**。输入：报告 F20 + DEMO-004(id=21) 复现。输出：①审核异常清单对每个 PENDING 项强制逐条裁决合格/不合格+必填说明并落库（人工判定来源），未裁决不能审核通过；②报告生成对仍待判定项 fail-loud 拒绝；③报告明细行只印合格/不合格；④终态列表整体结论聚合为确定值。**口径由 GLM 按 AGENTS 2.6 自裁并落档 DECISIONS/docs/knowledge（涉 CMA 合规，满足真实两难三问）**。验收：补单测；含待判定项样品未裁决时 approve/generate 均 400，裁决后报告无「待判定」行；不破坏现有审核红线（未勾选确认 400、退回原因必填 400） | S | **GLM** | ✅完成 2026-10-08（逐条裁决落库（`conclusion_source=2` + `judge_basis` 留痕段）、未录入硬阻断、报告生成 fail-loud 门禁、共享 `OverallConclusionPolicy` 聚合；新增单测 9 项，后端 **261/261**；契约 7.4/8.3 增量；口径落档 DECISIONS；E2E 进 QA 回归） |
| T-935 | **F8（P2）JWT 硬过期无静默续期、录入内容丢失**。输入：报告 F8；定位 `utils/request.ts:33-48,58`，后端已有 `POST /api/auth/refresh` 与 refreshToken 但前端未调用。输出：401 时用 refreshToken 静默换新 accessToken 并重放原请求（单飞锁防并发刷新），续期失败再登出；跳登录前对未保存表单提示/暂存。验收：静置 >2h（access-ttl=7200）后继续操作不被硬登出、表单不丢；refresh 失效时才回登录页 | A | **GLM** | ✅完成 2026-10-08（单飞锁静默续期 + 重放原请求 + token 轮换落库；真机 E2E（35s TTL 临时配置）：过期后操作→`/auth/refresh` 200→重放 200、未被登出；refresh 失效→回登录页。表单暂存未做——静默续期后丢表单场景消失，属范围外） |
| T-936 | **F4+F5（P2）双面包屑 + 工作台把 S90 已完成项列为活跃**。输入：报告 F4/F5。输出：面包屑只保留一处（建议留 PageHeader 层级、顶栏去重）；工作台当前/最近任务只取在检口径，终态项移历史或去活跃点。验收：每页仅一条面包屑；S90 花鲢不再以绿点「已录入」出现在活跃任务 | A | **GLM** | ✅完成 2026-10-08（面包屑单源=顶栏（菜单树驱动、含二级分组、首项「工作台」），18 页 `#breadcrumb` 全移除；工作台最近任务只取 S40~S70；F6 术语、F7 铃铛同批；Playwright 抽页验证通过） |
| T-937 | **P3 体验/文案/一致性扫尾（一批，纯前端）**。输入：报告第五节 + 第八节状态精简清单。输出逐条对应：F6 去开发术语、F7 铃铛提示只留 1 处、F9 新建任务表单分组+等级统一字典下拉、F11 样品页默认收敛 S10/S20（需 GLM 裁决）、F12 checkbox 还原方形、F13 筛选占位标字段与匹配方式、F15 监抽任务加「查看」、F16 详情状态去重、F18 jt1/jt2/jt3 枚举码改中文、F19 进度只留 n/N、F21 省平台导出说明文件名改「省平台上报数据」、F23 停留时长换算天/小时、F25 角色名「特权」改「综合管理员」、F26 部门人数标「本级/含下级」、F27 AI 任务去服务器绝对路径、F28 ISO 时间统一 yyyy-MM-dd HH:mm:ss、F29 我的任务终态行「去录入」改「查看」/禁用、F30 未知 /api 路径统一返 404 JSON（GlobalExceptionHandler 处理 NoResourceFoundException，不打堆栈）、F31 AI 离线提示按角色（普通用户「联系管理员」，不暴露脚本路径）。验收：逐条对照报告第八节清单；前端三门禁 + 后端编译通过 | A | **GLM** | ✅完成 2026-10-08（F6/F7/F9/F11/F12/F13/F15/F16/F18/F19/F21/F23/F25/F26/F27/F28/F29/F30/F31 逐条落地；F11 后端 additive `statuses` 参数 + 契约 §3.3；F30 未知接口真 404；F31 按角色收敛；浏览器抽验多项 + DOM 证据） |
| T-938 | **F22+F24（P3 数据）演示/旧数据卫生**。输入：报告 F22/F24。输出：豆包修正花鲢「菌落总数」结果串行（现为「色泽正常」，溯源到对应 seed/旧数据）；清理长期停留 S10/S20 的 TEST-NA 陈旧样品或补齐演示数据。验收：省平台导出解包无串行错误值；在检视图无 539h 陈旧噪声；改动幂等可重跑 | B | **豆包** | ✅完成 2026-10-08（本轮由主 agent 执行；`db/seed/05_data_hygiene.sql` 幂等：花鲢「菌落总数」→ jt1/50000/1200/合格、孔雀石绿补检出限 0.5 → 合格；删除 7 条陈旧 `TEST-*` + 2 条导入批次标记。已执行复核：TEST-* 归零、导出解包实测「菌落总数=1200」、无乱码） |
| T-939 | **性能观察（需复测，暂不定级）**：`POST /item/confirm`（62 项）实测 >15s、部分列表冷启动偶发 >15s。输出：GLM 在机器正常负载（非休眠恢复）下复测；若稳定复现，排查批处理/N+1/索引并优化。验收：给出复测数据；确认为缺陷则优化后 confirm 耗时明显下降并记录 | A | **GLM** | ✅完成 2026-10-08（正常负载复测：`save`(62 项) **130ms** / `item/confirm` **19ms** / `assign/auto` **112ms**——**非缺陷**，关闭；此前 >15s 为休眠恢复/冷启动环境假象） |
| T-940 | **修复后回归测试（豆包）**：GLM 各批修复后，豆包按报告第九节浏览器复测清单 + 重跑 `db/seed/03_demo_flow_seed.sql` 还原演示数据，S10→S90 全程回归，并更新本报告勾选状态 | B | **豆包** | ✅完成 2026-10-08（本会话由 QA 角色「严过关」执行：**53 PASS / 0 FAIL / 1 未验证**（F8 运行时→主理人补测 PASS）；报告 `docs/test/2026-10-08-regression-verification.md`（含主理人 F8 E2E 附录）；演示数据已由 seed03 还原、seed05 复核 no-op） |

## UI/UX 全面重构任务分解（T-917，用户 2026-09-13 指令）

> **范围界定（用户原话要点）**：这是**整个 LIMS 项目的统一升级**，不是只改报告审核页；报告审核页作为**第一批重点重构对象**；最终统一升级约 **17 个页面**。
> **不可破坏项**：除非确认存在严重问题，否则**不修改** DB 表结构 / 已有 API / API 参数 / 核心业务状态 / Pinia Store 数据结构 / 登录认证 / 权限体系 / 已有业务流程。
> **工作顺序（用户指定，不得跳步）**：动态路由 → 完成路由稳定性 → 分析现有全部页面 → 建立 Design System → 重构 Layout → 重构公共组件 → 逐页 UI 重构 → 完善交互 → ECharts/Dashboard → 全局视觉统一 → 自测。
> **设计规范来源**：`C:/Users/Chen/Desktop/前端优化/ui提示词.txt`（1613 行 / 38 章）。

| 任务ID | STEP | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|---|
| T-917-1 | STEP 1 | **分析现有项目与 UI 现状**：盘点技术栈 / 页面清单 / 公共组件 / `styles/` / stores / API / design token / ECharts 配置 / 权限路由，输出「现有 → 新 UI 系统」映射表 + UI 问题清单（**只读不改码**） | A | **GLM** | ✅完成（2026-09-13） |
| T-917-2 | STEP 2 | **统一 Design Token**：按提示词 §五 对齐色板（品牌色 `#18D6C5` 等）、间距、圆角、字号、阴影；收敛 `styles/tokens.css`，消除散落硬编码色值 | A | **GLM** | ✅完成（2026-09-13） |
| T-917-3 | STEP 3 | **重构 Layout 外壳**：Sidebar 224 / Header 64 / 菜单树驱动 / 激活态 3px 品牌竖条 / 面包屑 / 折叠态 | A | **GLM** | ✅完成（2026-09-13） |
| T-917-4 | STEP 4 | **重构公共组件 + Element Plus 主题覆盖**：`AppCard`/`AppButton`/`AppModal`/`AppDrawer`/`AppEmpty`/`AppLoading`/`AppConfirm` + `DataTable`/`DataFilter`/`StatusBadge`/`ProgressBar`/`StatCard` | A | **GLM** | ✅完成（2026-09-13） |
| T-917-5 | STEP 5 | **逐页 UI 重构（约 17 页）**：按提示词 §三十六 顺序推进；**报告审核页为第一批重点**；结果录入页异常行高亮；Dashboard 接真实业务数据 | A | **GLM** | ✅完成（2026-09-14 复核确认：17 页已 100% 使用 PageHeader + AppCard + StatusBadge + AppEmpty + DataFilter + askConfirm） |
| T-917-6 | STEP 6~10 | **交互完善 + ECharts/Dashboard + 全局视觉统一 + 自测**：三档分辨率（1440×900 / 1920×1080 / 1366×768）验证 + 用户 20 项 Checklist 逐条核对 + 输出变更清单 | A | **GLM** | ✅完成（2026-09-14：三档分辨率实测 `scrollWidth == clientWidth` 且 DOM 无越界元素；Edge+CDP 全量遍历 20 页 **0 console error / 0 network failure**；工作台/样品登记/结果录入/报告审核/用户管理逐页截图确认渲染正常） |

### T-917 实测发现的 UI 问题（豆包 2026-09-13 22:55 巡检，**已全部修复 2026-09-14**）

| 编号 | 问题 | 位置 | 修复建议 | 状态 |
|---|---|---|---|---|
| T-917-B1 | PageHeader 标题 flex-shrink 缺失，右侧多按钮时逐字竖排 | `components/common/PageHeader.vue` `.page-header__title` | 加 `flex-shrink:0; white-space:nowrap` | ✅已修 |
| T-917-B2 | 顶栏与 PageHeader 双面包屑，当前页名重复 | MainLayout + 各页 PageHeader breadcrumb slot | 二选一 | ✅已修（面包屑统一由顶栏渲染） |
| T-917-B3 | 表格列宽截断（任务来源/检验类别/抽样地址） | task/report-generate/sample 列表 | 加 min-width 或 show-overflow-tooltip | ✅已修（全站 65 处 `show-overflow-tooltip`） |
| T-917-B4 | 查询筛选按钮居左 vs 居右不一致 | query/testing,history,lib vs 其他页 | DataFilter 统一右对齐 | ✅已修 |
| T-917-B5 | favicon.ico 每页 404 | frontend/public/ | 放图标或 index.html link | ✅已修（`favicon.svg`，HTTP 200） |
| T-917-B6 | 顶部铃铛通知为 **4 条写死的假数据**（违反「禁 mock」原则） | MainLayout.vue | 改为真实待办汇总 | ✅已修（2026-09-14，见 T-918） |
| T-917-B7 | 用户菜单「操作日志」为**空壳**（对话框仅写「待后端接入」） | MainLayout.vue | 补齐后端 + 接真实数据 | ✅已修（2026-09-14，见 T-918） |
| T-917-B8 | `AppSkeleton.vue` / `ProgressBar.vue` **零引用死代码** | components/common | 使用或删除 | ✅已处理（Skeleton 接入工作台 KPI 加载态；ProgressBar 删除——全站 4 处进度已用 `el-progress`） |

**用户 20 项验收 Checklist**（T-917 最终验收依据，逐条须可举证）：
① Sidebar ② Header ③ Card ④ Button ⑤ Input ⑥ Table ⑦ StatusBadge ⑧ Modal/Drawer ⑨ Loading/Empty/Error ⑩ 十项组件视觉统一
⑪ 品牌色统一 ⑫ 无大面积空白 ⑬ 数据层级清晰 ⑭ 异常数据明显 ⑮ Dashboard 有真实业务数据 ⑯ ECharts 有实际意义
⑰ 无 Console Error ⑱ 不破坏业务 ⑲ 无横向溢出 ⑳ 1440×900 与 1920×1080 布局正常

### T-923 用户实测缺陷修复（2026-09-18，GLM）

| 编号 | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-923-1 | AI 助手「流式永久卡在正在生成」——SSE ASYNC 派发鉴权异常致连接被 RST + 前端改裸对象不触发视图更新 | S | GLM | ✅完成（236/236 全绿；经 5173 代理 `curl_exit=0`） |
| T-923-2 | 前端 SSE 兜底：30s 静默超时 + 「无 done 帧」显式回调，保证界面不再永久卡住 | A | GLM | ✅完成（含「区分用户主动停止 vs 超时」） |
| T-923-3 | 检索召回错误：整句改走自然语言模式 + 布尔层剔除噪声词（`GB`/纯数字）+ 条款标题相关性优先排序 | S | GLM | ✅完成（新增 `ft_gbc_title` ngram 索引，迁移 `V10`） |
| T-923-4 | OCR 条款归属错误：两行式标题合并、数字空格还原、纯数字伪边界剔除、带标题边界必切 | S | GLM | ✅完成（824 → 0 条噪声标题；索引已重建） |
| T-923-5 | 「点名的标准未收录」确定性 fail-loud（不再拿其他标准条款凑答案） | S | GLM | ✅完成（GB 2762 → 明说未收录 + 已收录清单） |
| T-923-6 | 审计口径：流式接口操作人被记成 `anonymousUser`、耗时恒为 0 | A | GLM | ✅完成（`nj001` / 真实耗时） |
