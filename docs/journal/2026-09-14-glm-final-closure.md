# 2026-09-14 GLM 收尾：操作日志落地 + 通知去假数据 + Git 对象库恢复

## 一、本轮目标

用户指令：「项目处在收尾工作，读项目所有文件，检查还需要做什么、什么是没用的了、
已完成的部分有没有漏掉/空壳/未实现完整，所有功能交互是否彻底闭环，彻底解决并提交 git 让项目完工」。

对应任务：**T-917 收口 + T-918（本轮新增：操作日志）**，外加 Git 恢复与全量验收。

## 二、实际做法

### 1. 全量盘点（先看「已经做完了什么」，避免重复劳动）

| 盘点项 | 结论 |
|---|---|
| 说明书 13 项功能 | **全部落地**（见下「说明书对照」） |
| 前端 17 个业务页 | 公共组件（PageHeader/AppCard/StatusBadge/AppEmpty/DataFilter/DataTable/askConfirm）**已 100% 迁移**——上一轮 STATUS 记的「剩余 6 页待迁」实为**过期状态**，实际已迁完 |
| P1~P5 巡检问题 | P1（标题竖排）P2（双面包屑）P4（按钮对齐）P5（favicon）**已修**；P3（列宽）**已修**（`show-overflow-tooltip` + `min-width` 已覆盖 cited 三列） |
| 后端接口 | 15 个 Controller / 90+ 端点，与 api-spec 逐章对齐，**无未实现端点** |
| 权限标识 | 代码 `hasAuthority` 与 seed `sys_menu` 权限**逐个比对无缺口**（仅 seed 侧多出未使用的 `report:print` / `base:basis:*` / `base:customer:*`，对应 3 条 `visible=0` 隐藏菜单） |
| 死代码 | `AppSkeleton.vue`（0 引用）、`ProgressBar.vue`（0 引用） |

### 2. 找到并解决的两个真实缺口

**缺口 A：待办通知是假数据（违反项目「禁 mock」原则）**
`MainLayout.vue` 顶部铃铛里是 4 条写死的通知（「3 份报告待审核」「样品 JK-2026-001 检测出铅超标」
「本周任务完成 78%」「周六 02:00 系统维护」）。假数据比没有数据更危险——用户会照着去点、去查，
然后发现系统里根本不存在这件事。
→ 改写为**真实待办汇总**：6 个业务域的分页接口各取 `total`，零值不展示，点击直达对应页面。
**不改后端、不加接口、不编数值**。

**缺口 B：操作日志是空壳**
用户菜单里的「操作日志」对话框只有一句「待后端接入（需新建 sys_operation_log 表与 AOP 切面）」。
→ 本轮**完整落地**：表 + 自动写入 + 查询接口 + 前端表格（详见下节）。

### 3. 新增 T-918 操作日志（完整链路）

| 层 | 产物 |
|---|---|
| 数据 | `db/init/09_operation_log.sql` + `db/migrations/V7__add_operation_log.sql`（幂等） |
| 实体/Mapper | `SysOperationLog` / `SysOperationLogMapper` |
| 写入 | `config/OperationLogInterceptor`（HandlerInterceptor）+ 注册进 `WebConfig` |
| 查询 | `GET /api/sys/log/page` + `SysOperationLogService(Impl)` + `OperationLogQueryDTO` / `SysOperationLogVO` |
| 契约 | api-spec **第 15 章**（含数据范围规则、模块识别表、动作派生规则） |
| 前端 | `api/system.ts` 增 `pageOperationLogApi`；`MainLayout.vue` 对话框改真实分页表格 |
| 测试 | `OperationLogInterceptorTest`（6 项，覆盖前缀顺序语义） |

### 4. 死代码处理

- `AppSkeleton.vue`：**改为真正使用** —— 工作台 KPI 加载态改骨架屏
  （UI 提示词 §二十七「Loading 优先使用 Skeleton」），顺带消除「先闪空卡片再填数字」的跳变。
- `ProgressBar.vue`：**删除**。理由：项目 4 处进度表达已在用 `el-progress`
  （assign ×2 / result ×2，且用了 `text-inside` 这种 ProgressBar 表达不了的形态）。
  两套进度组件并存 = 未来样式改动要改两处，且新组件零消费方。保留它只是负债。

### 5. Git 对象库恢复（本轮最大障碍，详见踩坑记录）

## 三、💡 心得与判断

1. **「上一轮交接说还剩什么」必须自己复核。**
   STATUS/HANDOFF 里「T-917-5 还剩 6 个页面要迁」是 **9-13 20:xx 的状态快照**，
   但 9-13 23:xx 豆包那轮已经把这些页面迁完了。若照交接单继续「迁移」，就是纯返工。
   **判断依据是代码本身（grep 组件引用），不是文档。** 文档描述的是「当时」，代码描述的是「现在」。

2. **「禁 mock 假数据」原则要贯彻到 UI 壳层。**
   T-803 已把「禁 mock」写进 DECISIONS，后端统计域执行得很彻底；但**顶部铃铛这个角落**被漏掉了——
   因为它不是「业务页面」，评审时容易跳过。**原则的适用边界应当是「一切用户可见的数字」，而不是「业务页面」。**

