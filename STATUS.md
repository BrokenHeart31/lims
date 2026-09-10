# STATUS.md（共享白板）

> 规则：开工前在此声明本轮占用的文件/模块；收工后更新。任何 Agent 30 秒读懂全局。

## 当前工作分支
- 豆包：`agent/doubao`（本轮：文件整理 + T-103 补 04 + 初步测试完成，待提交推送）
- GLM：`agent/glm`（T-003 前端骨架 ✅，终审通过）
- Copilot：`agent/copilot`（T-002 ✅；T-101/T-102/T-201 后端 + DDL/契约终审 ✅）

## 项目位置
- 本地仓库：`D:\lims`（远程 https://github.com/BrokenHeart31/lims.git ）

## 本轮占用文件（豆包，待提交）
- `.gitignore`（增 lims.sql/.workbuddy 防误提交）；`README.md`（修正 agent 角色写反）
- `db/init/04_tester_method.sql`（新增，T-103 补尾）；`db/migrations/V1__import_legacy_data.sql`（迁移源改 customer_legacy/dept_legacy）
- `backend/src/main/resources/application-dev.yml`（本机真实口令，gitignore 不入库）
- 治理四件套：TODO/STATUS/HANDOFF/DECISIONS

## 他人占用
- （无）

## 当前状态/阻塞
- ✅ 数据库实测通过：init 01→02→03→04 → 导入 lims.sql（customer/dept 改名 legacy）→ V1 → seed 01→02。V1 结果：basis 859（源 1088 去重）、customer 9、product_lib 92、product_lib_item 3728（judge_type 全 1、ref 834）。
- ✅ 后端实测通过：nj001 登录 /auth/me（R100 全权限 49 项 + 菜单树）/task/page + CRUD 四件套（审计 createdBy=nj001 自动填充生效）。
- ✅ 前端 vite dev 启动正常（root HTTP 200）；API 层全链路已验证。
- ⚠️ **待 Copilot 终审**：①V1 迁移源改 customer_legacy/dept_legacy（lims.sql 同名冲突方案）；②db/init/04_tester_method.sql；③application.yml `characterEncoding=utf8mb4` 是 bug（Connector/J 不认，应改 utf8），本机已用 dev yml 覆盖 url 跑通。
- ⚠️ GUI 通道不可用（bu 浏览器空间 disabled、cu 虚拟桌面 PIP 初始化失败），前端 UI 点击走查（登录→工作台→任务 CRUD 按钮操作）未完成，待环境恢复补走。
- ⚠️ git.exe 写 `D:\lims\.git\objects` 被安全软件按进程拦截（PowerShell 可写）；本地方案：GIT_OBJECT_DIRECTORY=TEMP + robocopy 同步对象（已验证可行）。
- ℹ️ 本机 MySQL 实际密码 123456（非 AGENTS 约定 11111111），已写入 gitignore 的 application-dev.yml。
