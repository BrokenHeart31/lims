# 2026-09-13 23:30 豆包 — 顶部栏功能补全 + P1~P5 修复

## 1. 本轮目标

用户反馈：个人资料/修改密码/操作日志/帮助中心未完成；面包屑不随路由更新、点哪都回工作台；要求全量交互测试并真正修掉剩余问题。

## 2. 本轮修复清单（已编译验证 + 浏览器实测）

### 后端（新增自服务改密接口）
| 文件 | 改动 |
|---|---|
| `dto/ChangePasswordDTO.java` | 新建：oldPassword + newPassword（@NotBlank + @Size 6~32） |
| `service/AuthService.java` | 接口加 `changePassword(dto)` |
| `service/impl/AuthServiceImpl.java` | 实现：SecurityUtils 取当前用户 → BCrypt 校验旧密码 → 新旧不同 → encode 新密码 → updateById |
| `controller/AuthController.java` | 加 `POST /api/auth/change-password`（登录即可，无需 sys:user:edit） |

### 前端（MainLayout 外壳）
| 文件 | 改动 |
|---|---|
| `api/auth.ts` | 加 `changePasswordApi()` |
| `layouts/MainLayout.vue` | ①面包屑重写（修 B6：不再重复分组名、LIMS 可点回 dashboard）；②用户菜单三个 ElMessage 占位改为真实对话框；③帮助按钮改为帮助中心对话框；④新增个人资料/改密/操作日志/帮助四个 el-dialog；⑤改密表单含校验+调 API+成功后登出重登 |
| `components/common/PageHeader.vue` | `.page-header__title` 加 `flex-shrink:0; white-space:nowrap`（修 P1 竖排） |
| `views/query/testing.vue` | 查询/重置按钮从表单内移到 DataFilter `#actions` 插槽（修 P4） |
| `views/query/history.vue` | 同上 |
| `views/query/library.vue` | 同上 |
| `public/favicon.svg` | 新建 SVG favicon（修 P5） |
| `index.html` | link rel icon 改为 favicon.svg |

## 3. 验证结果

- 后端 `mvn test`：**107/107 通过，BUILD SUCCESS**
- 前端 `vue-tsc --noEmit`：0 错误
- 前端 `npm run lint`：0 错误 0 警告（--fix 后）
- 改密接口：旧密码错误返回 400"旧密码不正确"；新密码 <6 位返回校验错误
- 浏览器实测：
  - 面包屑在 /dashboard 显示"LIMS / 工作台"（不再重复）；在 /sample 显示"LIMS / 样品登记"
  - PageHeader 标题不再竖排（样品登记/项目标准库/结果录入均单行）
  - 个人资料对话框正确显示 nj001/系统管理员/综合管理部/R100
  - 修改密码对话框三个输入框 + 校验
  - 操作日志对话框诚实展示"待后端接入"
  - 帮助中心对话框含快捷键/业务流程/FAQ
  - 查询页按钮已右对齐
  - 刷新页面 console 0 错误（favicon 404 消除）

## 4. 仍待 GLM/后端的项（豆包不动）

1. **操作日志审计**：需新建 `sys_operation_log` 表 + AOP 切面，当前前端已留好空态对话框。
2. **通知中心假数据**：MainLayout 中 notifications 仍为静态 4 条，"查看全部"未接后端。
3. **Git 恢复**：`.git` 对象库仍损坏，临时副本在 main 分支待推 agent/glm。
4. **表格列宽**：任务来源/检验类别等列在窄屏仍截断（P3，建议后续逐列加 show-overflow-tooltip）。
5. **T-917-6~10**：三档分辨率（1366×768 等）与 20 项 Checklist 未做。

## 5. 进度

项目总进度：**95% → 96%**（本轮补全顶部栏功能 + 修 P1/P4/P5，约 +1%）。
