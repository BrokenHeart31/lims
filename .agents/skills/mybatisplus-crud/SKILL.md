# SKILL: mybatisplus-crud —— MyBatis-Plus 标准 CRUD 五件套

> 来源任务：T-201 监抽任务后端 ｜ 提炼：Copilot ｜ 2026-09-11
> 状态：✅ 已在本仓库验证（豆包实测 CRUD 四件套 + 审计自动填充生效）

## 触发场景

任何新业务表的「分页查询/详情/新建/更新/删除」后端开发。

## 前置

- 建表 SQL 符合 AGENTS 6.1（BIGINT 自增 id / snake_case / 审计四字段 / deleted / 索引），放 `db/init/`，编号递增
- 工程骨架含 `MybatisPlusConfig`（分页插件，上限 500）、`AuditMetaObjectHandler`（审计填充）、`common/R`、`common/PageResult`、`common/exception/BizException`

## 五件套步骤（顺序固定，AGENTS 1「改表必同步」）

1. **Entity** 继承 `BaseEntity`（审计+逻辑删除自动获得）：`@TableName("表名")`，字段 camelCase，MP 默认驼峰映射；日期 `LocalDate` + `@JsonFormat(pattern="yyyy-MM-dd")`。→ 示例 `entity/SuperviseTask.java`
2. **Mapper** 接口 `extends BaseMapper<T>`，**禁止字符串拼 SQL**，复杂条件只用 LambdaQueryWrapper。→ 示例 `mapper/SuperviseTaskMapper.java`
3. **DTO**：保存请求对象加 JSR-303；可选枚举字段用 `@Pattern(regexp="^(甲|乙|丙)?$", ...)` 允许空串。→ 示例 `dto/SuperviseTaskSaveDTO.java`
4. **Service/Impl**：`extends ServiceImpl<Mapper, Entity>`；分页用 `Page<>` 出参；唯一性冲突先查后插，冲突抛 `BizException(400, ...)`；删除走 `removeById`（MP 自动逻辑删）。→ 示例 `service/impl/SuperviseTaskServiceImpl.java`
5. **Controller**：`@Validated @RestController`，统一返回 `R<T>`；分页参数 `@Min(1)`/`@Max(500)`；`PageResult.of(page)` 转 records/total/current/size；每个方法挂 `@PreAuthorize`。→ 示例 `controller/TaskController.java`

## 审计字段使用规约

- 业务代码**绝不手工 set** createdBy/createdAt/updatedBy/updatedAt——`AuditMetaObjectHandler` 从 SecurityUtils 取当前登录人自动填充（未登录场景落 "system"）。
- `deleted` 有 `@TableLogic`：查询自动追加 `deleted=0`，删除即 UPDATE；Entity 上 `select=false` 不出网。

## 契约同步

- 接口先登记 `docs/api/api-spec.md`（路径/方法/请求/响应/权限标识），前端按契约对接；分页出参固定 `{records,total,current,size}`（上限 500）。
- JSON 一律 camelCase（终审决策），Entity 直出即满足，无需 VO 的场景可直接返 Entity（无敏感字段时）。

## 踩坑

- `@Pattern` 校验可选字段必须允许空串（`^(...)?$`），否则前端清空下拉提交 400。
- 逻辑删除后唯一键冲突：唯一索引含 deleted 的数据会挡新插入——业务上先查「未删除」重复，物理唯一键设计需评估（本仓库 task_no 唯一，删除后同号重建需在 Service 层处理）。
- 分页 pageSize 上限 500（MybatisPlusConfig），前端传更大值会被 400。
