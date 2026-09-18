# SSE 流式卡死、Vue 响应式静默失效、ngram 检索路由 —— 三个已实测定位的坑

> 2026-09-18 GLM 实测自裁，落档留痕。
> 触发场景：用户反馈「问 GB 2762 铅的限量，AI 助手一直显示正在生成，关掉再打开才看到回答」。
> 排查后确认这是**三个独立缺陷叠加**，每个都足以单独造成「答非所问」或「界面卡死」。
> 本文只写「现象 → 根因 → 修法 → 判据」，全部附实测证据，可直接迁移到同类项目。

---

## 坑 1：`SseEmitter` 完成时的 ASYNC 派发会让 Spring Security 抛 `AccessDeniedException`

### 现象

- 服务端**确实生成了回答并落库**（`ai_message` 有记录），但前端**永远收不到 `done` 帧**，界面卡在「正在生成」。
- 后端日志出现：

```
ERROR [nio-8080-exec-2] o.a.c.c.C.[.[.[.[dispatcherServlet] : Servlet.service() for servlet [dispatcherServlet] threw exception
org.springframework.security.access.AccessDeniedException: Access Denied
    at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:98)
...
ERROR ... : Unable to handle the Spring Security Exception because the response is already committed.
```

- `curl -N` 原始观测：**帧收齐了（含 `done`），但 `curl` 退出码 = 18**（`CURLE_PARTIAL_FILE`，传输被异常掐断）——这是最容易误判的地方：**看起来「服务端发全了」，实际连接被 RST**。

### 根因

`SseEmitter.complete()` 会触发**容器内部的 ASYNC 派发**，而 ASYNC 派发会**重跑整条过滤器链 + HandlerInterceptor 链**。
此时：

1. `JwtAuthenticationFilter extends OncePerRequestFilter`，其 `shouldNotFilterAsyncDispatch()` 默认返回 `true`
   ⇒ **ASYNC 派发时不执行认证** ⇒ `SecurityContext` 变为匿名；
2. `authorizeHttpRequests(...).anyRequest().authenticated()` ⇒ `AuthorizationFilter` 判定匿名未授权 ⇒ 抛 `AccessDeniedException`；
3. 但 `text/event-stream` 响应**早已提交**，异常无法转成错误响应 ⇒ Tomcat 异常关闭连接。

### 修法（两层，缺一不可）

```java
// SecurityConfig：放行 ASYNC / ERROR 派发
.authorizeHttpRequests(auth -> auth
        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
        .requestMatchers(PUBLIC_PATHS).permitAll()
        .anyRequest().authenticated())

// JwtAuthenticationFilter：ASYNC 派发也认证（否则审计操作人会写成 anonymousUser）
@Override
protected boolean shouldNotFilterAsyncDispatch() {
    return false;
}
```

**安全性论证**：ASYNC 派发是容器对「**已通过 REQUEST 派发鉴权**」的同一请求的续跑，客户端**无法伪造 `DispatcherType`**，
故放行不会让任何接口对匿名用户开放；REQUEST 派发仍是全量鉴权。

### 连带缺陷（一并修）

ASYNC 派发重跑 `preHandle` 会**覆盖**审计上下文，导致 `/ai/chat/stream` 这类流式接口在 `sys_operation_log` 里被记成
`operator=anonymousUser`、`duration_ms=0`（真实耗时 1.6s 丢失）。修法：`preHandle` 幂等——

```java
if (request.getAttribute(ATTR_CONTEXT) != null) {
    return true;   // ASYNC 派发复用首次（REQUEST 派发）建立的上下文
}
```

**验证判据**：修后 `sys_operation_log` 中该接口 `operator=nj001`、`duration_ms=5570`（真实耗时）。

---

## 坑 2：改「push 进 `ref([])` 的裸对象」在 Vue 3 里**不会触发视图更新**

这是本次「卡在正在生成、重开面板才看到回答」的**真正根因**（比坑 1 更直接）。

