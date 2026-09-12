# 2026-09-12 Copilot — T-601 复核终审（契约 + 规则 + diff）与 T-701 铺垫

> Agent：Copilot（agent/copilot）。本轮为**复核轮**，不产实现代码。
> 用户指示：本会话为降级模型，以**提建议 + 交接铺垫**为主，S 级实现全部留给下一轮。

## 1. 本轮目标

- 关联任务：T-601 终审复核（GLM 三类职责中我保留的：契约终审 / 规则复核 / diff 审查）；
  为 T-701 做**只读铺垫**（不实现）。
- 输入：GLM 交接留言 + 本地提交 `887067e`（T-601 全链路，37 文件，本地三分支已快进，**远端未推**）。

## 2. 检索留痕（动手前先检索，AGENTS 2.5 第 3 件）

| 顺序 | 查了什么 | 查到什么 | 是否采用 |
|---|---|---|---|
| 1 | `.agents/skills/judge-engine/SKILL.md` + `lims-stage-delivery` + `sandbox-git-push` | 引擎四原则、验收自检清单、沙箱 git 坑清单 | ✅ 作为复核基准逐条对照 |
| 2 | `docs/knowledge/2026-09-11-judge-engine-whitelist.md`（T-902 定稿 D1–D5） | 判定口径唯一依据 | ✅ 逐格比对引擎矩阵 |
| 3 | `docs/journal/2026-09-12-glm-t601-result-judge.md` + HANDOFF 15:10 | GLM 自述的踩坑与修复（未检出/不得检出常量、并行 Edit） | ✅ 在代码中验证修复确已固化 |
| 4 | 未上网检索 | 复核为内部一致性比对，无需外部资料 | — |

## 3. 复核方法与证据（全部实测，非转述 GLM 自述）

1. **逐文件读源码**：JudgeEngine（317 行）/ JudgeInput/JudgeOutcome / ResultServiceImpl（531 行）/
   ResultController / 3 DTO / ResultDetailVO / SampleResult / Sample（conclusion 增量）/
   07_result_tables.sql / V4 / api-spec 第 6 章 / 前端 api/result.ts。
2. **质量门禁复跑**：本机 `mvn test` → **Tests run: 85, Failures: 0, Errors: 0，BUILD SUCCESS**
   （引擎 34 = 6 分组 + Parsing4/OutOfWhitelist7/Manual4/NoBasis6/NotDetected5/LimitCompare8；编排 15）。
3. **提交完整性**：`git show --stat 887067e` = 37 文件、4444+/32-，与 STATUS 申报清单一致，无删除项；
   `git diff fd6897b..887067e -- AssignServiceImplTest.java` = **0 行**（见踩坑①勘误）。
4. **规则口径比对**：D1（<检出限视同未检出→jt1 合格）、D2（≥检出限才算检出；检出限 NULL→待判定）、
   D4（`--` 矩阵）、闭集外 default→pending+WARN、jt3 人工（source=2）逐格核对实现，全部一致。
5. **回溯禁令**：grep `ResultServiceImpl` + `service/judge/*` 中 `ProductLib|product_lib|prj_detail`
   → **零命中**，D5「引擎只读 sample_item」落实。
6. **状态机现状**（T-701 铺垫）：白名单 S40→S50、S50 自环+S60、S60→S70、S70→S80、S80→S90 均已就位；
   **S60 出边仅 S70，「审核退回→S50」缺失实锤**（与 HANDOFF 预警一致）。

## 4. 💡 终审结论：**T-601 通过（含 2 条保留意见，均转 TODO，不阻塞）**

### 予以追认（证据充分的通过项）
- 判定矩阵与 D1/D2/D4/D5 一致；D3 聚合正确（`computeOverall`：全参考项→待判定、参考项不入整体）。
- 「不得检出 / 未检出」已各自定义常量（`NOT_DETECTED_STD_TEXT` / `NOT_DETECTED_TEXT`），GLM 自查缺陷修复固化。
- 全部数值比较走 `BigDecimal.compareTo`；`parseNumber` 解析失败返 null→待判定，不抛异常打断整批。
- 原始值/派生值分层落库 + 快照只读 + 一项一行覆盖式 upsert（uk `(sample_item_id, deleted)`）。
- 状态流转双保险（assertTransition + 乐观 UPDATE）与既有域同构；「待判定不阻断提交」有决策留档。
- 契约第 6 章 ↔ Controller/DTO/VO/前端 api/result.ts 逐字段一致；`result:entry` 与 seed id=61 一致。

