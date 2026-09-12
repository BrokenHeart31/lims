---
name: lims-stage-delivery
description: LIMS 项目「业务阶段全链路交付」标准流程——从数据前提验证、契约起草、建表、后端分层、单测、前端页面到质量门禁与提交推送的完整清单。当需要交付 LIMS 的某个业务阶段任务（如 T-401 项目分解、T-501 任务安排、T-601 判定引擎、T-701 审核签发、T-702 报告生成等 S/A 级任务）时使用。
agent_created: true
---

# LIMS 业务阶段全链路交付流程

## 触发场景

- 领取 LIMS 项目的某个「阶段任务」（T-4xx / T-5xx / T-6xx / T-7xx / T-8xx）
- 需要新增一张业务表 + 后端 CRUD/业务逻辑 + 前端页面 + 契约 + 单测
- 需要走状态机流转（S10→S90）
- 需要在多 Agent 治理下提交并推送

## 核心原则（血泪教训，务必遵守）

### 原则 1：动手前先证伪前提 ⭐ 最重要

任务描述 / 裁决文档给的是**规则**，但规则作用的**数据**可能根本不存在。
**必须先跑 SQL 验证假设的数据分布，再动手写代码或迁移脚本。**

反面案例（真实发生）：T-401 的任务要求「按 D5 裁决做 `judge_type` 一次性订正脚本」，
隐含前提是 `std_value` 里存在 `不得检出`/`≤数值` 等非纯数值形态。实测发现
`product_lib_item` 3728 行 **100% 纯数值**，源表同样 100% 纯数值，脚本最终是**零变更（no-op）**。
若直接照写，会得到一个永远输出 0 变更的**静默失败**——比脚本报错危险得多。

**标准动作**：写脚本前先跑形态诊断 SQL，把「分布 + 计数」写进脚本注释与裁决请求文档。

### 原则 2：标准库字段快照下沉

凡是「报告要固化」或「人工可改」的字段，从标准库**复制**进业务表，不要只存外键。
理由：① 国标会更新，报告须固化检验当时的判定依据；② 人工调整后的值必须独立于标准库。

### 原则 3：预览不落库，保存覆盖式

涉及「自动生成初稿 + 人工调整」的功能（套库、自动分配等）：
- 生成初稿的 GET 接口**不写库**；
- 保存用**覆盖式**（先逻辑删除再全量重建），不提供增量 patch。

理由：初稿与最终结果是两个概念；分解页是整体工作台，前端保证序号连续唯一。

### 原则 4：状态流转双保险 + 正向/退回**两张独立白名单**

正向：`SampleStatusTransition.assertTransition(旧, 新)` + **乐观条件 UPDATE**（`WHERE id=? AND status=旧值`）。
`updated == 0` 时抛「状态已变更，请刷新后重试」。防并发重复流转。

**逆向（退回）必须走独立白名单**：`RETURN` EnumMap + `assertReturn(旧, 新)`，**不要塞进正向 `VALID` 表**。
否则 `assertTransition(S60, S50)` 变成全局合法，任何调用方都可能误当普通推进使用。
单测必须固化：`assertReturn(S60,S50)` 通过 ∧ `assertTransition(S60,S50)` **拒绝** ∧ `returnAllowed(S70)` 为空。

**状态变更必配审计留痕**（有审批/判定语义的域）：
- 事件流水表**只追加、永不改写**（action / from_status / to_status / opinion / operated_by / operated_at）；
- 业务主表只存**当前有效值**（审核人/签发人）供报告打印；
- ⚠️ MP 实体式 `update` **忽略 null 字段**，「清空某列」必须 `LambdaUpdateWrapper.set(col, null)`
  （`entity.setXxx(null)` 是静默无效的）；
- **放行红线**：存在异常项（未录入/待判定）时，放行动作必须要求显式确认并留痕（`abnormal_confirmed=1`），
  禁止「有异常仍静默放行」。

**「未录入」≠「待判定」**（T-912 定稿）：前者是操作缺漏 → 阻断流程；后者是数据缺口 → 不阻断、审核环节人工裁决。
两者混同会导致「操作缺漏被静默放过」或「样品被永久卡死」。

### 原则 5：提交前必须 `git status --short` 逐项核对暂存区

史上最严重事故（4070ea6）根因就是「未核对暂存区」，把 shell 误解析产生的中文碎片文件名连同
118 个被误删文件一起提交入库。**任何 commit 前必须逐行看 `git diff --cached --name-status`。**

## 交付清单（五件套 + 门禁）