### 现象

- `assistant.streaming = false` 已经执行了（store 的 `finally` 也兜底了），但界面**永远停在「正在生成」**；
- **关闭悬浮窗再打开，答案就出现了**（组件重新挂载、重新读一遍数据）。

### 根因（已用 Node 实验证实，非推断）

```js
const messages = ref([])
const assistant = { content: '', streaming: true }   // 裸对象
effect(() => { rendered = messages.value.map(m => `${m.content}|${m.streaming}`).join(',') })

messages.value.push(assistant)
console.log(rendered, renderCount)        // ""|true   render# 2

assistant.content += '毒死蜱'
assistant.streaming = false
console.log(rendered, renderCount)        // ""|true   render# 2   ← 视图完全没更新！

const proxy = messages.value[messages.value.length - 1]
proxy.content += ' [经代理追加]'
console.log(rendered, renderCount)        // 毒死蜱 [经代理追加]|false  render# 3  ← 生效
```

`ref([]).value.push(obj)` 把**裸对象原样存进数组**，只有**从数组读出**时才会被包成响应式代理。
继续改那个裸对象 → 属性变更绕过了代理的 `set` trap → 依赖它的 render effect **不会重跑**。
（注意：`messages.value.length` 与 `push` 本身是走代理的，所以「消息出现」这一步是正常的——
于是表现为「消息在了、内容是空的、状态是生成中」，极具迷惑性。）

### 修法

```ts
messages.value.push({ /* ... */ })
// 取回数组里的响应式代理再改；流式 token / citations / streaming 才会实时渲染
const assistant = messages.value[messages.value.length - 1]
```

### 判据与排查口径

- **实验判据**：用 `@vue/reactivity` 的 `effect` 计数（`render#` 是否递增）比读代码可靠；
- **代码气味**：凡是 `xxx.value.push(literal)` 之后又对该 literal 变量做字段赋值的写法，都是可疑点；
- **组件生命周期会掩盖问题**：`v-if` 挂载的组件在重新挂载时会重新求值，于是「关掉再打开就正常」——
  这是**响应式失效**的典型信号，不要误判成「请求失败」或「后端没返回」。

---

## 坑 3：BOOLEAN 模式下「噪声词 + OR 兜底」会制造海量假命中，把正确结果挤出局

### 现象

问「GB 2763-2021 里毒死蜱的限量是怎么规定的？」→ 返回「标准条款未覆盖」，引用的是
`4.225.5 / 4.324.5 检测方法：谷物按照 GB23200.9…` 这类完全无关的块；
而正确的 `4.121 毒死蜱（chlorpyrifos）` **根本没被召回**。

### 根因（MySQL 探针实测）

| 查询串 | 模式 | 命中 |
|---|---|---|
| `+GB +27632021 +里毒死蜱的限量是怎么规定的` | BOOLEAN（AND） | 0 |
| `GB 27632021 里毒死蜱的限量是怎么规定的` | BOOLEAN（OR 兜底） | **968** |
| `毒死蜱` | BOOLEAN | 8 |
| 整句 | 自然语言模式 | 844 |

三点结论：

1. **BOOLEAN 模式下，不含空格的多字中文词被当「短语」（要求词序邻接）** ⇒ 整句必然 0 命中；
2. 整句里混入的「GB」「2763-2021」是**噪声词**：标准号前缀几乎出现在每个块（每页页眉都有「GB 2763—2021」），
   一旦参与 OR，就用**968 条无关命中**淹没结果，并且**阻止流程进入真正管用的自然语言层**；
3. 正确块（`4.121`）在噪声串下 `score = 0` —— **压根没进候选集**，所以再怎么排序也救不回来。

### 修法（检索路由：整句走自然语言，短词走布尔）

