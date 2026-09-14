# 零 AOP 依赖的操作日志实现（HandlerInterceptor 方案）

> 日期：2026-09-14 ｜ 作者：GLM ｜ 关联：T-918
> 适用场景：Spring Boot 3 项目需要「集中式操作审计」，但**不能或不引入 AOP 依赖**。

## 1. 为什么不是 AOP（决策前提）

「操作日志用 AOP 切面」是行业标准答案。但本轮落地时遇到硬约束：

- 本机离线 Maven 仓库**无 `spring-boot-starter-aop`**，也无 `aspectjweaver`（`org.aspectj` 目录不存在）；
- 当时网络不可用，无法下载依赖；
- 为日志功能引入新依赖需要走 `DECISIONS.md` 评审，而收益并不匹配。

**关键判断**：审计的本质需求是「集中记录 + 零业务侵入」，
而不是「必须用切面」。
`spring-webmvc` 自带的 `HandlerInterceptor`（已在依赖内）同样满足这两条。

| 维度 | AOP 切面 | HandlerInterceptor |
|---|---|---|
| 新增依赖 | `spring-boot-starter-aop` + `aspectjweaver` | **零** |
| 记录粒度 | Service 方法（可拿到入参对象） | HTTP 接口（方法 + 路径） |
| 业务侵入 | 零（仅加注解） | 零（仅注册拦截器） |
| 可拿到请求体 | 可以（但**不应该**） | 可以（但**不应该**） |
| 事务状态 | 可感知（`@Around` 内） | 不感知（`afterCompletion` 在事务之外） |

**结论**：当「接口 ↔ 业务动作」近乎一一对应时（本系统即如此），两者的信息量等价。
粒度差异不构成信息损失，而「零依赖 + 零侵入」是实打实的收益。

## 2. 实现骨架（三个要点）

### 要点 1：上下文在 `preHandle` 取，落库在 `afterCompletion` 做

```java
@Override
public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!WRITE_METHODS.contains(request.getMethod())) return true;   // ① 只记写请求
    Map<String, Object> ctx = new LinkedHashMap<>();
    ctx.put("uri", stripContextPath(request));
    ctx.put("method", request.getMethod());
    ctx.put("start", System.currentTimeMillis());
    ctx.put("ip", resolveIp(request));
    SecurityUtils.getLoginUser().ifPresent(u -> {                    // ② 此处安全上下文必定有效
        ctx.put("operator", u.getUsername());
        ctx.put("operatorName", u.getNickname());
    });
    request.setAttribute(ATTR_CONTEXT, ctx);
    return true;
}

@Override
public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
    // ③ 用 preHandle 存下的上下文落库；整个落库包在 try/catch 里
}
```

- **为什么上下文在 `preHandle` 取**：`afterCompletion` 阶段安全上下文可能已被
  `SecurityContextPersistenceFilter` 清理，此时再取会拿到空用户（落成 `system`）。
  在 `preHandle` 取值存进 request attribute 是唯一稳妥的做法。
- **`operator_name` 从 `LoginUser.getNickname()` 取**，不要多查一次 DB。

### 要点 2：审计写入失败绝不能影响业务响应

```java
try {
    operationLogMapper.insert(entity);
} catch (Exception e) {
    log.warn("[操作日志] 写入失败（不影响本次业务结果）：{}", e.getMessage());
}
```

审计是**旁路**。让日志表的一次写入失败把一次成功的业务操作变成用户眼里的「失败」，
是把基础设施的可靠性问题转嫁给业务——方向完全错误。

### 要点 3：绝不记录请求体（硬约束）

请求体可能包含 **密码**（登录 / 改密 / 重置密码）。本实现**只记录
「方法 + 路径 + 结果码 + 耗时 + 操作人 + IP」**，从设计上杜绝凭据落库。

> 这条要写进代码注释与契约，因为「日志看不到参数，加个 body 吧」是极自然的后续需求——
> 而那会立刻把一个审计功能变成密码泄露渠道。