### 第 0 步：状态与环境
1. `git checkout agent/<自己> && git pull`；`git branch -v` 确认引用未丢
2. 读 `STATUS.md` → `TODO.md` → `HANDOFF.md` → `DECISIONS.md` → `AGENTS.md`
3. **前置检索**：`.agents/skills/` → `docs/knowledge/` → `docs/journal/` → 上网

### 第 1 步：数据前提验证（SQL 探针）
```sql
-- 表行数、字段形态分布、空值率、唯一值集合
SELECT COUNT(*) FROM t;
SELECT std_value, COUNT(*) FROM t GROUP BY std_value LIMIT 20;
SELECT COUNT(*) FROM t WHERE std_value REGEXP '^[0-9]+(\\.[0-9]+)?$';
```

### 第 2 步：契约（`docs/api/api-spec.md`）
- 新章节追加，**不覆盖既有章节**（既有章节顺延）
- 每接口写清：路径 / 方法 / 权限标识 / 请求字段 / 响应字段（camelCase）

### 第 3 步：建表（`db/init/NN_xxx_tables.sql`）
新表规范（AGENTS 6.1）：
- `id BIGINT AUTO_INCREMENT` 主键
- snake_case 字段名
- 审计四字段 `created_by/created_at/updated_by/updated_at`
- 逻辑删除 `deleted TINYINT DEFAULT 0`
- 唯一键带 `deleted`（否则逻辑删除后重插会撞键）
- 高频/外键字段建索引

### 第 4 步：后端分层
```
entity/   → 表映射 + MP 注解（业务状态用枚举，禁魔法数字）
mapper/   → extends BaseMapper<T>
dto/      → 请求对象 + JSR-303（@NotEmpty/@NotNull/@Size）
vo/       → 响应对象，脱敏（禁 password/salt）
service/  → 接口 + impl（Impl extends ServiceImpl<M,T>）
controller/ → 参数校验 + @PreAuthorize("hasAuthority('权限标识')")
```
- 查询用 `LambdaQueryWrapper/LambdaUpdateWrapper`，禁字符串拼 SQL
- 分页用 `Page<T>`
- 避免 N+1：列表统计用 `Collectors.groupingBy` 批量聚合

### 第 5 步：单测（`src/test/java/com/lims/service/impl/XxxServiceImplTest.java`）

**MyBatis-Plus ServiceImpl 单测两个必备技巧**：

```java
@BeforeAll
static void initMeta() {
    // ① 每个实体都要调一次，否则 lambda 列名生成失败
    TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SampleItem.class);
    TableInfoHelper.initTableInfo(assistant, Sample.class);
}

@BeforeEach
void injectMapper() throws Exception {
    // ② baseMapper 是 protected，必须反射注入
    Field f = ServiceImpl.class.getDeclaredField("baseMapper");
    f.setAccessible(true);
    f.set(service, sampleItemMapper);
}
```
- `any(Wrapper.class)` 会触发 unchecked 警告（泛型擦除）→ 改 `any()` 并删 `Wrapper` import
- 覆盖：命中/未命中/多候选/参数为空/实体不存在/状态不合法/并发冲突

### 第 6 步：前端
- `src/api/xxx.ts`：接口封装 + 常量选项（`TYPE_OPTIONS`）+ 类型定义
- `src/views/xxx/index.vue`：查询卡片 + 列表 + 抽屉/弹窗
- 路由 `src/router/index.ts` 新增子路由，`meta: { title, permissions }`
- `src/layouts/MainLayout.vue` 新增菜单项（import 对应 icon）

**前端 TS 常见坑**：
- el-table 插槽 `row` 类型是 `DefaultRow`，与业务行类型不兼容 → 模板内 `row as XxxRow` 断言
- `vue-tsc` strict 下未使用的函数报 **TS6133** → 预留未用的 helper 必须删
- 手动删除行后要 **resequence()** 重排序号，保证后端唯一键约束满足

### 第 7 步：质量门禁（AGENTS 第 9 章硬要求）
```bash
# 后端
cd backend && <maven 直启 classworlds> test
# 前端
cd frontend && npm install        # 若 node_modules 缺失
npm run build                     # = vue-tsc --noEmit && vite build
npx eslint --fix <改动文件>        # 先自动修格式
npm run lint                      # 必须 0 错误 0 警告
```
前端格式告警（`vue/max-attributes-per-line`、`singleline-html-element-content-newline`）
用 `npx eslint --fix` 一键清掉，不要手改。

