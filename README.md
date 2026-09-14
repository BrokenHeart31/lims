# LIMS —— 食品质量检验测试中心 实验室信息管理系统

前后端分离架构，多 Agent 协作开发。

- **前端**：Vue 3 + TypeScript + Vite + Element Plus + Pinia + Vue Router 4
- **后端**：Spring Boot 3 + Java 17 + Maven + MyBatis-Plus
- **数据库**：MySQL 8（InnoDB / utf8mb4）
- **权限模型**：RBAC

## 协作分支

`agent/glm`（S+A 级：架构/后端核心/前端） · `agent/copilot`（可选复核：契约/裁决/diff） · `agent/doubao`（B 级：文档/数据/维护）

合并路径：`agent/xxx → develop → main`。

## 快速开始

### 1. 准备数据库

```bash
mysql -uroot -p --default-character-set=utf8mb4 -e "CREATE DATABASE IF NOT EXISTS lims DEFAULT CHARSET utf8mb4;"

# 按序执行建表脚本
for f in db/init/0*.sql; do mysql -uroot -p lims < "$f"; done

# 迁移（存量库升级用 V*.sql，全新部署可跳过）
# 种子：权限/菜单 + 演示数据
mysql -uroot -p lims < db/seed/01_rbac_seed.sql
mysql -uroot -p lims < db/seed/02_demo_task_seed.sql
mysql -uroot -p lims < db/seed/03_demo_flow_seed.sql   # ← 全角色可自测的演示数据
```

> **`db/seed/03_demo_flow_seed.sql` 说明**：为「每个角色都有活可干」准备了一条可走通的完整链路
> （见下表）。**幂等**，可反复执行（只清理 `DEMO-` 前缀的样品，不动既有数据）。
> 若不执行它，检验员登录后会看到「结果录入」空列表——不是程序缺陷，而是没有处于待录入状态的样品。

| 演示样品 | 状态 | 给谁测 | 能测什么 |
|---|---|---|---|
| DEMO-2026-001 鲜食玉米 | S10 已登记 | `nj002` 样品登记员 | 样品登记确认 |
| DEMO-2026-002 菠菜 | S30 已分解 | `nj003` 任务管理员 | 自动分配 + 安排确认 |
| DEMO-2026-003 草鱼 | S40 已安排 | `njsa000`/`njna000`/`njxa000` | **录入检测数据 → 自动判定 → 提交** |
| DEMO-2026-004 鳜鱼 | S60 检验完成 | `nj001` 综合管理 | 报告审核（含 1 个待判定项，可验**放行红线**） |
| DEMO-2026-005 团头鲂 | S70 已审核 | `nj001` 综合管理 | 签发 → 生成 CMA / CMA-CATL 报告 → 打印 |

另补齐「河蟹」项目标准库条目，使既有样品 JK(2026)-SA-002 可正常自动套库。

### 2. 启动后端（8080）

```bash
cd backend
# 本机 MySQL 口令若不是 11111111，请复制 application-dev.yml 模板覆盖
mvn -o -DskipTests spring-boot:run
# 接口前缀 /api，健康检查：POST /api/auth/login
```

### 3. 启动前端（5173，代理 /api → 8080）

```bash
cd frontend
npm install
npm run dev
```

### 4. 登录账号（**密码 = 账号名**）

| 账号 | 角色 | 可见功能 |
|---|---|---|
| `nj001` | R100 综合管理 | 全部（含统计、系统管理、报告审核签发） |
| `nj002` | R1 样品登记员 | 工作台、样品登记 |
| `nj003` | R2 任务管理员 | 工作台、监抽任务、项目分解、任务安排、基础数据、项目库查询 |
| `njsa000` / `njna000` / `njxa000` | R3 检验员 | 工作台、结果录入、我的检验任务 |

## 下载入口在哪里（常见问题）

| 想下载什么 | 入口 |
|---|---|
| 采样单导入模板 | **样品登记**页 → 页头「下载导入模板」（空列表时表格空态里也有一个） |
| 检验员任务 Excel | **我的检验任务**页 → 导出按钮（后端 `GET /api/export/my-tasks`） |
| 省平台上报数据 Excel | **省平台上报**页（`/export/province`，可用任务编号筛选） |
| 检验报告（纸质） | **报告生成**页 → 生成/重打印 → 打印页浏览器「打印 / 另存为 PDF」 |

> 项目**不生成报告 PDF 文件**：报告是白底 A4 的 HTML 页面，打印即得纸质件，
> 需要电子件时用浏览器「打印 → 另存为 PDF」。这是设计选择（避免引入 PDF 生成依赖）。

## 质量门禁

```bash
cd backend  && mvn -o test                     # 后端单测（113 项）
cd frontend && npm run lint && npm run typecheck && npm run build
```

## 文档

详见 `AGENTS.md`、`STATUS.md`、`TODO.md`、`HANDOFF.md`、`DECISIONS.md`、
`docs/api/api-spec.md`（接口契约）、`docs/knowledge/`（选型与踩坑）、`docs/journal/`（工作日记）。
