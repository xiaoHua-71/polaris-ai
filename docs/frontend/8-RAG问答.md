# 8 · RAG 知识库问答对接（M6）

> 执行对象：前端仓库 `D:\Project\AIProject\polaris-ai\dev\polaris-ai-front`
> 契约版本：M6，2026-11-25。后端已实现并验证（本地嵌入 + DeepSeek 生成）。

## 8.1 目标

新增「知识库问答」页面：调用 `POST /api/rag/chat`，后端先在本地知识库中检索相关片段，再交给 DeepSeek 生成回答；前端展示**回答正文 + 检索来源**。

**非目标**：

- 流式 RAG（当前只有同步接口）；
- 多轮记忆（RAG 接口无记忆，每次请求独立）；
- 文档上传 / 删除 / 知识库管理页面；
- 点击来源跳转原文、行内引用角标；
- 展示推理过程、rerank 中间结果。

**重要**：现有 `/api/chat`、`/chat/stream`、`/chat/extract`、`/chat/tools` 契约不变，老页面不需要改。本文只做新增。

## 8.2 与普通对话的区别

| 项 | `/api/chat` | `/api/rag/chat` |
|---|---|---|
| `conversationId` | 必填 | **不需要，不要发送** |
| 多轮记忆 | 有 | 无，每次独立 |
| 响应 | `{content}` | `{content, sources}` |
| 流式 | 有 `/chat/stream` | 无 |
| 额外耗时 | — | 多一次本地向量检索（通常秒级，无硬性承诺） |
| 失败前置 | 需要 DeepSeek key | 还需要后端本地模型文件存在，否则后端启动失败 |

不要把 RAG 请求塞进现有 `chatStore` 的 `conversationId` 流程；用独立的页面状态或独立 store。

## 8.3 接口

### 请求

`POST /api/rag/chat`

```json
{"message":"RAG 的 topK 默认是多少？"}
```

字段约束：

| 字段 | 必填 | 规则 |
|---|---|---|
| `message` | 是 | 非空白字符串；前端先 `trim()`，空则不发请求；长度 ≤ 2000 |

### 响应（HTTP 200）

```json
{
  "content": "4",
  "sources": [
    {
      "source": "polaris-tools.md",
      "title": "工具调用",
      "chunkIndex": 3,
      "score": 0.6051886133952071
    },
    {
      "source": "polaris-rag.md",
      "title": "RAG 基础链路",
      "chunkIndex": 2,
      "score": 0.5970306844675808
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 说明 |
|---|---|---|
| `content` | string | 模型回答；可能为空字符串，前端按「未返回内容」处理 |
| `sources` | array | 检索命中的资料，按相似度降序；**可能为空数组** |
| `sources[].source` | string | 来源文件名，可能为空字符串 |
| `sources[].title` | string | Markdown 标题，可能为空字符串；为空时用 `source` 兜底 |
| `sources[].chunkIndex` | number \| null | 该文档内的切片序号，可能为 `null` |
| `sources[].score` | number \| null | 相似度分数（余弦，0–1，越大越相似），**不是置信度** |

类型建议：

```ts
export interface RagRequest {
  message: string
}

export interface RagSource {
  source: string
  title: string
  chunkIndex: number | null
  score: number | null
}