### 第 8 步：收工三件套（用户强制，AGENTS 2.5）
1. **工作日记** `docs/journal/YYYY-MM-DD-<agent>-<主题>.md`
   （目标 / 做法 / 关键设计决策 / 踩坑 / 质量门禁 / 可复用结论）
2. **进度百分比**（固定权重：业务主干 55% + 前端 15% + 数据 10% + 质量 10% + 工程化 10%）
   更新 `STATUS.md`
3. **更新** `TODO.md`（任务状态）、`HANDOFF.md`（@ 下一人）、`DECISIONS.md`（技术决策）

### 第 9 步：提交与推送
```bash
git add -A
git diff --cached --name-status      # ⭐ 逐项核对！确认无 node_modules/dist/target，且不含删除项
grep -rniE "github_pat|ghp_|password" <改动文件>   # 确认无密钥
# ⚠️ 不要用 heredoc 写 commit message（中文+括号会触发 syntax error 整段被吞）
#    先 Write 到 C:\Users\Chen\AppData\Local\Temp\commit.msg，再：
git commit -F /c/Users/Chen/AppData/Local/Temp/commit.msg
git rev-parse HEAD                   # ⭐ 与 fsck 双验证（沙箱会写错 ref 末位 hash）
git fsck --lost-found 2>&1 | head
git branch -v                        # ⭐ 确认 agent/* 引用未丢，丢了用 shell 回填
git update-ref refs/heads/develop <hash>   # 快进（规避 checkout 被 SIGTERM）
git update-ref refs/heads/main <hash>
GIT_TERMINAL_PROMPT=0 GCM_INTERACTIVE=never \
  git -c http.sslVerify=false -c credential.helper= push "https://<PAT>@github.com/<owner>/<repo>.git" agent/xxx develop main
git -c http.sslVerify=false ls-remote "https://<PAT>@github.com/<owner>/<repo>.git"
```
详见 `.agents/skills/sandbox-git-push/SKILL.md`（沙箱 git 全套坑，含规则 6 hash 双验证 / 规则 7 临时文件）。

### 第 7.5 步：端到端联调 + 视觉回归（单测之外的第二道闸）

单测全绿 ≠ 功能可用（`@PreAuthorize`、MP 枚举集合参数、JPA 映射等只在真实链路暴露）。**必须跑端到端**：

```bash
# ① 补表/迁移（本机库）；init 脚本 DROP+CREATE，存量库用 migrations/V*.sql 增量
mysql -uroot -p123456 lims < db/init/07_xxx.sql
mysql -uroot -p123456 lims < db/migrations/V4__xxx.sql

# ② 起后端（spring-boot:run 必须走 run_in_background 托管；用 `(cmd &)` 会在父 shell 退出时被杀）
# ③ 等 "Started LimsApplication" + netstat 确认 8080 LISTENING
```

**端到端脚本模式**（已实测 45 条断言）：写 Python 脚本到 Temp（**不要用 `python -c` 传大段中文**），
用 `urllib.request.build_opener(urllib.request.ProxyHandler({}))` **显式禁代理**
（本机默认走 MITM 代理，访问 localhost 会被拒），逐条 `check(name, cond, detail)` 打印 PASS/FAIL，
覆盖：登录 → 列表 → 明细 → 各分支预览 → 保存 → 提交 → **负向用例（越权/越态/非法入参）**。

**视觉回归**（T-906 教训：DOM 有元素 ≠ 用户看得见）：
```bash
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
"$EDGE" --headless=new --disable-gpu --no-first-run \
  --user-data-dir="C:/Users/Chen/AppData/Local/Temp/edge-lims-profile" \
  --window-size=1600,1000 --virtual-time-budget=20000 \
  --screenshot="C:/Users/Chen/AppData/Local/Temp/shot.png" "<url>"
```
需要登录态时，**临时**在 `frontend/public/__devlogin.html` 放一个同源跳转页
（读 `?t=<token>` 写入 `localStorage['lims_access_token']` 后 `location.replace(?go=...)`），
截图后**立即删除该文件并 `git status` 复核**。token 从 `POST /api/auth/login` 取。

## 沙箱环境备忘

- **Maven 无法用 mvn 脚本**（MAVEN_HOME 解析失败 → ClassNotFoundException Launcher），必须直启 classworlds：
  ```bash
  M2='C:/Users/Chen/.m2/wrapper/dists/apache-maven-3.9.12/59fe215c0ad6947fea90184bf7add084544567b927287592651fda3782e0e798'
  java -classpath "$M2/boot/plexus-classworlds-2.9.0.jar" -Dclassworlds.conf="$M2/bin/m2.conf" \
       -Dmaven.home="$M2" -Dmaven.multiModuleProjectDirectory="D:/lims/backend" \
       org.codehaus.plexus.classworlds.launcher.Launcher <goal>
  ```
