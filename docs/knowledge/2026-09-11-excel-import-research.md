# 侦察记录：Excel 导入技术选型（EasyExcel）

> 侦察人：Copilot ｜ 日期：2026-09-11 ｜ 服务任务：T-301 采样单 Excel 导入 / T-802 省平台上报导出
> 来源：RuoYi-Vue-Plus（gitee.com/dromara/RuoYi-Vue-Plus）生态实践、EasyExcel 官方文档、若依社区扩展笔记

## 结论（已定）

**采用阿里巴巴 EasyExcel（3.3.x），禁止直接使用 Apache POI 裸 API。**

| 维度 | EasyExcel 3.3.x | Apache POI 裸用 |
|---|---|---|
| 内存模型 | SAX 流式解析，10 万行约 100MB | usermodel 全量加载，大文件 OOM 风险 |
| 代码量 | 注解绑定 + 监听器回调，极简 | 行/格手工遍历，样板代码多 |
| 生态对齐 | RuoYi-Vue-Plus / 若依系标准方案 | — |
| 写（导出） | 注解表头 + 模板填充，T-802 可复用 | SXSSF 需手工封装 |

采样单单次导入量级（数百至数千行）EasyExcel 完全覆盖；导出侧 T-802 同一依赖复用。

## RuoYi-Vue-Plus 标准模式（本项目套用）

1. **导入 BO（ImportVo）**：字段用 `@ExcelProperty("列名")` 绑定表头；JSR-303 注解加 `groups = {ImportGroup.class}`，与普通表单校验分组隔离，避免同一行多约束只报一条（需 Validator `failFast=false`）。
2. **自定义监听器** 继承 `AnalysisEventListener<T>`：
   - `invoke()` 逐行收集到 `cachedList`，**满 1000 条批量 saveBatch 后清空**（批大小常量 1000）；
   - 行号 = `context.readRowHolder().getRowIndex() + 1`，用于错误回报「第 N 行：原因」；
   - `doAfterAllAnalysed()` 刷剩余缓存；
   - 成功列表 + 错误列表（行号+原因）双收集，返回 `ExcelResult` 风格结果对象，**部分失败不回滚全批**（业务上采样单允许修正后重导）。
3. **Service 注入**：监听器不由 Spring 管理（每文件一个实例），需要的 Mapper/Service 通过构造器传入。
4. **Controller**：`@PostMapping("/import")` 收 `MultipartFile`，`EasyExcel.read(file.getInputStream(), ImportVo.class, listener).sheet().doRead()`，返回成功/失败明细。

## 本项目落地要点（T-301 执行时遵循）

- 依赖：`com.alibaba:easyexcel:3.3.4`（引入时登记 DECISIONS.md）。
- 表头以业务说明书采样单为准；导入 BO 放 `dto/` 包，命名 `SampleImportDTO`。
- 样品落库即 S10（已登记），状态字段 TINYINT + 枚举，见状态机侦察记录。
- 重复判定键（如样品编号）先查重再插入；错误行导出为可下载的反馈清单（可选增强，列入 T-301 验收加分项）。
- 单元测试：用 db/seed 准备的测试数据集 + 小样本 xlsx 断言导入行数与错误行定位。

## 参考链接

- EasyExcel 官方：https://easyexcel.opensource.alibaba.com/
- RuoYi-Vue-Plus ExcelUtil/DefaultExcelListener 模式：项目内已摘录核心签名于本文件，无需翻外网。