export interface RagResponse {
  content: string
  sources: RagSource[]
}
```

### 错误

| 场景 | HTTP | 前端处理 |
|---|---|---|
| `message` 空 | 400 | 前端已拦截；正常不应出现 |
| `message` 超过 2000 字符 | 400 | 展示通用错误提示 |
| DeepSeek 调用失败 | 500 | 展示通用错误提示，保留用户输入 |
| 后端本地模型缺失 | 后端启动失败 | `/api/hello` 也打不通，先让后端检查模型文件，不要改前端 |

4xx / 5xx 响应体仍是 Spring 默认格式，不含业务原因；不要尝试从响应体解析错误详情。

## 8.4 前端改造

1. `src/types/api.ts` 增加 `RagRequest` / `RagSource` / `RagResponse`。
2. `src/api/chat.ts` 或新建 `src/api/rag.ts` 增加：

   ```ts
   export function postRagChat(payload: RagRequest): Promise<RagResponse>
   ```

   要求：同步 `POST /api/rag/chat`，只传 `{ message }`，**不要带 `conversationId`**。

3. 新增 `src/pages/RagChatPage.tsx`：
   - 复用现有对话页的布局、气泡、Markdown 渲染样式；
   - **不要**复用 `conversationId` 逻辑，页面打开时也没有「新会话」；
   - 消息状态只在前端内存中维护（不做历史持久化）；
   - 发送前 `trim()`；发送中禁用输入和按钮，显示「检索并生成中…」；
   - 成功后追加助手消息：正文 + 「来源（N）」区块；
   - 来源渲染：`title || source` 作为主标题，`source` / `chunkIndex` 作为次要信息，`score` 显示为「相似度 xx.x%」并注明不是置信度；
   - `sources` 为空：仍然展示回答，同时提示「未检索到资料」；
   - 清空按钮只清空页面消息，文案用「清空对话」，不要叫「新会话」（后端无状态）；
   - 400 / 500 展示友好错误提示，不直接显示英文错误短语。

4. `src/App.tsx` 增加 `/rag` 路由；`AppShell` 导航增加「知识库问答」。
5. 更新前端仓库 `README.md` 的「当前接口契约 / 当前范围」，增加 RAG。

### 不要改

- `vite.config.ts` 的代理和端口；
- `src/api/client.ts` 的错误包装（除非有 bug）；
- Ant Design 主题和布局；
- 不要修改后端仓库；
- 不要新增 npm 依赖；
- 不要把 RAG 接入 `chatStore` 的 `conversationId` 流程；
- 不要使用 SSE / `fetch` 流式读取。

## 8.5 UI 建议

- 页面副标题写清楚：「回答只基于本地知识库；找不到资料时会说明不知道」；
- 来源区块可折叠（默认展开），条目多时限制高度并滚动；
- 来源条目不要做成外链（后端没有原文地址接口）；
- 空回答 + 空来源时展示空状态，不要显示空白气泡。

## 8.6 验收清单

- [ ] 问「RAG 的 topK 默认是多少？」回答包含 `4`，来源包含 `polaris-rag.md`
- [ ] 问「工具结果太长会怎么处理？」回答提到 2000 字符 / 截断，来源包含 `polaris-tools.md`
- [ ] 问知识库外的问题（如「今天天气怎么样」）不崩溃；回答说明资料不足，`sources` 允许为空
- [ ] 空白输入不发请求
- [ ] 发送中按钮禁用，重复点击不会重复请求
- [ ] 来源列表正常渲染；`score` 标注为「相似度」，不是「置信度」
- [ ] 浏览器 Network 面板确认请求体只有 `message`，没有 `conversationId`
- [ ] 400 / 500 有友好提示，输入内容保留
- [ ] 「清空对话」只清 UI，不发送请求
- [ ] `npm run build` 通过

## 8.7 curl 自测

```bash
printf '{"message":"RAG 的 topK 默认是多少？"}' > /tmp/rag.json
curl -X POST http://localhost:8103/api/rag/chat \
  -H 'Content-Type: application/json' \
  --data-binary @/tmp/rag.json
```

期望：`content` 为 `4`，`sources` 至少包含 `polaris-rag.md`。

## 8.8 已知限制

- RAG 无记忆：同一页面连续问两个问题，它们互不相关；
- 无历史消息接口：刷新页面后消息丢失；
- 无流式：回答一次性返回，长回答等待时间可能更明显；
- `score` 未校准：只适合同一次检索内排序，不要在前端做「低于 xx 分就报错」；
- 来源可能重复（同一文档的多个 chunk），按后端返回原样展示即可。