```java
List<String> keywords = usableTerms(query).stream().filter(t -> !isNoiseTerm(t)).toList();
// 只有「用户已分词」（≥2 个有意义短词、且每个都短）才走 BOOLEAN（AND → OR）
if (keywords.size() >= 2 && keywords.stream().allMatch(t -> t.length() <= MAX_KEYWORD_LEN)) { ... }
// 整句一律交给自然语言模式（按 ngram 拆句 + tf-idf 排序，常见 bigram 权重自然被压低）
```

噪声词判据：标准号前缀（`GB / GBT / GBZ / SN / NY …`）+ 纯数字串（`2763-2021`、`0.05`）。

### 附带改进：排序加「标题相关性优先」

OCR 正文里有大量「检测方法清单」式超长块，同一块提到几十种农药名（如「SN/T2324 进出口食品中抑草磷、毒死蜱、
甲基毒死蜱等 33 种有机磷农药残留量的检测方法」），单看正文相关度会盖过真正的条款块。
**条款标题才是「这条讲什么」的权威信号**，故对 `clause_title` 建 ngram 全文索引并把它作为第一排序键：

```sql
ORDER BY (MATCH(c.clause_title) AGAINST(#{query}) > 0) DESC, score DESC, c.id ASC
```

**实测收益**（GB 2763-2021，4660 块）：5 个抽样项目的条款全部升到第 1 位
（毒死蜱→4.121、阿维菌素→4.10、腐霉利→4.193、吡虫啉→4.44、多菌灵→4.27）。

---

## 坑 4（同源）：OCR 国标的「条款号/名称分列两行」与「数字被空格拆散」

### 现象与根因

扫描件里条款版式是**两行**：

```
4. 121
毒死蜱（chlorpyrifos）
```

单行正则永远拼不出「编号 + 名称」。旧实现的连锁后果：

1. 编号行 `4. 121` 被当成「无标题伪边界」丢弃 → **编号丢失**；
2. 下一行 `毒死蜱（chloroprifos）` 变成普通正文 → **名称不再出现在任何块标题里**；
3. 于是 4.121 的正文被挂在**上一张表的编号**（如「表120」）下 —— 引用卡片显示错误条款号。

同时 OCR 会在小数点前后插空格（`4. 121`、`0. 05`、`4. 121. 4`），需要还原。

### 修法

1. **预处理合并两行式标题**：编号行 + 下一行「像标题」的行 → 合成 `"4.121 毒死蜱（chlorpyrifos）"`；
   编号行必须**含小数点且首位非 0**（否则 `0.01`、`0.05` 这类限量值会被误当条款号）；
2. **`(\d)\s*\.\s*(\d)` 反复折叠**，还原被拆散的数字（仅用于边界识别，不改动正文）；
3. **剔除纯数字标题的伪边界**（实测旧索引 1269 块里 **824 块（65%）** 的标题是 `15`、`470` 这类噪声）；
4. **带标题的边界一律切分**（不受「最小块长」限制），避免新条款被错误归属到上一段。

**实测收益**：重建后 4660 块、纯数字标题 **0** 条、`4.121 → 毒死蜱（chlorpyrifos）` 等条款号与名称正确对应。

---

## 一句话总结（可迁移）

| 症状 | 优先怀疑 | 判据 |
|---|---|---|
| SSE 界面永久「生成中」 | ① 后端 ASYNC 派发鉴权异常掐断连接；② 前端把「改裸对象」当响应式更新 | `curl -N` 看**退出码**（18 = 被掐断）；`sys_operation_log` 的 operator/耗时 |
| 重开组件才显示数据 | Vue 响应式代理失效（改的是裸对象） | 用 `effect` 计 `render#` 是否递增 |
| 检索「答非所问」且引用不相关条款 | BOOLEAN 短语语义 + 噪声词 OR 淹没 | 直接对 MySQL 跑同一查询串的 `COUNT(*)`，看候选集里有没有正确块 |
| 引用卡片条款号错位 | OCR 版式（两行式标题、数字被空格拆散） | 拿原文行核对「编号 ↔ 名称」是否分列两行 |
