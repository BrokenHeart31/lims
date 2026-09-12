# 2026-09-12 豆包：T-802 省平台上报格式定稿 + 项目启动交付

> 本轮目标：以豆包身份完成 B 级任务 T-802 的可交付部分，并把项目跑起来交用户测试。
> 关联任务：T-802（省平台上报 Excel 导出，B）；兼做环境启动与状态维护。

## 本轮目标

1. 读 STATUS/TODO/HANDOFF/DECISIONS，对齐全局（GLM 刚完成 T-913 UI 重整，进度约 84%，样品 1 在 S80）。
2. T-802 豆包侧交付：从业务说明书抠出省平台上报格式，落格式规范 + 样例。
3. 启动前后端，验证可用，交用户测试。

## 实际做法

### 环境与启动（本机）

- git 不在 PATH：注册表指向 `C:\Users\Chen\Desktop\Git` 已被删，实际用 PortableGit
  `C:\Users\Chen\.workbuddy\binaries\PortableGit\versions\1.2.0\cmd\git.exe`（与 STATUS 环境备忘一致）。
- Maven 也不在 PATH：实际在 `C:\Users\Chen\Desktop\apache-maven-3.9.11\bin\mvn.cmd`（java 21 自带 javapath）。
- MySQL80 服务运行中，库 `lims` root/123456，表齐全；样品 1 = `JK(2026)-SA-001` 在 status=80、conclusion=2（不合格），7 个检验项均有结果。
- 后端：`mvn spring-boot:run`，Tomcat 8080 + context-path `/api`，2.7s 起。
- 前端：`npm run dev`（vite 6.4.3），http://localhost:5173/，代理 `/api`→8080。
- 验证：nj001/nj001 登录拿 token，`/api/auth/me` 返回 49 权限、11 菜单；经 5173 代理登录 code=0；首页 200。

### T-802 抠格式（说明书 image30）

- 说明书 docx 第十一节文字只说"导出数据形式如下"，真正的列样例是**内嵌截图**。
- 用 python zipfile 解开 docx，按 document.xml 正文顺序定位图片：image28=查询页、image29=导出菜单、**image30=系统导出数据样例**。
- 从 image30 读出 10 个逻辑列：样品编号/样品名称/抽样日期/检验依据/检验项目/单位/技术要求/检验结果/单项评价/任务编号；旧表把它们放在 A/B/C/D/F/H/J/L/N/P，E/G/I/K/M/O 为空隔列。
- 写 SQL（sample_info ⋈ sample_item ⋈ sample_result，status>=80）导出 TSV，再用 openpyxl 生成 `docs/reference/province_export_sample.xlsx`（7 行真实数据）。
- 格式定稿写入 `docs/knowledge/2026-09-12-province-export-format.md`：列映射、取值规则、参考 SQL、5 个待 GLM 落档点（导出阈值/任务筛选/参考项星号/api-spec 落档/EasyExcel 实现）。

## 心得与判断

- **说明书里"如下"往往是截图不是文字**：docx 纯文本解析只能拿到"系统导出的数据形式如下"，列名全在图片里。下次遇到"格式如下/样式如下"直接先解 word/media 按正文顺序看图，不要反复读文字。
- **豆包边界守住**：T-802 虽标在豆包名下，但它是"导出 Excel"——后端 Java 端点归 GLM（AGENTS 2.7 禁止豆包写 backend 代码、禁止改 api-spec）。豆包真正能交付的是**格式定稿 + 字段映射 + 参考样例**，把 GLM 实现成本压到最低。已在文档里把"待 GLM 裁定/落档点"单列，不越权设计契约。
- **隔列版式不照搬**：旧样例 E/G/I/K/M/O 空隔列是老 .xls 合并视觉残留，建议后端直接输出 10 个连续逻辑列；文档里同时记录了旧落位，平台若严格再补空列。

## 踩坑记录

| 现象 | 根因 | 处理 |
|---|---|---|
| `&&` 不是有效分隔符 | 本 shell 是 PowerShell 不是 cmd | 改用 `;` |
| git/mvn 命令找不到 | 注册表/桌面路径已失效 | 用 PortableGit 与 apache-maven 全路径 |
| PowerShell 变量 `$home` 只读报错 | `$HOME` 是内置变量 | 改用 `$resp` |
| TSV 首格带 `\ufeff` | Win PowerShell `Out-File -Encoding utf8` 写 BOM | python 读取用 `utf-8-sig` |
| python 无 openpyxl/pymysql | workbuddy python 干净环境 | openpyxl pip 装；pymysql 不装，改 mysql.exe -B -N 导 TSV |

## 进度

- 本轮前：业务主干 7/9 阶段（T-702/T-801/T-802 未做），总约 84%。
- 本轮后：T-802 **豆包侧格式定稿 + 参考样例**完成（后端端点仍归 GLM，不计阶段完成度）；总进度维持约 **84%**（B 级准备工作，不改变七阶段完成数）。
- 项目已可本地运行：前端 5173、后端 8080，供用户测试。

## 可复用结论

- docx 抠图流程（zipfile → document.xml 正文顺序 → rels 映射 media）可复用到任何"说明书截图即需求"的场景。
- T-802 字段映射 SQL 直接可作为 GLM Mapper 的查询蓝本。
