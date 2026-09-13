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
| T-603 | 检验员查询自己的检验任务 + 下载任务 Excel（说明书第七节；当前仅预留 `result:export-excel` 标识，接口未实现） | A | **GLM** | ⬜待办 |

## 阶段一延伸：基础数据维护界面（说明书二(2)(3)；当前有表有数据、无管理页）
| 任务ID | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-105 | 方法-检验员资质设置（`tester_method` CRUD + 导入；说明书原文「添加检验员-检验方法」） | A | **GLM** | ⬜待办 |
| T-106 | 项目标准库维护 + 「导入新的项目库」Excel 批量导入（`product_lib`/`product_lib_item`） | A | **GLM** | ⬜待办 |
| T-107 | 系统管理 4 页：用户（含角色分配/启停/重置密码）、角色（含菜单权限树）、菜单、部门 | A | **GLM** | ⬜待办 |

## 查询与省平台上报
| T-801 | 在检/历史/项目库查询 | A | **GLM** | ✅完成 2026-09-13（契约第 9 章 `/api/query` 3 接口：testing/history/lib + 分页 current/size + 停留时长**近似推导**（不新建流水表，按 createdAt/confirmedAt/updatedAt/MAX(assigned_at)/MAX(sample_result.updated_at)/auditAt 拼）+ `itemTotal/enteredCount/pendingCount/abnormalCount` 强制复用 `ResultEntryPolicy`（T-912 唯一口径）+ 权限 `query:testing/query:history/query:lib`；前端 `views/query/{testing,history,lib}.vue` 三个查询页 + 路由菜单；端到端 54/54 + 视觉回归全过） |
| T-802 | 省平台上报 Excel 导出 | B | 豆包 | ✅完成 2026-09-13（豆包：格式定稿+样例已交付 2026-09-12；GLM：契约第 10 章 `/api/export/province` + **EasyExcel 3.3.4 流式**禁用 POI 裸 API + 阈值 `status>=80`（已签发即可上报，含 S90 已出报告）+ **严格 10 列不插空隔列** + 参考项不加 `*` 前缀 + 支持 `?taskNo=` 筛选 + 权限 `export:province`；前端 `views/export/province.vue` + `utils/download.ts` + 路由菜单；端到端 54/54 含 njsa000 越权真 HTTP 403） |
| T-803 | 可视化看板：工作台图表 + 质量分析 + 统计报表（须基于真实统计接口，**禁 mock 假数据**） | A | **GLM** | ⬜待办 |

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

> 注：以上为初始骨架。S/A 级任务的接口定义由 GLM 起草写入 api-spec.md，**Copilot 终审**；存在判定口径歧义时由 Copilot 裁决。豆包不自行设计业务表。