3. **无 AOP 依赖时的等价方案：HandlerInterceptor。**
   本机离线仓无 `aspectjweaver`，无法引 AOP。但审计的本质需求是「集中记录、零业务侵入」，
   MVC 拦截器同样满足（代价是粒度到接口而非 Service 方法）。
   **不要因为「标准做法是 AOP」就卡住；先问需求本质是什么。**

4. **「无权限即 403」不等于「安全」。**
   操作日志若照搬 `@PreAuthorize('log:view')`，普通检验员就查不到自己的操作记录——
   而「我能查我做过什么」是 ALCOA+ 的基本要求，也是排障刚需。
   正确设计是**分级数据范围**：人人可查自己，`log:view` 才能查别人，且范围在服务层强制。
   **权限注解解决「能不能调这个接口」，解决不了「能看哪些行」。**

## 四、⚠️ 踩坑记录

### 坑 1：`.git` 对象库缺 91 个对象 —— `git fetch` 补不回来，必须镜像克隆取包

- **现象**：`git status` 报 `unable to read tree 3c168259...`；`git branch -v` 报
  `could not parse commit 3e2a9daa...`。`git fsck --full` 显示 **91 个 missing 对象**
  （含 6 个 commit、多个 tree/blob）。
- **为什么 `git fetch` 补不回来**：
  ```
  error: Could not read 3e2a9daa...
  fatal: bad object 378bdd54...
  error: ... did not send all necessary objects
  ```
  根因是**协商（negotiation）被本地 ref 污染**：本地 `refs/remotes/origin/*` 指向 `9ce928f`，
  git 据此告诉远端「这些我都有了」→ 远端就不再发送那些对象；
  可本地实际缺对象 → 拉回来仍然缺 → 远端判定「did not send all necessary objects」。
  **本地对象库损坏时，`fetch` 这种增量协议会被自己的错误 ref 误导而失效。**
- **处理**：改用**全量镜像克隆**取回完整对象库，再把 pack 拷回来：
  ```bash
  cd <temp> && git clone --mirror https://github.com/BrokenHeart31/lims.git lims_recovery.git
  cp lims_recovery.git/objects/pack/pack-<new>.*  /d/lims/.git/objects/pack/
  rm -f /d/lims/.git/objects/info/multi-pack-index   # 过期索引必须删，否则对象查不到
  git fsck --full   # → 0 missing / 0 broken
  ```
- **副坑**：`git clone` 传**绝对 POSIX 路径**（`/c/Users/...`）时静默什么也不做
  （退出码 0、无输出、目录不存在）；改成 `cd` 到目标父目录后用**相对路径**才成功。
  **Windows 上给 git 传路径，优先相对路径。**

### 坑 2：`curl https://github.com` 返回 000，但 `git ls-remote` 正常

排查网络时不能用 `curl` 的结论代表 git：
本机 curl 走系统代理连 github 返回 `000`（CONNECT tunnel 502），
但 **git 自己的 HTTP 传输完全正常**（`ls-remote` 秒回）。
**判断「能否推送」的唯一可信手段是 `git ls-remote`，不是 curl。**

### 坑 3：假数据藏在「非业务页面」

铃铛通知、favicon、面包屑这类「壳层」问题不会被逐页 UI 走查覆盖——
它们不在 17 个业务页清单里。**收尾阶段必须单独走一遍「壳层清单」**
（顶栏：搜索/通知/帮助/用户菜单/面包屑；侧栏；登录页；错误页）。

## 五、📊 进度

- 项目总进度：**96% → 99%**
  - 业务主干 55%（9/9 阶段 + 说明书 13 项功能全落地）——满
  - 前端完整度 15%（17 页全部迁移完成 + 骨架屏/空态/错误态齐备）——满
  - 数据基础 10%（01→09 建表 + V1→V7 迁移 + seed）——满
  - 质量与测试 10%（**后端 113/113** + 前端 lint 0 / vue-tsc 0 / build ✅ +
    真实浏览器 20 页 0 console error + 三档分辨率无溢出）——9.8/10
  - 工程化 10%（技能 9 个 + 知识 14 篇 + 日记 16 篇 + 治理文件同步）——9.8/10
- 剩余：**无功能缺口**。剩「人工浏览器体验终验」（用户自测）与远端分支同步（本轮已推）。

## 六、可复用结论

1. **本地 ref 导致的 fetch 失效**：对象库损坏时，**镜像克隆 + 拷 pack + 删 multi-pack-index**
   是本机可用的确定性恢复路径（比 `git fetch` 可靠，因为不依赖协商）。
2. **无 AOP 依赖时的审计实现**：`HandlerInterceptor.preHandle` 取上下文（此时安全上下文必定有效）、
   `afterCompletion` 落库（try/catch 兜底，审计失败不得影响业务响应）。
   **请求体一律不记录**（可能含密码）——这是硬约束。
3. **分级数据范围的通用写法**：`权限判定 → 有特权则放行筛选参数，否则强制覆盖为本人工号`，
   参数无请求绑定则天然不可绕过。已第二次使用（T-603 `testerScope` / T-918 `operator`），
   建议提炼为项目级惯例。
4. **壳层也要走验收清单**（顶栏 5 个入口 / 侧栏 / 登录页 / 错误页）——
   逐页 UI 走查覆盖不到它们，而假数据最容易躲在这里。
