# SKILL: vue3-crud-page —— Vue3 + Element Plus 标准 CRUD 页面

> 来源任务：T-201 监抽任务前端（豆包代 GLM 产出，Copilot 终审通过）｜ 提炼：Copilot ｜ 2026-09-11
> 状态：✅ lint 0 问题 + vue-tsc 通过，契约与后端核对一致

## 触发场景

任何业务模块的「列表页 + 查询栏 + 新建/编辑对话框 + 删除」前端开发。

## 前置

- 后端契约已在 `docs/api/api-spec.md` 对应域定稿（路径/字段/权限标识）
- 骨架已含：`utils/request.ts`（统一解包 R，401 跳登录）、`types/api.ts`（PageResult）、`stores/auth.ts`（hasPermission）、`directives/permission.ts`

## 权威示例文件

| 层 | 文件 |
|---|---|
| API 封装 | `frontend/src/api/task.ts`（接口函数 + 字典常量 + TS 接口，三合一） |
| 页面 | `frontend/src/views/task/index.vue`（查询栏/表格/分页/对话框/删除确认全模式） |
| 路由 | `frontend/src/router/index.ts`（meta.title，守卫自动鉴权） |

## 步骤

1. **API 封装**：`api/<模块>.ts`——导出 TS interface（与后端 Entity camelCase 对齐）、字典常量数组（`as const`）、五个函数（page/detail/create/update/del），全部走 `@/utils/request` 的 get/post/put/del，**禁止硬编码 baseURL**。
2. **页面**：`<script setup lang="ts">` + **TS strict 禁 any**：
   - 查询表单 ref + 列表 loading/tableData/total + 分页 `pageNum/pageSize`
   - 新建/编辑共用 `<el-dialog>` + `<el-form :rules>`，提交前 `formRef.validate()`
   - 按钮挂 `v-permission="'域:动作'"`（显隐用，安全后端兜底）
   - 删除 `ElMessageBox.confirm` 后调 API，成功 `ElMessage.success` + 刷新
   - 字典渲染用常量数组转 `<el-option>`，状态列用 `<el-tag :type>`
3. **路由登记**：静态路由加一条（动态路由改造见 HANDOFF，GLM 排期），meta.title 与菜单名一致。
4. **菜单/权限**：在 `db/seed/01_rbac_seed.sql` 对应菜单下补按钮权限行（后端 seed 范畴，提 TODO 给 Copilot/豆包）。

## 质量门禁（交付前必跑）

```bash
npm run lint      # ESLint 9 扁平配置，0 error 才准交；排版问题 npm run lint:fix
npm run build     # = vue-tsc --noEmit && vite build
```

## 踩坑

- **日期范围查询**：`<el-date-picker type="daterange" value-format="yyyy-MM-dd">` 出参是数组，拆 start/end 两个 query 参数，契约里分开定义。
- 路由守卫对已登录未加载 me 的导航会先 `await fetchMe()`——权限数据必在渲染前就绪，v-permission 不会误删按钮；**不要**在页面 onMounted 里再拉 me。
- 401 由 axios 拦截器统一处理（清 token 跳 /login?redirect=），页面层不 catch 401。
- Token 存 localStorage 键 `lims_access_token`（AGENTS 决策），禁止入 URL。
- 生产代码禁 `console.log`（lint 会拦）。
