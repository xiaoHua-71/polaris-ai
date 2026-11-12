# 15-自定义 Advisor

> 版本前提：Spring AI 2.0.2 + Spring Boot 4.1.2。
> 状态：✅ 2026-11-12。

## ① 这个模块治的是哪类病

模型调用默认是黑盒：代码只把用户消息交给 `ChatClient`，中间发生了什么看不到。

- **问题类别**：调用链可观测性（observability）。具体到本项目，是「一次模型调用前后到底发生了什么」。
- **链路位置**：`ChatClient` 与底层 `ChatModel` 之间，属于 Advisor 链的一环。上游是控制器和记忆 Advisor，下游是模型调用。
- **边界**：只负责记录请求、响应、耗时和 token；不修改请求/响应，不做指标聚合，不做成本换算，不负责脱敏策略的设计。
- **典型失败模式**：
  - 多轮对话里历史没拼进 prompt，但接口看起来正常；
  - 流式首 token 很慢，分不清是模型慢还是链路慢；
  - token 用量超出预期，没有数据可查；
  - 客户端断开后，不知道模型调用有没有真正停止。

## ② Spring AI 官方怎么做（2.0.2）

- Advisor 是**模型调用拦截器**。同步实现 `CallAdvisor#adviseCall`，流式实现 `StreamAdvisor#adviseStream`；调下一环用 `CallAdvisorChain#nextCall`、`StreamAdvisorChain#nextStream`。
- 2.0.2 **没有** `aroundCall` / `aroundStream` / `nextAroundStream`，这些是 1.0.0-M 系列的旧名字。实现类 `DefaultAroundAdvisorChain` 保留了 “Around” 类名，但方法已经是 `nextCall` / `nextStream`。
- Advisor 顺序由 `getOrder()` 决定，**数值越小越外层、越先执行**。`Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER = HIGHEST_PRECEDENCE + 200`；`ToolCallingAdvisor.DEFAULT_ORDER = HIGHEST_PRECEDENCE + 300`。
- 官方自带 `SimpleLoggerAdvisor`：DEBUG 级别打印 request / response 的 `toString()`，可用 `order` 排序。它**不记录耗时、首 token 延迟、token 用量、chunk 数**。
- 流式聚合工具 `ChatClientMessageAggregator#aggregateChatClientResponse`：chunk 原样向下游发，正常完成时用 `MessageAggregator` 回调一条聚合后的 `ChatResponse`；usage 取各 chunk 最后一次非零值。
- `BaseAdvisor` 的默认实现是 `before → next → after`，但流式 `after` 只在 finishReason 命中时执行，**没有逐 chunk 钩子**，也没有取消回调；异常还会被包装成 `IllegalStateException`。

## ③ 本项目为什么这么落地

| 决定 | 做法 | 备选 | 理由 |
|---|---|---|---|
| Advisor 类型 | 直接实现 `CallAdvisor` + `StreamAdvisor` | 实现 `BaseAdvisor` 只写 before/after | 需要逐 chunk 统计首 token 延迟和块数，并区分正常完成与取消；`BaseAdvisor` 没有这些钩子 |
| 顺序 | `HIGHEST_PRECEDENCE + 250`（记忆 +50） | 记忆之前（≤ +200）/ 工具循环之内（> +300） | 在记忆 Advisor 之后执行，能看到注入历史后的最终 prompt；又在工具循环之外，M5 前只记录一次逻辑调用 |
| 日志级别 | 仅 DEBUG | INFO / 可配置开关 | `application.yml` 已把 `com.xiaohua.polaris` 开到 debug；预览截断，避免刷屏 |
| 流式统计 | `ChatClientMessageAggregator` 聚合文本、model、usage | 自己累计 chunk | 复用官方聚合逻辑，已验证 usage 汇总行为 |
| 包位置 | `observability/TraceAdvisor.java` | `config` / `chat` | 按功能分包，docs/04 已预留 `observability/` |
| 注册方式 | 默认 Advisor | 每个请求临时加 | 所有对话自动生效，控制器无需感知 |

## ④ 数据结构与流程

**没有新增表、没有新增配置项。** 日志字段如下：

| 字段 | 来源 | 说明 |
|---|---|---|
| `conversationId` | 请求 context 的 `ChatMemory.CONVERSATION_ID` | 缺失时为 `-` |
| `消息数` / `消息摘要` | `Prompt#getInstructions()` | 摘要格式 `SYSTEM:… \| USER:…`，单条 40 字、整体 200 字截断 |
| `耗时` | `System.nanoTime()` | 毫秒；同步为整次调用，流式为累计 |
| `首 token 延迟` | 第一个非空文本 chunk | 流式专用 |
| `块数` | 所有 `ChatClientResponse` 计数 | 流式专用，含空文本尾块 |
| `finishReason` | `Generation#getMetadata()` | 流式取最后一个非空值，不用聚合结果 |
| `model` | `ChatResponseMetadata#getModel()` | 聚合结果 |
| `tokens` | `Usage` 的 prompt/completion/total | 聚合结果；缺失时打印 `null` |

