# SKILL: excel-import —— EasyExcel 采样单导入（T-301 执行指引）

> 侦察记录：docs/knowledge/2026-09-11-excel-import-research.md（选型论证）
> 提炼：Copilot ｜ 2026-09-11 ｜ 状态：⬜ 待用（T-301 开工即按本 skill 执行）

## 触发场景

T-301 采样单 Excel 导入；后续任何批量导入/导出（T-802 省平台上报导出复用同依赖）。

## 前置

- 已读 `docs/knowledge/2026-09-11-excel-import-research.md`
- 样品表 DDL 已按 6.1 + 状态机枚举落地（见 docs/knowledge/2026-09-11-sample-statemachine-research.md）
- api-spec 样品域契约已先行登记（导入/登记确认/分页查询）

## 步骤

1. **加依赖**（登记 DECISIONS.md）：`com.alibaba:easyexcel:3.3.4`
2. **导入 DTO**：`dto/SampleImportDTO.java`——`@ExcelProperty("列名")` 逐列绑定（列名以业务说明书采样单为准），JSR-303 注解按需加。
3. **监听器**：`service/excel/SampleImportListener.java` 继承 `AnalysisEventListener<SampleImportDTO>`：
   - `BATCH_COUNT = 1000`，`invoke()` 收集满批即 `saveBatch` 后清空
   - 行号 = `context.readRowHolder().getRowIndex() + 1`；逐行业务校验（必填/查重），失败收集 `(行号, 原因)` 不中断
   - `doAfterAllAnalysed()` 刷余量
   - 构造器传入所需 Service/Mapper（监听器非 Spring Bean）
4. **结果对象**：`vo/ImportResultVO.java`——`successCount / failCount / List<ErrorRow{rowNum,message}>`。
5. **Service**：`importSamples(MultipartFile)` 内 `EasyExcel.read(in, DTO.class, listener).sheet().doRead()`；落库样品状态 **S10（已登记）**，状态值经 SampleStatus 枚举，禁止魔法数字。
6. **Controller**：`@PostMapping("/sample/import")` + `@PreAuthorize("hasAuthority('sample:import')")`，返回 `R<ImportResultVO>`；`multipart` 大小限制在 application.yml 配（默认 10MB 起步）。
7. **前端**：`views/sample/index.vue` 上传组件（`el-upload` 手动上传 + FormData），导入后展示成功/失败明细对话框（失败清单可复制）。

## 验收（T-301 完成标准）

- 合法文件导入后样品全部 S10，分页可查
- 含错误行的文件：合法行入库、错误行逐条报行号+原因，**不整批回滚**（业务允许修正重导）
- 重复样品编号：查重拦截并计入失败清单
- 单测：小样本 xlsx 断言导入行数与错误定位（AGENTS 4.3）

## 造测试文件并实测（2026-09-15 补充，可复用）

生成导入样例后用 curl 实测一遍再交付，**验证后必须清理**，否则污染用户首次导入：

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"nj001","password":"nj001"}' | python -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
curl -s -X POST http://localhost:8080/api/sample/import -H "Authorization: Bearer $TOKEN" \
  -F "file=@<文件>.xlsx"          # 字段名为 file
```

清理（缺一不可）：

```sql
DELETE FROM sample_import_batch WHERE file_marker LIKE 'SAMPLE-IMPORT-TEST%';  -- 文件标记防重
DELETE FROM sample_info WHERE sample_no LIKE 'TEST-%';
```

三个导入端点：`POST /api/sample/import`、`/api/base/tester-method/import`、`/api/base/lib/import`。

## 踩坑

- **A1 是「文件标记」，同标记禁止重复导入**（落 `sample_import_batch`），重复导直接 400。
  想再导一次改 A1 文本即可。造样例时 A1 用独立标记名。
- **「文件内重复编号」依赖前一行先成功入库**：撞号的第一行若自身校验失败（如样品名称空），
  第二行**不会**报重复而会成功。造错误样例时撞号的第一行必须本身合法，否则错误数会比预期少一条。
- **列按 `@ExcelProperty(index=n)` 绑定，不是按表头名**：生成文件时列顺序必须严格对齐 DTO 的 index 注解，
  表头文字写错不影响读取，但列错位会整列读空。
- **判定类型一致性**（项目标准库导入）：1=限量比较（限量值须为数值、`≤0.5` 也可）、
  2=不得检出/不得使用（限量值必须含该文本）、3=文本（填 `--`）。三者互斥，填反会 400。
- 监听器每文件一实例，**不可**做成 Spring 单例（并发导入互相污染 cachedList）。
- 表头与 `@ExcelProperty` 严格一致；说明书表头含空格/全角括号要在 DTO 上原样写。
- 大文件流式读，不要 `doReadSync()` 全量进内存。
- Excel 日期列用 `LocalDate` 接收，EasyExcel 自动转；文本形态的日期加 `@DateTimeFormat("yyyy-MM-dd")`。
- 导出（T-802）复用同依赖：`EasyExcel.write(response.getOutputStream(), ExportVO.class).sheet().doWrite(list)`，文件名 URLEncoder 编码防中文乱码。
