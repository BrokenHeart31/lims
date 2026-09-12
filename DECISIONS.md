# DECISIONS.md（决策记录）

> 格式：日期 | 决策 | 理由 | 决策人

| 日期 | 决策 | 理由 | 决策人 |
|---|---|---|---|
| 2026-09-10 | 新旧库并存原则（AGENTS.md 0.1） | 旧 lims.sql 风格禁止复用，数据走 V1 迁移脚本单向清洗进新表 | 全员 |
| 2026-09-10 | 分支策略 agent/xxx → develop → main | 多 Agent 防冲突，保留每周 main 固化考核 | 全员 |
| 2026-09-10 | 项目根目录定在 `C:\Users\Chen\Desktop\lims`（非手册原写的 `D:\test\lims`） | 用户指定实际路径 | 豆包记录 |
| 2026-09-10 | 实际仓库位置修正为 `D:\lims`（桌面路径被安全软件拦截 git 写入） | 覆盖上一条，与 HANDOFF/STATUS 一致 | 豆包（GLM 顺手修正） |
| 2026-09-10 | T-003 前端骨架由 GLM 完整实现，含 package.json/vite.config/tsconfig/router 等公共文件，Copilot 终审 | TODO T-003 明确指派 GLM，与 AGENTS.md 2.1 所有权表冲突；按任务指派执行并留痕待审 | GLM |
| 2026-09-10 | 认证域前端实现按 AGENTS.md 4.1/8.3 约定先行，JSON 字段命名暂定 camelCase（accessToken/refreshToken），以 Copilot api-spec.md 终审为准 | 契约未落地但骨架需先行；契约变更影响面收敛在 src/api/auth.ts + src/stores/auth.ts | GLM |
| 2026-09-10 | 前端 token 存 localStorage（key=lims_access_token），401 由 axios 拦截器统一清除并跳 /login?redirect= | 禁止 Token 入 URL；刷新 token 机制待 T-002 后端设计 | GLM |
| 2026-09-10 | 认证域契约终审：采纳 camelCase（accessToken/refreshToken、user/permissions/menus），新增 expiresIn/user.deptId/MenuNode.parentId；JSON 字段一律 camelCase | 与 T-003 前端实现一致，前端零改动；camelCase 为 JSON 通例 | Copilot |
| 2026-09-10 | 共享检验员账号定为 njsa000（说明书"njsa00"系笔误；其密码列三行同为 njna000 按"账号名即初始密码"理解） | 与 njna000/njxa000 命名对齐，AGENTS.md 7.4/8.1 为准 | Copilot |
| 2026-09-10 | 后端引入 Lombok（BOM 管理 1.18.34），maven-compiler-plugin 显式声明 annotationProcessorPaths | 减少样板代码；高版本 JDK 下保证注解处理稳定生效 | Copilot |
| 2026-09-10 | 后端统一 server.servlet.context-path=/api | 与前端 vite 代理（/api→8080 无 rewrite）对齐，契约路径直观 | Copilot |
| 2026-09-10 | Spring Boot 定版 3.3.2（不追 4.x） | AGENTS 约束 Spring Boot 3.x；MP 3.5.7 对 Boot 4 兼容性未验证；本地 .m2 已缓存 | Copilot |
| 2026-09-10 | 安全层 401/403 返回 HTTP 状态码 + 统一响应体；业务异常 HTTP 200 + body.code | 前端 request.ts 双通道均已处理；语义清晰 | Copilot |
| 2026-09-10 | 基础表 DDL（basis/customer/dept）由豆包按 6.1 规范先落 db/init/01_basic_tables.sql，product_lib/product_lib_item 作为 T-401 草稿一并落库供 V1 迁移 | T-103/T-104 需可跑；product_lib* 为 S 级领域草稿，Copilot 终审可改 | 豆包 |
| 2026-09-10 | T-201 前端按约定契约 /api/task/*（page/{id}/POST/PUT/DELETE，camelCase，分页 records/total）先行 | Copilot 任务域契约未落地；调整面收敛在 src/api/task.ts | 豆包（代 GLM） |
| 2026-09-10 | 前端引入 ESLint 9 扁平配置（eslint.config.js），新增 devDeps eslint/typescript-eslint/eslint-plugin-vue/globals，npm run lint 门禁 | AGENTS 第 9 章要求 npm run lint；T-004 补齐 | 豆包（代 GLM） |
| 2026-09-10 | RBAC 表采用 sys_ 前缀（sys_user/sys_role/sys_menu/sys_user_role/sys_role_menu），部门复用业务 dept 表并补 parent_id | user 为 MySQL 函数名须转义，sys_ 前缀（RuoYi 惯例）规避；dept 已被 01 建为业务表，加列比重建安全 | Copilot |
| 2026-09-10 | 业务字典字段（任务性质/区域级别/抽样环节/任务状态等）以 VARCHAR 存中文字典值；样品状态机 S10→S90 强制 TINYINT+枚举 | 字典值源自下达文书需原样展示打印，值域稳定；6.1 的 TINYINT 规则针对状态机类枚举（T-301 起严格执行） | Copilot |
| 2026-09-10 | product_lib_item 增加 judge_type（1=限量比较 2=不得检出/不得使用 3=文本/感官人工），V1 按 stdValue 形态推导；T-401 表结构就此定稿 | T-601 自动判定引擎（AGENTS 7.3 规则 1/2/3）直接消费，避免引擎每次重判文本形态 | Copilot |
| 2026-09-10 | JWT 过滤器每请求按 username 从 DB 装配 LoginUser，token 内 perms claim 仅作签发快照不作鉴权依据；R100 在代码层 isAdmin 短路拥有全部权限/菜单 | 权限变更即时生效（踢人/改权无需等 token 过期）；小系统 DB 查询开销可接受 | Copilot |
| 2026-09-10 | 路由守卫对已登录未加载 me 的导航先 await fetchMe | 保证 v-permission 指令在页面渲染前拿到权限数据，避免按钮被误移除 | Copilot |

| 2026-09-10 | V1 迁移源旧表以 customer_legacy/dept_legacy 后缀导入（lims.sql 旧 customer/dept 与 01 新建表同名异构），V1 源表同步改名；lims.sql 本身不入库 | 同库并存前提下旧表与新表同名会覆盖/1050 冲突；_legacy 后缀最小改动，第 5 节校验仍读新表 | 豆包（实测发现，Copilot 终审通过 2026-09-11） |
| 2026-09-11 | application.yml 的 JDBC url 中 characterEncoding 用 utf8（Java 字符集名），禁用 utf8mb4 | Connector/J 8 报 Unsupported character encoding 'utf8mb4'；库表 utf8mb4 由 DDL/连接协商保证 | Copilot |
| 2026-09-11 | 追认 AGENTS.md 角色表修订：GLM=首席架构师/后端核心/终审（S 级），Copilot=前端主力/常规 CRUD（A 级）；Copilot 分支以 `agent/copilot` 为准（首页表格"agent/gpt"为笔误，豆包下轮统一） | 与实际分工一致；分支名以 0.3 节和实际远程分支为准 | Copilot |
| 2026-09-11 | 后端引入 **EasyExcel 3.3.4**（com.alibaba:easyexcel），T-301 采样单导入落地，T-802 省平台导出复用同依赖；禁止 Apache POI 裸 API | 方向性定稿落地（docs/knowledge/2026-09-11-excel-import-research.md）：SAX 流式低内存、注解+监听器极简、RuoYi-Vue-Plus 生态标准；POI 直用易 OOM | GLM（T-301） |
| 2026-09-11 | 样品状态字段 `sample.status` 用 **TINYINT + SampleStatus 枚举**（`@EnumValue` 落库 code、`@JsonValue` 出网数字 code、`getStatusLabel()` 派生中文）；流转唯一入口 `common/enums/SampleStatusTransition`（EnumMap 白名单 + 乐观条件 UPDATE） | AGENTS 0.2/7.2 唯一标准；知识库状态机选型定稿落地（禁 Spring StateMachine、禁私增状态/跳态、禁魔法数字） | GLM（T-301） |
| 2026-09-11 | 样品域权限标识用 **`sample:import` / `sample:confirm` / `sample:query`**；此前交接留言中的 `sample:list` 表述作废（api-spec 已更正） | 以 seed `sys_menu`（menu 31/32/33）与 AGENTS 8.2 实际值为准；`/me` 返回的即 `sample:query`，接口 `@PreAuthorize` 必须同值方可鉴权通过 | GLM（T-301） |
| 2026-09-11 | 采样单导入**部分失败不回滚**（合法行入库 S10，错误行逐条报「行号+原因」）；以 `sample_import_batch` 表登记 Excel A1 文件标记，同标记重复导入整文件拒绝 | 业务允许修正后重导（说明书采样单流程）；A1 标记防重复导入系说明书明确要求（「A1列自定义Excel文件标记，防止重复导入」） | GLM（T-301） |
| 2026-09-11 | 样品表名用 **`sample_info`**（非 `sample`） | 实测发现 `SAMPLE` 是 SQL 关键字（TABLESAMPLE），与 MyBatis-Plus 分页插件所用 JSqlParser 冲突：`SELECT ... FROM sample` 解析失败，导致分页 count SQL 无法优化（WARN + 大表走子查询 count）。与本项目 `user`→`sys_user` 同一处理原则；接口路径 `/api/sample/*` 与权限标识 `sample:*` 不受影响 | GLM（T-301） |

## 2026-09-11 角色调整（用户决策，GLM 执行落地）

| 日期 | 决策 | 理由 | 决策人 |
|---|---|---|---|
| 2026-09-11 | **S 级 + A 级执行权全部归 GLM**（GLM 与 Copilot 同级）；Copilot 只保留三类不可替代工作：① api-spec 契约起草协助/终审；② 规则裁决（判定口径与跨模块语义歧义的最终解释）；③ diff 审查（合并进 develop 前的代码审查） | 用户决策：GLM 能力覆盖 S/A，Copilot 额度应集中在判断类工作；「GLM 实现，Copilot 把关」边界最清晰，避免所有权悬空 | 用户（GLM 落地） |
| 2026-09-11 | 覆盖 2026-09-11「追认 AGENTS.md 角色表修订」（Copilot=前端主力/常规 CRUD）——该条作废 | 上条已被本次角色调整取代；前端主要所有者回归 GLM | 用户（GLM 落地） |
| 2026-09-11 | AGENTS.md 2.1 文件所有权：`backend/` 核心与 `frontend/`、公共文件 → **GLM 独有**；`docs/api/api-spec.md` → GLM 起草 + **Copilot 终审**；冲突基准：公共文件以 GLM 版本为基准，契约以 Copilot 终审版本为基准 | 消除原表「backend 核心 Copilot 独有」与 2.3「S 级仅 glm」的自相矛盾 | GLM |
| 2026-09-11 | AGENTS.md 2.3 重写：删除「GLM/豆包遇到 S 级问题停止」矛盾表述，改为「S/A = GLM，B = 豆包，Copilot = 契约+裁决+审查」；2.4 冲突基准同步修订；7.3 标题改为「GLM 实现于后端，Copilot 裁决口径」；0.2 判定规则实现者由 Copilot 改 GLM | 同上，四份治理文件口径必须一致 | GLM |
| 2026-09-11 | `prompts/glm.md` 与 `prompts/copilot.md` **全文重写**（原两份文件角色名交叉错写：glm.md 自称 copilot、分支写 agent/copilot；copilot.md 分支写 agent/glm） | 提示词是 Agent 的开工依据，错写会导致分支误提交与职责越界 | GLM |
| 2026-09-11 | TODO.md 的 T-401 / T-501 / T-601 / T-701 / T-702 / T-801 Owner 由 Copilot 改为 **GLM**；新增 T-901（治理维护）/ T-902（白名单草案）/ T-903（product_name 补全，豆包） | 与角色调整对齐 | GLM |

## 2026-09-11 Copilot 裁决与终审（T-902 / T-301 契约）

| 日期 | 决策 | 理由 | 决策人 |
|---|---|---|---|
| 2026-09-11 | **修复 4070ea6 误删事故**：该提交误删 backend/db/docs/frontend 共 118 个文件并将 4 个垃圾文件（空 .gitkeep 被改成中文碎片文件名）入库，且已推送远程三分支。以修复提交（agent/copilot `e476cf6`）前滚恢复，不重写历史 | 事故树已推送 main/develop/agent/glm，force-push 改写受保护历史风险大于收益；修复提交完整恢复 fbe8062 内容 | Copilot |
| 2026-09-11 | **T-902 D1 裁决：采纳**——数值结果 < 最低检出限视同「未检出」；jt1 限量比较型直接判合格；lower_limit 为 NULL 时不做视同 | AGENTS 7.3 规则 4 落实；与实测反推口径一致 | Copilot |
| 2026-09-11 | **T-902 D2 裁决：采纳**——「不得检出/不得使用」型按「数值结果 ≥ 检出限才算检出」：未检出或低于检出限 → 合格，≥ 检出限 → 不合格；lower_limit 为 NULL 且数值结果 → 待判定（禁默判合格） | 20 条旧不合格样例反推 + GB 2763 通行解释；白名单最关键口径 | Copilot |
| 2026-09-11 | **T-902 D3 裁决：采纳含补充**——参考项（is_reference=1）单项结论照常计算并标注「参考」展示，但不计入整体结论；全部项目均为参考项时整体 = 待判定 | AGENTS 7.3 规则 5「参与计算但标注参考」与规则 6 的歧义裁决：规则 6「任一单项」仅指非参考项 | Copilot |
| 2026-09-11 | **T-902 D4 裁决：采纳补全矩阵**——stdValue=`--`：未检出（含视同）→ 合格；数值且 ≥ 检出限或检出限 NULL → 待判定 | 与旧 prj_detail 实测口径（`--`+未检出→合格）一致，堵住无依据默认合格的洞 | Copilot |
| 2026-09-11 | **T-902 D5 裁决：采纳**——T-401 由 GLM 做一次性 judge_type 订正脚本（按 stdValue 文本形态重判 jt2/jt3，含 before/after 统计）；引擎运行时只读 product_lib_item.judge_type，禁止依赖旧 prj_detail | lib 表现存 judge_type 全 1 与旧数据存在 jt2 形态不符；运行时依赖旧表违反 0.1 | Copilot |
| 2026-09-11 | 说明书第 2 页两处矛盾样例（铅 0.1 对 ≤0.25 判不合格；氯霉素未检出判不合格）确认为旧系统数据瑕疵，不作为规则依据 | 与白名单口径矛盾；裁决全文见 docs/knowledge/2026-09-11-judge-engine-whitelist.md | Copilot |
| 2026-09-11 | **api-spec 样品域（第 3 章）终审通过**：5 接口路径/方法/权限标识（sample:import/confirm/query）与 Controller、seed、前端 api/sample.ts 逐字段一致；唯一调整为 3.1 审计字段行补 `updatedBy` | T-301 遗留终审关闭；契约以本文档为准 | Copilot |

## 2026-09-11 T-401 项目分解（自动套库）实现决策（GLM）

| 日期 | 决策 | 理由 | 决策人 |
|---|---|---|---|
| 2026-09-11 | **套库预览不落库**：`GET /item/match/{sampleId}` 仅返回初稿，不写 `sample_item` | 业务允许分解员在自动套库初稿上人工增删调整；「套库初稿」与「最终确认的分解结果」是两个概念。若套库即落库，人工调整须先删再写，语义混乱且污染审计字段 | GLM（T-401） |
| 2026-09-11 | **保存为覆盖式**：`PUT /item/save` 先逻辑删除既有明细再全量重建，不提供增量 patch 语义 | 分解页是整体工作台，前端已保证项次连续唯一（1..N）；覆盖式让后端逻辑最简，并消除「前端删了一行但后端未收到」的中间态 | GLM（T-401） |
| 2026-09-11 | **标准库字段快照下沉**：`unit`/`basis_code`/`methods`/`std_value`/`judge_type`/`is_reference`/`lower_limit`/`method_note` 八个字段从 `product_lib_item` **复制**进 `sample_item`，而非只存 `lib_item_id` 外键 | ① 国标会更新，报告必须固化「检验当时的判定依据」，事后查报告不能被新标准追溯篡改；② 分解员人工调整后的值必须独立于标准库，若只存外键则人工改动无处安放 | GLM（T-401） |
| 2026-09-11 | **推论（约束 T-601）**：判定引擎**只读 `sample_item`**，**禁止回溯 `product_lib_item`** | 上条的必然推论。已写入契约 4.1、`SampleItem` 实体注释与 `06_item_tables.sql` 表注释 | GLM（T-401） |
| 2026-09-11 | **项次唯一性用三列唯一键** `uk_sample_item_order (sample_id, item_order, deleted)` | 带 `deleted` 以兼容逻辑删除——否则删掉第 2 项后再新增第 2 项会撞唯一键。前端在删除行后调 `resequence()` 重排为连续 1..N | GLM（T-401） |
| 2026-09-11 | **确认流转用乐观条件 UPDATE**：`WHERE id=? AND status=旧值`，`updated==0` 时抛「样品状态已变更，请刷新后重试」 | 防止并发下重复确认 / 状态被他人推进后仍强行流转；与 `SampleStatusTransition.assertTransition(S20, S30)` 双保险 | GLM（T-401） |
| 2026-09-11 | **`pagePending` 用 `Collectors.groupingBy` 一次性统计 itemCount**，不做逐行 count | 避免 N+1 查询；分页列表最多 100 行，批量聚合成本可忽略 | GLM（T-401） |
| 2026-09-11 | **D5 前提被实测推翻 → 提出裁决请求 #1（T-905）**：`product_lib_item` 3728 行 `std_value` 100% 纯数值；源 `lib` 表同样 100% 纯数值（非迁移漏迁）；`prj_detail` 的 220 条 `不得检出` 仅 5 个兽残项目名且在 `product_lib_item` 中 0 匹配（两表不同源，`lib` 仅覆盖农残 GB 2763-2021）。故 `V3__correct_product_lib_item_judge_type.sql` 实测为**零变更（no-op）**，定位改为「可重跑的口径校验器」。建议 **R1（推荐）** 采纳。全文：`docs/knowledge/2026-09-11-adjudication-request-d5.md` | 执行前先证伪前提：裁决文档给出的是「规则」，但规则作用的「数据」可能根本不存在。若照写脚本，V3 会成为永远输出 0 变更的静默失败，比报错更危险 | GLM 提出，**待 Copilot 裁决**（2 次配额之第 1 次） |
| 2026-09-11 | **T-903 数据补齐方式**：V2 脚本以 `product.id = product_lib.product_code` 一对一匹配，只填 NULL 值（幂等可重跑） | 92 行一对一完美匹配，无歧义；只填空值保证脚本可反复执行而不覆盖人工修正 | GLM（T-903） |
| 2026-09-11 | **工作纪律三件套制度化**（用户强制）：①工作日记 `docs/journal/YYYY-MM-DD-<agent>-<主题>.md`（目标/做法/心得/踩坑/进度/可复用结论）②进度百分比（固定权重：业务主干 55% + 前端 15% + 数据 10% + 质量 10% + 工程化 10%）③动手前先检索（`.agents/skills/` → `docs/knowledge/` → `docs/journal/` → 上网）④经验资产化。写入 AGENTS 2.5 节，2.2 开工五步→六步 | 用户要求「以后的 agent 都要保持这个习惯」，并使项目经验可作为模板复现 | 用户（GLM 落地） |
| 2026-09-11 | **提交纪律（事故教训）**：GLM 提交前必须 `git status --short` 逐项核对暂存区 | 4070ea6 误删事故根因即「未核对暂存区」；已写入 AGENTS 第 12 章红字 | GLM |

## 2026-09-11 Copilot 裁决（T-905，裁决配额第 1 次）

| 日期 | 决策 | 理由 | 决策人 |
|---|---|---|---|
| 2026-09-11 | **T-905 D5 修订：采纳 R1**——D5 前提（lib 存在 jt2/jt3 形态、judge_type 全 1 属迁移缺陷）被实测证伪；judge_type=1 对现存 3728 行是正确值。V3 定位改为「可重跑口径校验器」，零变更即存档证据；**校验器必须 fail-loud**（应然≠实然且 UPDATE 后仍不一致须报错退出，禁止静默通过） | GLM 全库核对证据链完整（docs/knowledge/2026-09-11-adjudication-request-d5.md）：std_value 100% 纯数值、源表 lib 同样、prj_detail 不得检出仅 5 个兽残项目且 0/5 匹配、两表不同源 | Copilot |
| 2026-09-11 | **R3 否决，不开任务**——禁止从旧 prj_detail 反建兽残标准库；5 个兽残「不得检出」项目登记为已知数据覆盖缺口，将来由业务方提供标准文本后走 /api/base 补录 + 重跑 V3 | 标准库权威来源是标准文本而非历史检验记录；反建行缺 basis_code/方法/限量值，不能用于正式判定 | Copilot |
| 2026-09-11 | **追认 T-401「快照下沉」对 D5 的强化**：判定引擎只读 sample_item（快照），禁止回溯 product_lib_item / prj_detail | 国标更新不追溯篡改已出报告；人工调整独立于标准库。取代原 D5「只读 product_lib_item.judge_type」表述 | Copilot |
| 2026-09-11 | **T-601 测试基线裁定**：引擎单测用构造数据（直接造 sample_item 行，不走标准库匹配）覆盖 jt2/jt3 全分支；当前生产数据触发不到 ≠ 可删除白名单分支 | 引擎单测测判定逻辑闭集，与标准库覆盖度无关；20 条旧不合格样例仅作构造蓝本 | Copilot |

## 2026-09-11 用户二次决策 + 本轮工程决策（GLM 自行裁决，第 2 次角色调整）

| 日期 | 决策 | 理由 | 决策人 |
|---|---|---|---|
| 2026-09-11 | **GLM 自行裁决机制（2.6 节）**：判定口径 / 数据形态歧义 / 跨模块语义冲突一律由 GLM 自裁并落档，**不再挂起等待 Copilot**；自裁证据标准不得低于原 Copilot 裁决定稿（可复现证据 + 落档 DECISIONS/knowledge + 反例说明 + fail-loud 优先）；Copilot 降为**可选复核**，裁决配额上限 2 次（已用 1 次，剩 1 次），且**不得作为推进阻塞** | 用户要求「接下来尽量不依赖 copilot 裁决完成项目，方便我启动一次完整项目测试……同时也方便你重整思路」；GLM 已具备完整证据链能力（T-905 实证即为范例） | 用户（GLM 落地） |
| 2026-09-11 | **豆包协作分工清单（2.7 节）**：文档整理、seed/演示数据、省平台模板（T-802）、报告版式样本整理、字段逐项比对校对、迁移脚本执行与条数校验记录 → 可委派豆包；`backend/` 代码、状态机、判定引擎、`db/init`+`db/migrations` 的**内容设计**、api-spec 契约、裁决类工作 → **禁止委派** | 用户要求「期间杂事你可以交给豆包处理」；目的是让 GLM 专注 S/A 主线，同时避免低能力模型触碰核心逻辑 | 用户（GLM 落地） |
| 2026-09-11 | **UI 设计基准「Aurora Glass」**：暗色沉浸 + 极光配色 + 折射玻璃，参考 Mineradio 的视觉语言**并独立实现**；令牌统一 `--lims-*`（`frontend/src/styles/tokens.css`）；三类表面 `.lims-glass` / `.lims-glass-refract` / `.lims-panel` 用途不可混用；**数据密集区禁用折射玻璃** | 用户要求「项目总体 UI 美观参考 mine radio」；暗色玻璃能显著提升观感，但强模糊会损害小字号数字可读性，故必须分区治理 | 用户（GLM 落地） |
| 2026-09-11 | **许可合规红线**：参考第三方 UI **必须先查许可**；Mineradio 为 **GPL-3.0**，本项目**只借鉴设计思路与参数关系，禁止逐字拷贝其代码/CSS/资源**（否则仓库被 GPL 传染，实训项目无法交付） | 工程合规底线；已写入 AGENTS 5.1 与 `docs/knowledge/2026-09-11-ui-design-mineradio-research.md` 第 2 节 | GLM |
| 2026-09-11 | **强调色与按钮底色分离**：`--lims-accent`（青 `#00F5D4`）只做光晕/描边/激活条；按钮/CTA 用 `--lims-brand-gradient`（深端 `#0b7f7a` → 蓝 `#2442ff`，白字对比度 ≥ 4.4:1） | 直接用浅青做按钮底会导致白字看不清（对比度约 2.7:1），是「华丽但不可用」的典型陷阱 | GLM |
| 2026-09-11 | **V3 落地为 fail-loud 口径校验器**（执行 T-905 裁决 R1）：`db/migrations/V3__correct_product_lib_item_judge_type.sql` 归一化改为**派生表统一计算 + NULL 安全比较 `<=>`**（避免 SET/WHERE 双写漂移、避免 `judge_type IS NULL` 行因 `UNKNOWN` 漏更新）；末尾加 `SIGNAL SQLSTATE '45000'` 断言，drift>0 或越界>0 即报错中止（退出码 1） | 裁决硬性要求「禁止静默通过」；实测验证双路径：正常 3728 行零变更 → 通过(exit 0)；人为制造 drift=1/outside=1 → 报错明细 + exit 1；再跑 V3 → 归一化并恢复 exit 0 | GLM（执行 T-905 裁决） |
| 2026-09-11 | **el-tag 全局禁用入场过渡**（实测缺陷修复）：表格内 `el-tag` 会卡在 `el-zoom-in-center-enter-from`（opacity:0），导致「分解进度」「状态」两列**完全空白**；修法为 CSS 统一 `transition: none !important` + `enter-from/active` 强制 opacity:1 | 标签在**异步数据到达后**才挂载，Vue 的双 rAF `nextFrame` 回调被打断时不执行，过渡类永不摘除 → 元素永久 opacity:0。`transition:none` 会让 Vue 判定「无过渡」并立即摘除类名（已用探针页验证类名消失、opacity=1） | GLM |


### T-501 关键设计（2026-09-11 GLM）

| 日期 | 决策 | 理由 | 决策人 |
|---|---|---|---|
| 2026-09-11 | **三级分配规则顺序固定为 分类→资质→兜底**，assignType 数字语义固定为 0=未指派 / 1=分类 / 2=资质 / 3=人工改派；人工改派（assignType=3）永不覆盖 | 规则顺序固定利于后续审计与单测；assignType=3 不覆盖避免「主管手改又被自动分配覆盖」的痛点 | GLM（自裁） |
| 2026-09-11 | **detail.candidates 仅含资质者**，不出现「无资质者作为候选」；reassign 必须从 candidates 中选或传具资质工号，否则 400 | 与 AGENTS 7.4 「仅允许指派有资质者」一致；前端下拉不出错选项 | GLM（自裁） |
| 2026-09-11 | **tester_method 0 行视为数据缺口而非实现缺陷**，auto 自然落到 pending 兜底；不写「自动创建资质」的兜底逻辑 | 反建资质会破坏数据真实性；业务方补录即可恢复，文档化在契约 5.0 | GLM（自裁） |
| 2026-09-11 | **状态机推进必经白名单 + 乐观条件 UPDATE**：S30→S40 走 SampleStatusTransition.assertTransition + baseMapper.update(entity, WHERE id=? AND status=30)，updated==0 抛「样品状态已变更，请刷新」 | 与 T-301 / T-401 一致；防止高并发场景下「以为流转了实际没流转」 | GLM（沿用既有约定） |
| 2026-09-11 | **pagePending 用内联 IN 一次统计 assignDone**，避免 N+1（每样品一次 COUNT sample_item WHERE sample_id=? AND assign_status=1 AND deleted=0） | 列表页性能护栏；样品量大时不能 N+1 | GLM（自裁） |
| 2026-09-11 | **沙箱坑扩展（commit hash 末位错位 + bash heredoc 中文括号 syntax error）**：commit 后必跑 `git rev-parse HEAD` + `git fsck --lost-found` 双验证；ref 错位时用真实 hash 覆盖；commit message 走临时文件 `git commit -F <file>` | 已踩两次；预防第三次 | GLM（沉淀） |

## 2026-09-12 T-601 结果录入 + 自动判定引擎（GLM 自裁，执行期发现并修复 1 个真实缺陷）

| 日期 | 决策 | 理由 | 决策人 |
|---|---|---|---|
| 2026-09-12 | **编号勘误：交接留言（HANDOFF 2026-09-11 21:30）中的「T-602」即 TODO 的阶段六任务，权威编号为 `T-601`**（结果录入 + 自动判定引擎 + S50→S60）。本次所有代码/契约/文档统一按 **T-601** 落档 | TODO.md 为任务清单权威（按业务七阶段对齐编号），且 STATUS 2026-09-11 进度评估亦写 T-601；「T-602」系口误沿用。历史条目（HANDOFF 21:30、journal 09-11、memory 09-11）保持原样不改写 | GLM（自裁） |
| 2026-09-12 | **判定引擎实现形态：不引入任何规则引擎/表达式引擎**，用「闭集白名单矩阵 + `BigDecimal.compareTo` + 闭集外一律待判定 + WARN」实现 | 判定语义固定且规模 < 10 条，Rete/议程调度等机制零回报；**最关键**：`std_value` 来自标准库（数据），若用 Aviator/SpEL/DRL 当表达式求值，等于让数据获得执行语义 → 污染即不可预测结论、无法静态审计。**规则必须是代码，判定依据才是数据**。全文 `docs/knowledge/2026-09-12-judge-engine-research.md` | GLM（T-909 侦察后自裁） |
| 2026-09-12 | **引擎与编排分层**：`service/judge/JudgeEngine` 为**无状态纯函数**（无 IO、无注入依赖），Service 只做「读快照 → 调引擎 → 落库 → 流转 → 聚合」 | 判定矩阵可用构造数据穷举覆盖单测（T-902 裁决要求的测试基线因此廉价）；编排用 Mock 验证。实测：引擎 34 项 + 编排 15 项，全绿 | GLM（自裁） |
| 2026-09-12 | **结果表 `sample_result` 一项一行 + 覆盖式 upsert**（唯一键 `(sample_item_id, deleted)`），存 `test_value`（原始值）/ `conclusion` / `conclusion_source` / `judge_basis`（判定依据说明） | ALCOA+：原始值（人为输入）与派生结论分层保存，报告上每个结论可回放到「哪条规则 + 哪个原始值 + 谁录的」；重复保存走 UPDATE，修订由审计字段留痕，不产生第二行 | GLM（自裁） |
| 2026-09-12 | **判定依据参数不冗余存放**：`std_value`/`judge_type`/`lower_limit`/`is_reference` 一律取自 `sample_item` 快照 | 避免两处真相不一致；延续 T-401 快照下沉 + 白名单定稿 D5「引擎只读 sample_item」 | GLM（自裁） |
| 2026-09-12 | **整体结论聚合只用非参考项**，且「全为参考项 → 待判定」；参考项单项结论照常计算并展示 | 落实 AGENTS 7.3 规则 6 与白名单 D3 裁决；参考项不作放行依据 | GLM（自裁） |
| 2026-09-12 | **整体结论只在保存/提交时重算回写 `sample_info.conclusion`；明细查询实时重算返回** | 列表取持久化值（便宜），明细/提交取实时聚合（权威）；避免 GET 产生写副作用 | GLM（自裁） |
| 2026-09-12 | **存在「待判定」项不阻断提交**（整体结论如实为待判定），放行红线交给 T-701 审核/签发 | 待判定成因可能是数据缺口（缺检出限/缺标准文本），阻断会让样品永久卡在 S50——「用流程阻断掩盖数据问题」；fail-loud 的正确落点是**绝不自动判合格**，而非阻断流程 | GLM（自裁） |
| 2026-09-12 | **形态与判定类型矛盾时判「待判定 + WARN」，不做推测**（如 jt2 配数值型标准值、jt1 配「不得检出」、judgeType 越界） | 矛盾数据意味着上游有误，交人工比猜测安全；同时保证闭集外无静默路径 | GLM（自裁） |
| 2026-09-12 | **缺陷修复（自行发现）：标准值形态关键词与检验值形态关键词必须用各自常量**——标准值是「**不得**检出」，检验值是「**未**检出」 | 初版复用了同一常量，导致 jt2 全部分支退化为「待判定」（6 个单测同时报红）。已各自定义常量并补文档与技能防复发 | GLM（实测发现） |
| 2026-09-12 | **工具纪律补充：同一文件的多次编辑必须串行** | 一条消息内对同一文件并行发多条 Edit 会「基于旧内容写回」互相覆盖（报成功但改动消失），表现为编译报「找不到符号」。已写入 `sandbox-git-push` 技能规则 7 | GLM（实测发现） |