- **大段含中文/反引号/引号的命令不要用 Bash 直接传**（会被 shell 错误解析，产生 `command not found` + SIGTERM + 碎片文件）。
  改用「Write 写临时 `.py` 文件到 `%TEMP%` → python 执行该文件」。
- 本机 MySQL 密码 `123456`（非 AGENTS 约定值），在 gitignore 的 `application-dev.yml`。
- ⚠️ **本机 `git.exe` 已不在 PATH**（注册表指向的 `C:\Users\Chen\Desktop\Git` 目录已被删除）。
  改用全路径：`C:\Users\Chen\.workbuddy\binaries\PortableGit\versions\1.2.0\cmd\git.exe`。
  开工先探测 git，勿假设 `git` 可直接调用。
- ⚠️ **改了后端代码必须重启后端**（`spring-boot:run` 不会热加载已加载的类）；
  停旧进程用 PowerShell `Stop-Process -Id <pid> -Force`
  （Git Bash 下 `taskkill //PID` 与 `cmd //c taskkill` 都会因参数改写而失败）。
- ⚠️ **`git add` 前若 ref 被吞，`git status` 会把整仓显示为「已暂存新增」**——
  这是「当前分支 ref 文件消失（HEAD 指向不存在的 ref）」的典型症状。
  恢复：从 `.git/logs/refs/heads/<branch>` 末行取**全 hash**（短 hash 也可能无效）shell 回填 ref 文件。
- JDBC url `characterEncoding` 必须写 `utf8`（Java 字符集名），写 `utf8mb4` 会被 Connector/J 拒。
- **vite dev server 只监听 IPv6 `[::1]:5173`**：浏览器/脚本一律用 `http://localhost:5173`，
  用 `127.0.0.1:5173` 会「拒绝连接」（`netstat` 可见 `[::1]:5173`）。
- **`--virtual-time-budget` 会压缩时间**：路由过渡可能停在半透明 enter 态，截图看起来「整体发灰」——
  判读时必须区分「过渡未完成」与「真的坏了」；必要时加大 budget 或对同一页重截。
- **视觉验证要挑对账号**：R3 检验员没有 `assign:confirm` / `item:decompose`，对应页面必为空列表
  （这是权限正确，不是 bug）。需要看数据时用 `nj001`（R100 全权限）。
- **vue-tsc：`el-table` 作用域插槽的 `row` 是 Element Plus 的 `DefaultRow`**，不是 `any`。
  把 `row` 直接传给强类型函数会报 `TS2345: Argument of type 'DefaultRow' is not assignable to ...`。
  解法：加一个收窄函数 `function rowItem(row: unknown): XxxRow { return row as XxxRow }`，模板里统一 `rowItem(row)`。
  另：`computed` 里用 `.map().filter((x): x is T => ...)` 易触发「类型谓词不可赋值」，
  改成**显式 for 循环 + 类型化数组**最省事。
- **同一文件的多次 Edit 必须串行**：一条消息里并行发多条 Edit 到同一文件，
  会「基于旧内容写回」互相覆盖（Edit 报成功但改动消失），典型症状是编译报「找不到符号」。
- **单测断言日志**：需要验证「必须记 WARN」这类行为时，用 logback `ListAppender`
  挂到目标类的 logger 上（`(Logger) LoggerFactory.getLogger(X.class)`），
  `@BeforeEach` 挂载 / `@AfterEach` 卸载，断言 `event.getFormattedMessage()` 内容。
  组件侧同时暴露一个布尔标记（如 `requiresAttention`）便于编排层断言。

## 验收自检清单

- [ ] 数据前提已用 SQL 验证，不是照抄任务描述
- [ ] 契约章节已追加（未覆盖既有）
- [ ] 建表符合 AGENTS 6.1（审计四字段 + deleted + 唯一键带 deleted）
- [ ] 后端 `mvn test` 全过（含新增单测）
- [ ] 前端 `npm run build` + `npm run lint` 全绿
- [ ] 路由与菜单已接入
- [ ] 工作日记已写、进度百分比已更新、TODO/HANDOFF/DECISIONS 已更新
- [ ] `git diff --cached --name-status` 逐项核对过，无异常文件
- [ ] 无密钥入库
- [ ] 推送后 `ls-remote` 核对过远程
