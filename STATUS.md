# STATUS.md（共享白板）

> 规则：开工前在此声明本轮占用的文件/模块；收工后更新。任何 Agent 30 秒读懂全局。

## 当前工作分支
- 豆包：`agent/doubao`（文件整理 + T-103 补 04 + 初步测试 ✅，三项均终审通过）
- GLM：`agent/glm`（待命 → 下一轮 T-301 采样单 Excel 导入，S 级；开工先读 .agents/skills/excel-import + docs/knowledge 两篇）
- Copilot：`agent/copilot`（终审三项 ✅ + 修 application.yml ✅ + 合并推送 ✅ + 技能库/知识库建设 ✅）

## 项目位置
- 本地仓库：`D:\lims`（远程 https://github.com/BrokenHeart31/lims.git ）

## 本轮占用文件
- （无占用，全部已提交推送）

## 他人占用
- （无）

## 当前状态/阻塞
- ✅ **Copilot 终审三项全部通过**（2026-09-11）：①V1 迁移源 customer_legacy/dept_legacy 方案——源表/目标表引用全量核对一致，实测条数校验通过；②`db/init/04_tester_method.sql`——6.1 五要素齐全（BIGINT 主键/snake_case/审计四字段/deleted/uk+索引），对齐 AGENTS 7.4 资质匹配与 seed 菜单权限 `base:tester-method:*`；③application.yml `characterEncoding=utf8mb4→utf8` 已由 Copilot 直接修复（7dbadad），`mvn clean compile` BUILD SUCCESS。
- ✅ 已合并推送：`agent/doubao → develop`（b37e496）、`agent/copilot → develop`（8d0d256），develop 含全部最新成果。
- ✅ 数据库实测通过：init 01→02→03→04 → 导入 lims.sql（customer/dept 改名 legacy）→ V1 → seed 01→02。V1 结果：basis 859（源 1088 去重）、customer 9、product_lib 92、product_lib_item 3728（judge_type 全 1、ref 834）。
- ✅ 后端实测通过：nj001 登录 /auth/me（R100 全权限 49 项 + 菜单树）/task/page + CRUD 四件套（审计 createdBy=nj001 自动填充生效）。
- ✅ 前端 vite dev 启动正常（root HTTP 200）；API 层全链路已验证。
- ⚠️ **角色表已修订（AGENTS.md 首页表格 + 2.3 + 12 章 + prompts/）**：GLM=首席架构师/后端核心/终审（S 级），Copilot=前端主力/常规 CRUD（A 级）。**遗留不一致**：首页表格写 Copilot 分支为 `agent/gpt`，但 0.3 节与实际分支均为 `agent/copilot`——暂以 `agent/copilot` 为准，豆包下轮顺手统一。
- ⚠️ GUI 通道不可用（bu 浏览器空间 disabled、cu 虚拟桌面 PIP 初始化失败），前端 UI 点击走查（登录→工作台→任务 CRUD 按钮操作）未完成，待环境恢复补走。
- ⚠️ git 沙箱：本机 git 需 `git config http.schannelCheckRevoke false` + 推送用 `git -c http.sslVerify=false push`（本轮已验证可用）。
- ℹ️ 本机 MySQL 实际密码 123456（非 AGENTS 约定 11111111），已写入 gitignore 的 application-dev.yml。
- ✅ **技能库/知识库已建立**（2026-09-11 Copilot）：`.agents/skills/` 四技能（rbac-backend / mybatisplus-crud / vue3-crud-page / excel-import）+ `docs/knowledge/` 两篇侦察（EasyExcel 选型、状态机 EnumMap 白名单）。全员开工前按 AGENTS 2.2 第 2 步查阅。
- 📌 **方向性定稿两条**：①Excel 导入导出一律 EasyExcel（禁 POI 裸 API）；②样品状态流转一律「枚举 + EnumMap 白名单」（禁引 Spring StateMachine、禁私增状态/跳态）。
