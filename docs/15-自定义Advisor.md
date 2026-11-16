# 15-自定义 Advisor

> 版本前提：Spring AI 2.0.2 + Spring Boot 4.1.2。
> 状态：✅ 2026-11-16。

## ① 这个模块治的是哪类病

模型调用默认是黑盒：代码只把用户消息交给 `ChatClient`，中间发生了什么看不到。

- **问题类别**：调用链可观测性（observability）。
- **链路位置**：`ChatClient` 与底层 `ChatModel` 之间，属于 Advisor 链。
- **边界**：只负责记录；不修改请求/响应，不做指标聚合、成本换算、内容脱敏。
- **典型失败模式**：
  - 多轮对话里历史没拼进 prompt；
  - 流式首 token 很慢，分不清是模型慢还是链路慢；
  - token 用量超出预期；
  - 客户端断开后不知道模型调用有没有停止；
  - **请求卡住时，不知道卡在数据库、记忆还是模型**。

最后一条是本模块调整过一次设计的原因，见 ③。

## ② Spring AI 官方怎么做（2.0.2）

- Advisor 是模型调用拦截器。同步实现 `CallAdvisor#adviseCall`，流式实现 `StreamAdvisor#adviseStream`；调下一环用 `CallAdvisorChain#nextCall`、`StreamAdvisorChain#nextStream`。
- 2.0.2 **没有** `aroundCall` / `aroundStream` / `nextAroundStream`，那是 1.0.0-M 系列的旧名字。
- 顺序由 `getOrder()` 决定，**数值越小越外层、越先执行**。`Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER = HIGHEST_PRECEDENCE + 200`；`ToolCallingAdvisor.DEFAULT_ORDER = HIGHEST_PRECEDENCE + 300`。
- 官方 `SimpleLoggerAdvisor` 只在 DEBUG 打印 request / response 的 `toString()`；不记录耗时、首 token、token 用量、chunk 数。
- `ChatClientMessageAggregator#aggregateChatClientResponse`：chunk 原样向下游发，正常完成时用 `MessageAggregator` 回调聚合后的 `ChatResponse`；usage 取各 chunk 最后一次非零值。
- `BaseAdvisor` 的流式 `after` 只在完成时执行，没有逐 chunk 钩子，也没有取消回调；异常会被包装。

## ③ 本项目为什么这么落地

| 决定 | 做法 | 备选 | 理由 |
|---|---|---|---|
| 链路日志放外层 | `TraceAdvisor`，order = `HIGHEST_PRECEDENCE + 150` | 放到记忆 Advisor 内层 | 记忆读库或模型调用卡住时，外层仍能先记录「模型调用开始」；只有开始、没有结束就是卡点信号 |
| prompt 日志放内层 | `PromptTraceAdvisor`，order = `HIGHEST_PRECEDENCE + 250` | 与链路日志合并 | 只有过了记忆层才会打印；不打印就说明卡在记忆层 |
| 数据库阶段单独计时 | `JdbcChatMemoryRepository` 的读/写各记一条 DEBUG | 只靠 Advisor 打点 | 直接区分「DB 慢」和「模型慢」 |
| JDBC 快速失败 | URL 加 `connectTimeout=5000&socketTimeout=30000` | 用驱动默认值 | 实际故障中默认 `connectTimeout=0`，连接会无限挂起 |
| 流式统计 | `ChatClientMessageAggregator` 聚合文本、model、usage | 自己累计 chunk | 复用官方逻辑，已验证 usage 汇总 |
| 日志级别 | 仅 DEBUG，无开关 | INFO / 可配置 | 与 `application.yml` 一致，预览截断避免刷屏 |

**原设计为什么不成立**：最初把唯一的 TraceAdvisor 放在 +250（记忆内层），为了看到注入历史后的 prompt。实际故障中，记忆读 MySQL 的连接挂起，流程停在 `MessageChatMemoryAdvisor#before`，内层 TraceAdvisor 永远不执行，日志和断点同时消失，反而最难排查。因此拆成外层链路日志 + 内层 prompt 日志。

## ④ 数据结构与流程

**没有新增表。** Advisor 顺序：

```
TraceAdvisor (+150)
  └─ MessageChatMemoryAdvisor (+200)
      └─ PromptTraceAdvisor (+250)
          └─ 模型
```

**同步流程**：

1. `TraceAdvisor` 打印「模型调用开始」（此时只有系统提示词和本轮用户消息）；
2. 记忆 Advisor 读 MySQL，`JdbcChatMemoryRepository` 打印「读取记忆」；
3. 记忆注入完成后，`PromptTraceAdvisor` 打印「最终 prompt」；
4. 模型调用；
5. 记忆 Advisor 写回，`JdbcChatMemoryRepository` 打印「写入记忆」；
6. `TraceAdvisor` 打印「模型响应」，耗时是端到端总耗时（含读写库和模型）。

**流式流程**：同上，区别是 `TraceAdvisor` 在 `boundedElastic` 上逐 chunk 记录首 token、块数；正常完成打印「流式响应完成」，取消打印「流式调用取消」。

**日志字段**：