**同步流程**（`TraceAdvisor#adviseCall`）：

1. 记录开始时间，打印请求摘要；
2. `callAdvisorChain.nextCall(request)`；
3. 打印响应摘要，返回原响应。

**流式流程**（`TraceAdvisor#adviseStream`，用 `Flux.defer` 隔离每次订阅状态）：

1. 订阅时打印请求摘要；
2. 每个 chunk：块数 +1；第一个非空文本 chunk 打印首 token 延迟；记录最后一个非空 finishReason；
3. `ChatClientMessageAggregator` 在正常完成时打印聚合响应；
4. 上游错误打印 WARN；`doFinally(CANCEL)` 打印取消；
5. chunk 原样向下游发送，不改变控制器拿到的 `Flux<String>`。

**实测日志样例**（2026-11-12，本机 DeepSeek）：

```
模型请求，conversationId=trace-e2e-20261112-1，消息数=2，消息摘要=SYSTEM:你是 Polaris… | USER:我叫 Alice，请记住
模型响应，conversationId=trace-e2e-20261112-1，耗时=1835ms，finishReason=STOP，model=deepseek-flash，tokens=25/13/38，文本预览=好的，Alice，我记住了…
流式首 token，conversationId=trace-e2e-20261112-1，首 token 延迟=630ms
流式响应完成，conversationId=trace-e2e-20261112-1，耗时=911ms，块数=31，finishReason=STOP，model=deepseek-flash，tokens=58/29/87，文本预览=我是 Polaris…
流式调用取消，conversationId=trace-e2e-20261112-cancel，耗时=1162ms，块数=3
```

> 以上数字是单次本机测得，不是性能基准。

## ⑤ 边界、已知限制、踩过的坑

### 没做什么

- **没有指标**：只有日志，没有 Micrometer / Actuator，无法按维度聚合 QPS、P95、token 总量。
- **没有成本换算**：只记录 token 数，不按模型单价折算金额。
- **没有日志开关**：固定 DEBUG；如果生产环境把包级别调到 INFO，本模块静默。
- **没有请求追踪 ID**：只有 `conversationId`，同会话并发请求无法区分。
- **没有内容脱敏**：消息摘要和回答预览是原文截断，敏感内容会进日志。
- **不覆盖工具循环内部**：order 在工具循环之外，M5 后只记录一次逻辑调用；若要看每轮工具调用，需要调整 order。

### 坑

1. **聚合后的 finishReason 可能被冲掉**
   - 现象：流式完成日志里的 `finishReason` 是 `-`，但实际是 `STOP`。
   - 原因：`MessageAggregator` 用「最后一个非 NULL 的 generation metadata」覆盖；后续 chunk 若带空元数据，会把前面的 finishReason 覆盖成 null。
   - 规避：TraceAdvisor 自己在 `doOnNext` 里记录最后一个非空 finishReason，不复用聚合结果。

2. **取消时不能依赖聚合回调**
   - 现象：客户端断开后，若只在 `ChatClientMessageAggregator` 的回调里打完成日志，则什么都不会记录。
   - 原因：聚合回调只在 `doOnComplete` 触发，取消走的是 `doFinally(CANCEL)`。
   - 规避：完成与取消分开记录，二者互斥。

3. **首 token 不能用块数判断**
   - 现象：第一块可能是空文本或 usage-only 块，按第一块计时会把延迟算小。
   - 原因：provider 可能先发空内容块。
   - 规避：以第一个非空文本 chunk 为准，并用 `AtomicBoolean` 保证只记一次。

### 待验证

| 问题 | 验证方法 | 状态 |
|---|---|---|
| 其他 provider 的流式 usage 是否稳定 | 换 provider 跑流式，检查完成日志 tokens 是否非空 | 待验证 |
| 工具调用开启后 order 是否仍合适 | M5 实测工具循环，确认日志次数符合预期 | 待验证 |
| 高并发下日志开销 | 小规模压测，观察 DEBUG 日志对延迟的影响 | 待验证 |

---

## 完成检查

- [x] 验收标准逐条实测，结果写进 [05-当前实现与验证](05-当前实现与验证.md)
- [x] `mvn test` 全绿（17 个测试）
- [x] 本文件五个小节都有实质内容
- [x] 「没做什么」和「待验证」已写
- [x] [00-索引](00-索引与阅读顺序.md) 的状态已更新
- [x] 版本 / 配置变化已同步到 [02-技术栈与版本](02-技术栈与版本.md)（本次无版本/配置变更）
