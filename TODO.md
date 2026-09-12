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
| T-702 | CMA / CMA-CATL 报告模板合成 + S80→S90 | S | **GLM** | ⬜待办（✅ 版式样本已从业务说明书 docx 取得：首页编号/资质号/注意事项 + 第1页表头与检验结论句式 + 第2页七列明细表；无需挂起等待外部样本） |

## 查询与省平台上报
| T-801 | 在检/历史/项目库查询 | A | **GLM** | ⬜待办 |
| T-802 | 省平台上报 Excel 导出 | B | 豆包 | 🔵进行中(豆包：格式定稿+样例已交付 2026-09-12；后端 `/api/export/province` 端点+前端按钮归 GLM，格式见 `docs/knowledge/2026-09-12-province-export-format.md`，样例 `docs/reference/province_export_sample.xlsx`) |

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

> 注：以上为初始骨架。S/A 级任务的接口定义由 GLM 起草写入 api-spec.md，**Copilot 终审**；存在判定口径歧义时由 Copilot 裁决。豆包不自行设计业务表。
