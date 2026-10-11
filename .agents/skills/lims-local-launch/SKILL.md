---
name: lims-local-launch
description: LIMS 项目本地三服务（后端 8080 / 前端 5173 / Ollama 11434）的标准启动、停止与冒烟验收流程。当用户说「启动让我验收」「重启服务」「系统怎么起」「项目跑起来」或在验收前需要复跑质量门禁与冒烟时使用。
agent_created: true
---

# LIMS 本地启动与冒烟验收

> 场景：D:\lims（Windows + Git Bash 沙箱）。本流程 2026-10-11 实测验证：三服务启动 + API 冒烟 + 浏览器 6/6 核查全过。

## 触发场景

- 用户要求「启动项目供验收 / 重启服务 / 复跑门禁」
- 需要确认「代码全绿」之外「系统真能跑」

## 0. 前置检查（30 秒）

```bash
# ① 端口占用（有输出说明已在跑，先决定是否重启）
netstat -ano | grep -E ":8080|:5173|:11434"
# ② 前端依赖与 .bin 空坑（本机 node_modules/.bin 是空的，一律直调 node 二进制）
ls /d/lims/frontend/node_modules/vite/bin/vite.js
# ③ MySQL 库（开发库 lims 应在跑）
"C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe" -uroot -p123456 --default-character-set=utf8mb4 -e "SELECT COUNT(*) FROM sample_info WHERE deleted=0;" lims
```

## 1. 重建 Maven 直启脚本（重启后 /tmp 会丢，必检）

本机无独立 Maven；用 wrapper 里的 3.9.12。**该哈希目录本身就是 Maven home**（没有嵌套层）。
`-Dmaven.multiModuleProjectDirectory` **必须加**——缺它只输出一行 "system property is not set." 后静默退出。

```bash
cat > /tmp/lims-mvn.sh << 'MVNEOF'
#!/bin/bash
MVN_HOME="C://Users//Chen//.m2//wrapper//dists//apache-maven-3.9.12//59fe215c0ad6947fea90184bf7add084544567b927287592651fda3782e0e798"
JAVA_EXE="D://Program Files//Java//jdk-21.0.10//bin//java.exe"
exec "$JAVA_EXE" -classpath "${MVN_HOME}//boot//plexus-classworlds-2.9.0.jar" -Dclassworlds.conf="${MVN_HOME}//bin//m2.conf" -Dmaven.home="${MVN_HOME}" -Dmaven.multiModuleProjectDirectory="${MVN_HOME}" org.codehaus.plexus.classworlds.launcher.Launcher "$@"
MVNEOF
chmod +x /tmp/lims-mvn.sh && bash /tmp/lims-mvn.sh -v | head -3
```

> ⚠️ 路径必须 `C://...` Windows 形式；POSIX `/c/...` 让 java.exe 报「找不到主类 Launcher」。

## 2. 启动三服务（一律 Bash run_in_background=true，不加 nohup/&）

| 服务 | 命令（工作目录见左） | 端口 |
|---|---|---|
| 后端 | `cd /d/lims/backend && bash /tmp/lims-mvn.sh -o -DskipTests spring-boot:run` | 8080 |
| 前端 | `cd /d/lims/frontend && "C:/Users/Chen/.workbuddy/binaries/node/versions/22.22.2-2/node.exe" node_modules/vite/bin/vite.js` | 5173 |
| Ollama | `cd /d/lims/ai/runtime && OLLAMA_MODELS='D:\lims\ai\models' OLLAMA_HOST='127.0.0.1:11434' ./ollama.exe serve` | 11434 |

启动后 25~40s 检查：`netstat -ano | grep -E ":8080|:5173|:11434"`（5173 只监听 `[::1]`，用 `localhost:5173` 访问，不要用 127.0.0.1）。

> ⚠️ **PowerShell 工具在本机沙箱可能静默失败**（任意命令 exit 1 无输出，2026-10-11 实测）——启动/停止服务不要依赖它；停进程候选：`taskkill //F //PID <pid>`（Git Bash，历史上失败过，需现场验证）。

## 3. 冒烟（API，每轮验收前跑）

- 模板脚本：`_logs/accept-check-2026-10-11.py`（浏览器）；API 冒烟见下方要点；
- 直接调 API 的坑：**禁 PowerShell 5.1 发中文 JSON**（会写坏库），用 curl / Node / Python；
  `curl -o /dev/null` 偶发 exit 23，输出重定向到 shell 变量最稳。
- 关键断言（全部应过）：
  1. `POST /api/auth/login` `{"username":"nj001","password":"nj001"}` → `code=0`；
  2. `GET /api/auth/me` → `user=nj001`、**61 权限 / 12 菜单**；
  3. `GET /api/sample/page?pageNum=1&pageSize=5`（注意**该域用 pageNum/pageSize**，current/size 会 400）→ total>0；
  4. `/api/item/pending`、`/api/assign/pending`、`/api/result/pending`、`/api/report/audit/pending`（均 `current/size`）→ code=0；
  5. `GET /api/ai/status` → `online=true / modelPresent=true / kbReady=true`。

## 4. 浏览器冒烟（系统 Python 3.12 + playwright，已装）

```bash
"C:/Users/Chen/AppData/Local/Programs/Python/Python312/python.exe" /d/lims/_logs/accept-check-2026-10-11.py
```

- 6 项核查：登录页 / 登录跳转 / 工作台 / 样品登记表体 / AI 悬浮球（`.ai-fa`）/ 报告审核页；console error 应为 0；
- 登录选择器：`input[placeholder="用户号（工号）"]`、`input[placeholder="登录密码"]`、`button.login-button`。

## 5. 质量门禁复跑（验收前推荐）

```bash
# 后端（约 40s）
cd /d/lims/backend && bash /tmp/lims-mvn.sh -o test | tail -5     # 期望 261/261 BUILD SUCCESS

# 前端（以旧版 .bin 为空，直调 node；约 1.5~2min）
cd /d/lims/frontend && NODE="C:/Users/Chen/.workbuddy/binaries/node/versions/22.22.2-2/node.exe" && \
  "$NODE" node_modules/vue-tsc/bin/vue-tsc.js --noEmit && \
  "$NODE" node_modules/eslint/bin/eslint.js . && \
  "$NODE" node_modules/vite/bin/vite.js build
```

## 6. 验收账号与演示数据

- 账号（密码=账号名）：`nj001`（全权限）/ nj002 登记 / nj003 任务 / njsa000·njna000·njxa000（检验员）；
- 演示样品（seed03 标准态）：DEMO-2026-001~005 = **S10/S30/S40/S60/S70**；
  **禁止硬编码 DEMO id**（seed03 是删除重建，自增 id 每次都变，按 `sample_no` 动态查）；
- 需要还原演示数据：`mysql ... lims < db/seed/03_demo_flow_seed.sql`（幂等）。

## 7. 已知坑速查

| 坑 | 处理 |
|---|---|
| Maven 静默退出 | 补 `-Dmaven.multiModuleProjectDirectory` |
| `/tmp/lims-mvn.sh` 消失 | 按第 1 节重建 |
| 端口被占 | `netstat -ano` 找 PID → 尝试停进程（PowerShell 工具若可用则 Stop-Process -Id <pid> -Force）|
| 后端接口 404 | 后端未重启（spring-boot:run 不热加载，改 Java 必须重启）|
| vite 落 `[::1]` | 访问用 `localhost:5173` |
| 页面空白 | 换 nj001 全权限账号对照，多为权限渲染差异而非 bug |