| 字段 | 来源 | 说明 |
|---|---|---|
| `conversationId` | 请求 context 的 `ChatMemory.CONVERSATION_ID` | 缺失时为 `-` |
| `消息数` / `消息摘要` | `Prompt#getInstructions()` | 单条 40 字、整体 200 字截断 |
| `耗时` | `System.nanoTime()` | 毫秒；外层为端到端 |
| `首 token 延迟` / `块数` | 流式 chunk | 第一个非空文本 chunk 计时 |
| `finishReason` | `Generation#getMetadata()` | 流式取最后一个非空值 |
| `model` | `ChatResponseMetadata#getModel()` | 聚合结果 |
| `tokens` | `Usage` 的 prompt/completion/total | 缺失时为 `null` |

**实测日志样例**（2026-11-16，本机 DeepSeek）：

```
模型调用开始，conversationId=split-e2e-1，消息数=2，消息摘要=SYSTEM:你是 Polaris… | USER:用一句话介绍你自己
读取记忆，conversationId=split-e2e-1，条数=4，耗时=1ms
写入记忆，conversationId=split-e2e-1，条数=5，耗时=5ms
最终 prompt，conversationId=split-e2e-1，消息数=6，消息摘要=SYSTEM:你是 Polaris… | USER:我叫 Alice… | ASSISTANT:… | USER:我叫什么？ | ASSISTANT:你叫 Alice。 | USER:用一句话介绍你自己
流式首 token，conversationId=split-e2e-1，首 token 延迟=690ms
写入记忆，conversationId=split-e2e-1，条数=6，耗时=5ms
流式响应完成，conversationId=split-e2e-1，耗时=866ms，块数=17，finishReason=STOP，model=deepseek-flash，tokens=57/15/72
```

> 一轮对话可能出现多条「读取记忆」：`MessageWindowChatMemory#add` 在追加消息前也会读一次现有记忆。

**按日志定位卡点**：

| 现象 | 结论 |
|---|---|
| 只有「模型调用开始」，没有「最终 prompt」 | 卡在记忆层：看有没有「读取记忆」，有则在写库或裁剪，没有则在数据库连接 |
| 有「最终 prompt」，没有「模型响应」 | 卡在模型调用 |
| 有「模型调用开始」，没有任何后续 | 连记忆层都没进，检查链路装配或上游校验 |
| 全部都有 | 链路正常 |

## ⑤ 边界、已知限制、踩过的坑

### 没做什么

- **没有指标**：只有日志，没有 Micrometer / Actuator。
- **没有成本换算**：只记 token 数。
- **没有日志开关**：固定 DEBUG。
- **没有请求追踪 ID**：只有 `conversationId`，同会话并发请求无法区分。
- **没有内容脱敏**：消息摘要和回答预览是原文截断。
- **不覆盖工具循环内部**：`PromptTraceAdvisor` 在工具循环外，M5 后只记录一次逻辑调用。

### 坑

1. **日志 Advisor 放在记忆内层，上游卡住时完全不可见**
   - 现象：请求卡在 MySQL 连接，TraceAdvisor 的日志和断点都不出现，看起来像 Advisor 没生效。
   - 原因：记忆 Advisor（+200）先执行 `before()` 读库，返回后才调用内层（+250）。
   - 规避：链路日志放外层（+150），prompt 日志放内层（+250），并用仓储计时日志区分 DB 阶段。

2. **聚合后的 finishReason 可能被冲掉**
   - 现象：流式完成日志 `finishReason` 为 `-`，实际是 `STOP`。
   - 原因：`MessageAggregator` 用最后一个非 NULL 的 generation metadata 覆盖；后续空元数据块会覆盖成 null。
   - 规避：流式时自己记录最后一个非空 finishReason。

3. **取消时不能依赖聚合回调**
   - 现象：客户端断开后没有完成日志。
   - 原因：聚合回调只在 `doOnComplete` 触发，取消走 `doFinally(CANCEL)`。
   - 规避：完成与取消分开记录，二者互斥。

4. **首 token 不能用块数判断**
   - 现象：第一块可能是空文本或 usage-only 块。
   - 原因：provider 可能先发空内容块。
   - 规避：以第一个非空文本 chunk 为准，用 `AtomicBoolean` 保证只记一次。

### 待验证

| 问题 | 验证方法 | 状态 |
|---|---|---|
| `connectTimeout` 是否按 5 秒生效 | 停掉 MySQL 后发起请求，观察报错时间 | 待验证 |
| 其他 provider 的流式 usage 是否稳定 | 换 provider 跑流式，检查 tokens | 待验证 |
| 工具调用开启后 order 是否仍合适 | M5 实测工具循环 | 待验证 |

---

## 完成检查

- [x] 验收标准逐条实测，结果写进 [05-当前实现与验证](05-当前实现与验证.md)
- [x] `mvn test` 全绿（20 个测试）
- [x] 本文件五个小节都有实质内容
- [x] 「没做什么」和「待验证」已写
- [x] [00-索引](00-索引与阅读顺序.md) 的状态已更新
- [x] 版本 / 配置变化已同步到 [02-技术栈与版本](02-技术栈与版本.md)（本次为配置项变化，见 05 配置表）