## 3. 派生字段的设计（模块 / 动作）

日志表的 `module` 与 `summary` 都是**派生标签**，不是业务语义：

```java
/** 有序前缀表：先匹配到的胜出 */
private static final Map<String, String> MODULE_PREFIXES = new LinkedHashMap<>();
static {
    MODULE_PREFIXES.put("/sys/user", "用户管理");
    MODULE_PREFIXES.put("/base/tester-method", "方法资质");   // 必须排在 /base 之前
    MODULE_PREFIXES.put("/report/audit", "报告审核");         // 必须排在 /report 之前
    MODULE_PREFIXES.put("/sample", "样品登记");
    // ...
}
```

**两个必须遵守的约束**：

1. **顺序敏感**——`LinkedHashMap` 保证插入序，更长的前缀必须排在更短的前面。
   否则 `/report/generate` 会被 `/report` 吞掉，日志显示一个「看起来完全合理」的错误模块名。
   这类错误在界面上极难发现，**必须用单测固化顺序语义**。
2. **匹配不到就归「其他」**，不硬造模块名。硬造会把「路径没登记」这个缺陷伪装成正常数据。

动作词同理，由路径关键词派生（`/import`→导入、`/approve`→审核通过……），
匹配不到时退回 HTTP 方法的通用说法（DELETE→删除 / PUT→修改 / 其余→新增）。

## 4. 分级数据范围（可复用写法）

需求：
- 每个登录用户都应能查**自己**的操作记录（个人可追溯性，ALCOA+ 要求）；
- 「跨用户查看」才是特权。

**错误设计**：接口挂 `@PreAuthorize("hasAuthority('log:view')")` → 普通检验员 403，
查不到自己的记录。**权限注解解决「能不能调接口」，解决不了「能看哪些行」。**

**正确设计**：接口只要求登录，范围在服务层收口：

```java
boolean canViewAll = hasPermission("log:view");
if (!canViewAll) {
    wrapper.eq(SysOperationLog::getOperator, selfNo);        // 强制覆盖，参数无法绕过
} else if (StringUtils.hasText(query.getOperator())) {
    wrapper.eq(SysOperationLog::getOperator, query.getOperator().trim());
}
```

配合「DTO 里的 operator 字段**无请求绑定**」（或即便绑定了也被上面的分支覆盖），
客户端伪造参数无效。**这是本项目第二次使用该写法**（首次为 T-603 `MyTaskQueryDTO.testerScope`），
建议作为项目级惯例沉淀。

## 5. 验收清单

| 验证项 | 做法 | 期望 |
|---|---|---|
| 写请求被记录 | 调任意 POST → 查列表 | 出现对应行，`result=1` |
| **失败请求也被记录** | 用无权限账号调写接口 | `result=0` + `statusCode=403` |
| GET 不记录 | 连续翻页查询 → 查列表 | 列表条数不变 |
| 无 `log:view` 只看自己 | 普通账号查列表 | 所有行 `operator` = 本人工号 |
| **伪造 operator 无效** | 普通账号传 `?operator=<他人>` | 结果与不传一致 |
| 有 `log:view` 可跨用户 | R100 传 `?operator=<他人>` | 只返回该人工号的行 |
| 请求体不入库 | 调用改密接口 | 表中仅方法/路径，无任何密码字段 |
| 写入失败不阻断业务 | 造表不存在 / 断库 | 业务接口仍返回成功 |

## 6. 反向教训（留给后人）

- **不要为了「用 AOP 才是标准做法」而卡住**。先问需求本质（集中 + 零侵入），再选手段。
  本次若坚持 AOP，会因为无法下载 `aspectjweaver` 而整块需求搁置。
- **不要因为「表单里没这个参数」就以为安全**。数据范围必须在服务端强制，
  前端隐藏按钮只是体验层。
- **不要把审计当业务**。审计写入失败记 WARN 即可，业务结果不受影响。