### 保留意见（P2，已开 TODO 给下一轮 GLM 裁决与实施）
- **T-911 分页参数双轨**：api-spec 0.3 说列表接口统一 `pageNum/pageSize`，但 item/assign/result
  三域实际用 `current/size`（spec 4.6/5.2/6.2 也这么写，Controller 与前端一致，运行无碍）。
  T-401 引入、T-501/T-601 沿用的契约总纲违背。建议方向二选一：**(a)** 修订 0.3 承认双轨（成本 0）；
  **(b)** 统一回 `pageNum/pageSize`（动 3 Controller + 3 前端 api）。倾向 (a)，留 GLM 自裁。
- **T-912「已录入」定义允许空值行**：`ResultSaveDTO.Item.testValue` 无 @NotBlank，
  空值可落一行 `conclusion=3`；而 `submit` 的录齐校验只看 `results.containsKey(itemId)`
  → **可带空结果行提交至 S60**。与「待判定不阻断、红线在审核」自洽、非安全洞（不会静默判合格），
  但属流程卫生缺口。建议：「已录入」改为 `testValue 非空 || (jt3 且 manualConclusion∈{1,2})`，
  或 T-701 审核通过时强制展示待判定/空值清单。两种落点请 GLM 对照业务说明书后自裁。

### 非阻塞备忘（P3）
- `parseStdValue` 把 `<` 前缀归入 `LE_NUMERIC`（按 ≤ 处理）：`<0.25` 与 `=0.25` 时判合格，边界语义近似。
  现库 std_value 100% 纯数值，触发概率为零；将来人工录入 `<` 形态前应严格化（加 LT 形态 + 单测）。
  建议下一轮在 judge-engine 技能补一行说明即可。
- `upsertResult` 在 save 循环内逐项 `selectOne`（N+1）。单样品几十项量级可接受；如优化，循环外批量查。
- 勘误：T-501 历史申报「AssignServiceImplTest 14 项」实为 **13 项**（本轮实测），T-601 未触碰该文件。

## 5. ⚠️ 踩坑记录

1. **环境突变：本机 git.exe 消失**。注册表 `HKLM\SOFTWARE\GitForWindows` 仍指向
   `C:\Users\Chen\Desktop\Git`，但该目录已被删除（上轮会话后环境变化）。`where git` 无结果。
   **处理**：改用 WorkBuddy 自带 PortableGit：
   `C:\Users\Chen\.workbuddy\binaries\PortableGit\versions\1.2.0\cmd\git.exe`。
   ⚠️ 下一轮开工请先探测 git 路径（`where git || 用 PortableGit 全路径`），勿假设在 PATH。
2. **本会话 Bash 包装是 Windows cmd**：`set X=... & %X%` 变量展开失效（报「不是内部或外部命令」）；
   `&&` 可用但反斜杠路径 + 变量组合不可靠。**处理**：全路径直呼 + PowerShell 处理文本。
3. **agent/copilot、agent/doubao 引用再丢**（沙箱坑第 N 次复发）：`refs/heads/agent/` 仅剩 glm。
   reflog 完好（copilot 尾行 1237277、doubao 6282c64），已按既定流程 shell 回填。
4. **`mvn test` 产物 `backend/mvn-test.log` 勿提交**：收工前删除；`git status --short` 逐项核对（红线）。
5. **历史申报笔误排查法**：对「数量对不上」先 `git diff A..B -- <file>` 判定是否被本轮改动，
   再下「偷改测试」结论——本轮 0 diff 即勘误而非质询。

## 6. 📊 进度（固定口径，AGENTS 2.5 第 2 件）

- **项目总进度：74% → 74%**（复核轮不产产能）。
- 增量说明：质量项「85/85 由 Copilot 独立复跑证实」；契约一致性债务（T-911）与流程卫生缺口（T-912）
  已显性化为 TODO 条目——**隐性风险下降，进度数字不变**。

## 7. 可复用结论（沉淀建议）

- 「终审复核三证据法」可复用：① 门禁独立复跑（不信自述）② `git show --stat` + 定向 diff 核对
  申报文件清单与历史疑点 ③ 关键禁令 grep 验证（如回溯禁令）。
- T-701 铺垫结论（交接给下一轮）：状态机正向白名单已含 S60→S70→S80→S90；**退回分支建议不要塞进
  `VALID` 正向白名单**（会让 assertTransition(S60,S50) 全局合法、易被误用），建议独立
  `RETURN` 表 + 专用方法（如 `assertReturn(S60,S50)`），同步 AGENTS 7.2 表格与
  `SampleStatusTransitionTest`；审核页放行红线建议：通过审核前必须展示「待判定/空值项」清单并显式确认。
