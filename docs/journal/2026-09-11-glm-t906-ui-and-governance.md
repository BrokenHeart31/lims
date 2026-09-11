# 2026-09-11 GLM（本轮 2）— UI「Aurora Glass」主题 + V3 fail-loud + 治理二次调整

> 任务：T-906（UI 基准）/ T-907（V3 fail-loud）/ T-908（治理二次调整）｜分支 `agent/glm`｜起 `2e9f471`
> 用户指令：「尽量不依赖 copilot 裁决」「杂事交豆包」「UI 参考 mine radio」「方便我启动一次完整项目测试」

---

## 一、本轮目标

1. 执行 Copilot 对 T-905 的裁决（D5 → R1 采纳，并要求 V3 **fail-loud**）
2. 按用户要求建立 UI 设计基准（参考 Mineradio）
3. 治理二次调整：GLM 自裁机制、豆包分工、许可合规红线
4. 打通本地「可运行」链路，为用户的完整项目测试做准备

---

## 二、做法与关键判断

### 2.1 V3 fail-loud：先探机制，再改脚本

裁决要求「应然 ≠ 实然且 UPDATE 后仍不一致必须报错退出」。我先做**机制探测**（不直接改脚本）：

| 尝试 | 结果 | 结论 |
|---|---|---|
| 非存储程序直接 `SIGNAL` | ✅ MySQL 8.0.45 支持（不受限于是存储程序） | 可用 |
| 动态 `PREPARE` + `SIGNAL` | ❌ `ERROR 1295: not supported in the prepared statement protocol` | 排除 |
| `SELECT 1/0` 条件化除零 | ❌ 返回 NULL，sql_mode 含 `ERROR_FOR_DIVISION_BY_ZERO` 也只对 INSERT/UPDATE 生效 | 排除 |
| **存储过程 + `SIGNAL`** | ✅ 报 `ERROR 1644` 且**中止后续语句**（mysql 客户端遇错默认中断，退出码 1） | **采用** |

**顺带收获的两个坑**：
- `SIGNAL` 的 `MESSAGE_TEXT` 上限 **128 字符**（不是 128 字节）。我第一版消息约 135 字符，报 `ERROR 1648: Data too long for condition item 'MESSAGE_TEXT'`——**报错位置是 SIGNAL 那行，排查时会误导**。改法：把诊断明细放进**结果集**（SIGNAL 前先 SELECT），消息只留短摘要。
- 中文消息必须配合 `--default-character-set=utf8mb4`，否则存储过程局部变量报 `Incorrect string value`。

**脚本本身的两处实质改进**（超出裁决要求，但属必要）：
1. **消除 SET/WHERE 双写漂移**：原写法 `SET judge_type = CASE...` 与 `WHERE judge_type <> CASE...` 各写一遍同样的 CASE，改一处忘另一处就会造成部分行不更新。改为**派生表统一算一次 `expected`**，再 `JOIN` 更新。
2. **NULL 安全比较**：`judge_type <> expected` 在 `judge_type IS NULL` 时为 `UNKNOWN` → **该行被漏更新且永久漂移**。改用 `NOT (judge_type <=> expected)` 才真正收敛。

**验证必须走双路径**（只测通过路径等于没测）：
- 正常数据 → 3728 行零变更 → 断言通过、退出码 0 ✅
- 人为制造 `drift=1 / outside=1` → 输出明细 + `ERROR 1644` + 退出码 1 ✅
- 再跑一次 → 自动归一化收敛、恢复退出码 0 ✅（顺带证明脚本的修复能力）

### 2.2 UI：先查许可，再定"华丽"的实现方式

**第一步不是写 CSS，而是查许可**：Mineradio 是 **GPL-3.0**。
若逐字拷贝其 `index.css` / SVG 滤镜代码，本项目仓库会被 GPL 传染——交付直接出问题。
故定下原则：**只借鉴设计思路与参数关系，代码 100% 独立实现**，并把这条写进 AGENTS 5.1 作为红线。

**提炼出的设计语言**（详见 `docs/knowledge/2026-09-11-ui-design-mineradio-research.md`）：
- 近黑冷调底（`#08090B`）+ 极光三色（青 `#00F5D4` / 蓝 `#2442FF` / 金 `#F4D28A`）
- **签名元素是"折射玻璃"而非毛玻璃**：`feTurbulence` → `feDisplacementMap` → RGB 三通道分离 → `screen` 混合。项目原作者明确否定"普通毛玻璃""中间糊成一团""白色渐变扫过去的廉价感"。
- 缓出曲线 `cubic-bezier(.16,1,.3,1)`；暗色层级靠 **1px 白色内发光** 而非提亮底色。

**但 LIMS 不能照搬**（这是本文最有判断价值的部分）：

| 维度 | Mineradio | LIMS 决策 | 理由 |
|---|---|---|---|
| 装饰密度 | 粒子/镜头/3D 架 | 克制静态极光 + 玻璃卡片 | 检验员每天录入数百条数据，动态粒子分散注意力 |
| 强调色用于按钮 | 浅青直接做底 | **青只做光晕**；按钮用深青→蓝渐变 | 浅青底 + 白字对比度仅约 2.7:1，看不清 |
| 圆角 | 面板 22–50px | 卡片 14–18px | 50px 会切掉表格四角 |
| 动效 | 4–5s 长进场 | ≤0.45s | 办公系统要快，不要仪式感 |

