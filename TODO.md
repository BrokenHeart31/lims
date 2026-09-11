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
| T-301 | 采样单 Excel 导入 + S10→S20 登记确认 | S | GLM（由 Copilot 改派） | ✅完成（2026-09-11：api-spec 样品域 + db/init/05_sample_tables.sql + EasyExcel 导入监听器 + 登记维护/确认 + 前端导入页 + 状态机枚举 + 9 项单测通过；契约待 Copilot 终审） |

## 阶段四：检验项目分解（自动套库）
| T-401 | 项目标准库 ProductLib + 自动分解 + S20→S30 | S | **GLM** | ⬜待办（product_lib/product_lib_item DDL 已终审定稿含 judge_type；V1 已迁 lib 数据；⚠️ product_name/category 全 NULL，需从旧 product 表补齐） |

## 阶段五：检验任务安排
| T-501 | 自动分配规则（NA/XA/SA + 方法资质）+ S30→S40 | S | **GLM** | ⬜待办（实物数据已探明：user_method 3 行粗粒度 + user_item 6 行项目级；tester_method 表 0 行） |

## 阶段六：检验数据录入（自动判定）
| T-601 | 结果录入 + 自动判定引擎 + S50→S60 | S | Copilot | ⬜待办 |

## 阶段七：报告审核签发 + 报告生成
| T-701 | 审核/签发 S60→S70→S80（含审核退回 → S50） | S | **GLM** | ⬜待办 |
| T-702 | CMA / CMA-CATL 报告模板合成 + S80→S90 | S | **GLM** | ⬜待办（✅ 版式样本已从业务说明书 docx 取得：首页编号/资质号/注意事项 + 第1页表头与检验结论句式 + 第2页七列明细表；无需挂起等待外部样本） |

## 查询与省平台上报
| T-801 | 在检/历史/项目库查询 | A | **GLM** | ⬜待办 |
| T-802 | 省平台上报 Excel 导出 | B | 豆包 | ⬜待办 |

## 治理维护
| 任务ID | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-901 | 角色调整落地：TODO/AGENTS/prompts 分级与所有权统一（S+A→GLM，Copilot 转契约+裁决+审查） | S | **GLM** | 🔵进行中(GLM) 2026-09-11 |
| T-902 | 判定引擎表达式白名单草案（基于真实数据分布，交 Copilot 裁决） | S | **GLM** | 🔵进行中(GLM) 2026-09-11 |
| T-903 | 补全 product_lib.product_name / category（源：旧 product 表 prd_category + libName） | B | 豆包 | ⬜待办 |

> 注：以上为初始骨架。S/A 级任务的接口定义由 GLM 起草写入 api-spec.md，**Copilot 终审**；存在判定口径歧义时由 Copilot 裁决。豆包不自行设计业务表。
