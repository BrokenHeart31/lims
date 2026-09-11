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