**一句话原则**：沉浸式视觉只用于**外壳与第一眼表面**，数据密集区用高对比面板。
落地为三类表面：`.lims-glass` / `.lims-glass-refract`（仅登录卡、品牌区、抽屉头） / `.lims-panel`（表格区）。

### 2.3 🔴 视觉回归发现真实缺陷（本轮最有价值的发现）

截图验证项目分解页时发现：**「分解进度」与「状态」两列完全空白**。

排查过程（**没有靠猜**）：
1. 先查 API → `statusLabel: "登记确认"`、`itemCount: 0` **字段正常** → 排除后端
2. `--dump-dom` → 标签元素**存在于 DOM 且文本正确** → 排除渲染分支
3. 动态 `PREPARE` 探针（同源 iframe + `getComputedStyle`）→ **`opacity=0`，且类名为 `el-zoom-in-center-enter-from el-zoom-in-center-enter-active`** → **定位根因**

**根因**：`el-tag` 在**异步数据到达后**才挂载，Vue 的过渡流程会先加 `enter-from`（`opacity:0`），再用**双 rAF** 的 `nextFrame` 摘掉它。该 rAF 回调若未执行（渲染/时间推进被打断），过渡类**永不摘除**，元素永久 `opacity:0`。

**修法**：在 `element-override.css` 里统一 `transition: none !important`——Vue 的 `getTransitionInfo` 会判定「无过渡」，直接 `resolve()` 并**立即摘除类名**；再对 `enter-from/active` 强制 `opacity:1` 兜底。修复后 DOM 中过渡类消失，标签正常显示（已复截图确认）。

**教训（写进技能库）**：**纯 CSS 主题改造必须做视觉回归**。DOM 里有元素 ≠ 用户看得见；`getComputedStyle` 才是判据。

---

## 三、踩坑记录

| 现象 | 根因 | 处理 |
|---|---|---|
| `ERROR 1648 Data too long for condition item 'MESSAGE_TEXT'` | `SIGNAL` 消息上限 128 字符 | 明细移入结果集，消息精简至约 60 字符 |
| `ERROR 1295 not supported in the prepared statement protocol` | `SIGNAL` 不支持动态 PREPARE | 改用存储过程（`DELIMITER`） |
| 存储过程报 `Incorrect string value` | 客户端字符集非 utf8mb4 | 脚本首行 `SET NAMES utf8mb4` + 执行时 `--default-character-set=utf8mb4` |
| `SELECT 1/0` 不报错 | MySQL 除零在 SELECT 返回 NULL | 放弃该方案 |
| 表格两列空白 | `el-tag` 卡在 `enter-from`（opacity 0） | `transition:none` + opacity 兜底 |
| 后端无法启动 | `application-dev.yml` 不存在（gitignore） | 本地新建，覆盖本机口令 123456 |
| `sample_item` 表不存在 | `db/init/06` 未在该库执行过 | 执行建表脚本 |
| 截图里过渡态异常 | `--virtual-time-budget` 压缩时间，rAF 可能未跑 | 判读截图时留意；关键结论用 `getComputedStyle` 验证 |

---

## 四、质量门禁

- 前端 `npm run build`（`vue-tsc --noEmit` + `vite build`）✅
- 前端 `npm run lint` ✅ **0 错误 0 警告**
- 后端 `mvn test` ✅ **23/23**（本轮未改后端代码，复跑确认）
- 端到端：后端 `spring-boot:run` 起在 8080，`nj001/nj001` 登录成功，`/api/item/pending` 返回 2 条
- 视觉回归：登录页 / 工作台 / 项目分解页 三屏截图确认

---

## 五、进度

**业务主干 4/9（T-501 进行中）｜项目总进度 41% → 46%**

增量来源：前端完整度 8→11（统一主题体系落地）、工程化 7→8（自裁机制/豆包分工/UI 基准/许可红线落档）。

---

## 六、可复用结论

1. **测机制再改脚本**：不确定 SQL/框架能力时，先用最小样本探测（列 4 种方案对比），别在正式脚本里试错。
2. **`SIGNAL` 三要点**：可用于非存储程序；不支持动态 PREPARE；`MESSAGE_TEXT` ≤128 字符（中文按字符计）。
3. **SQL 幂等更新两原则**：应然值只算一次（派生表）；比较必须 NULL 安全（`<=>`）。
4. **fail-loud 要双路径验证**：只测"通过路径"等于没测，必须人为制造失败态确认会报错退出。
5. **参考第三方 UI 先查许可**：GPL/AGPL 只能借鉴思路，代码独立重写。
6. **华丽 ≠ 堆装饰**：配色关系 + 质感（折射而非模糊）+ 动效曲线三者同时成立；且**数据密集区必须让位可读性**。
7. **强调色与按钮底色分离**：浅色强调色做光晕，按钮用深色渐变保证对比度。
8. **CSS 改造必做视觉回归**：`--dump-dom` 看元素、`getComputedStyle` 看可见性、截图看观感，三者缺一不可。
9. **headless 截图的时间陷阱**：`--virtual-time-budget` 压缩时间，过渡可能停在 `enter-from`，判读截图需谨慎（但也正是它暴露了上述缺陷）。
