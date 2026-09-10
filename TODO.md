# TODO.md（任务清单）

> 规则：按业务七阶段对齐编号，禁止跨阶段跳做。S=Copilot / A=GLM / B=豆包。
> 状态：⬜待办 🔵进行中(名字) ✅完成 ⏸阻塞

## 初始化阶段
| 任务ID | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-001 | 仓库连接 + 目录架构 + 治理文件（.gitignore/README/AGENTS/STATUS/TODO/HANDOFF/DECISIONS/api-spec） | B | 豆包 | ✅完成 |
| T-002 | 后端工程骨架（pom.xml/LimsApplication.yml/统一响应/异常/JWT 骨架）+ api-spec 首批登录 me 接口 | S | Copilot | ✅完成 |
| T-003 | 前端工程骨架（vite/package.json/路由/axios 封装/登录页壳） | A | GLM | ✅完成（Copilot 终审通过） |
| T-004 | 前端 ESLint 门禁补齐（AGENTS 第 9 章要求 npm run lint；骨架阶段缺 lint 脚本） | A | GLM | ⬜待办（不阻塞阶段一，T-201 前完成） |

## 阶段一：基础数据准备（对应 7.1 第 1 步）
| 任务ID | 任务 | 级别 | Owner | 状态 |
|---|---|---|---|---|
| T-101 | RBAC 建表（user/role/menu/dept/role_menu/user_role）+ 新表规范 | S | Copilot | ⬜待办 |
| T-102 | 后端登录/JWT/me 接口 + @PreAuthorize + v-permission 指令 | S | Copilot | ⬜待办 |
| T-103 | 基础表：basis（判定依据）、customer（客户）、dept、tester-method | B review S | Copilot审 | ⬜待办 |
| T-104 | 旧数据迁移脚本 V1__import_legacy_data.sql（basisname≈1150/customer9/lib/dept） | B | 豆包 | ⬜待办 |

## 阶段二：监抽任务管理
| T-201 | 监抽任务 SuperviseTask CRUD | A | GLM | ⬜待办 |

## 阶段三：样品登记（采样单 Excel 导入）
| T-301 | 采样单 Excel 导入 + S10→S20 登记确认 | S | Copilot | ⬜待办 |

## 阶段四：检验项目分解（自动套库）
| T-401 | 项目标准库 ProductLib + 自动分解 + S20→S30 | S | Copilot | ⬜待办 |

## 阶段五：检验任务安排
| T-501 | 自动分配规则（NA/XA/SA + 方法资质）+ S30→S40 | S | Copilot | ⬜待办 |

## 阶段六：检验数据录入（自动判定）
| T-601 | 结果录入 + 自动判定引擎 + S50→S60 | S | Copilot | ⬜待办 |

## 阶段七：报告审核签发 + 报告生成
| T-701 | 审核/签发 S60→S70→S80 | S | Copilot | ⬜待办 |
| T-702 | CMA / CMA-CATL 报告模板合成 + S80→S90 | S | Copilot | ⬜待办 |

## 查询与省平台上报
| T-801 | 在检/历史/项目库查询 | A | GLM | ⬜待办 |
| T-802 | 省平台上报 Excel 导出 | B | 豆包 | ⬜待办 |

> 注：以上为初始骨架，S 级任务的接口与表结构以 Copilot 在 api-spec.md 的定义为准；豆包不自行设计业务表。
