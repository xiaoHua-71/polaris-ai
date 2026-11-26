# RAG 基础链路（M6）

> 版本前提：Spring AI 2.0.2 + Spring Boot 4.1.2 + JDK 21。
> 快照时间：2026-11-24。
> 状态：✅ 2026-11-25 实现并验证（本地检索 Hit@4 10/10；生成链路 DeepSeek 实测 1 题 + 关闭检索对照）。类名 / 默认值来自本地依赖 jar（2026-11-24 确认）。

## ⓪ 先看懂几个词（零基础可读，已懂可跳过）

### RAG 是什么

模型的知识被训练数据冻结，也不知道本项目的私有文档。**RAG（Retrieval-Augmented Generation，检索增强生成）** 是：

1. 把文档切成小块（chunk），转成向量存进向量库；
2. 用户提问时，把问题也转成向量，在向量库里找最相似的若干块；
3. 把这些块拼进 prompt，让模型**基于检索到的内容**回答。

模型负责组织语言，向量库负责找资料。模型本身不会「记住」这些文档。

### Embedding

**Embedding（向量化）** 把一段文本映射成一个固定维度的浮点数向量，语义相近的文本向量距离更近。它需要**嵌入模型**，和对话模型是两回事：

- 对话模型：DeepSeek（当前项目已接入）；
- 嵌入模型：本项目**不调用远程 API**，用本地 ONNX 模型（见 ③）。

DeepSeek 的 OpenAI 兼容接口只提供 chat，不提供 embeddings；这也是「嵌入模型必须单独选」的原因。

### Chunk 与切片

文档太长不能整篇塞进 prompt，否则：

- 超出上下文窗口；
- 检索粒度太粗，命中一整篇却只有一句有用；
- token 成本高。

**切片（chunking）** 把文档切成若干段，每段单独向量化。切片大小、重叠、边界（按段落/标题/标点）都会影响检索质量。

### 向量库与相似度检索

**向量库**保存 `向量 → 文档块` 的映射，并支持「给我和这个向量最相似的 K 条」。本项目用 Spring AI 的 `SimpleVectorStore`：内存实现，支持存盘/加载，适合小规模知识库。

### 检索增强 Advisor

Spring AI 把「检索 → 把结果塞进 prompt」封装成 Advisor。调用处只写 `.advisors(...)`，检索逻辑不在 Controller 里手写。详见 ②。

## ① 这个模块治的是哪类病

RAG 处在「用户问题 → 检索 → 模型回答」的中间层：上游是文档与用户输入，下游是对话模型。它要解决的是：

- **知识时效与私有性**：模型训练数据之后的内容、本项目私有文档，模型不知道；
- **可溯源**：回答要能指回来源，而不是编造；
- **上下文成本**：不能把整库文档塞进 prompt，只能按问题取少量相关片段；
- **可评估**：要能证明「检索到的东西确实被用上了」，而不是只看到回答像模像样。

它的**边界**：

- 不负责文档清洗、OCR、权限过滤（M6 语料可信且格式统一）；
- 不负责让模型「学会」知识（那是微调的事）；
- 不保证回答一定正确，只保证**资料被检索并注入**；正确性要靠评估集验证。

典型失败模式：

1. **检索不到**：切片/嵌入模型不合适，正确片段不在 top-K。
2. **检索到但没用上**：context 塞了，模型仍按自己的知识编；提示词没有约束「只根据资料回答」。
3. **切片断裂**：一句话被切成两半，或标题与正文分离，向量语义不完整。
4. **相似度阈值照搬**：不同嵌入模型的分数分布不同，OpenAI 的阈值不能直接套到 bge。
5. **索引与模型漂移**：换了嵌入模型却没重建索引，向量空间不一致。
6. **成本失控**：top-K 太大、chunk 太大，prompt token 暴涨。
7. **提示词注入**：知识库内容包含恶意指令，被模型当成用户指令执行（M6 语料可信，暂不处理）。

## ② Spring AI 2.0.2 官方怎么做

以下类名与默认值均从本地依赖 jar 确认（2026-11-24）。

### ETL：读文档 → 切块

| 能力 | 接口 / 类 | 所在构件 |
|---|---|---|
| 文档模型 | `org.springframework.ai.document.Document` | `spring-ai-commons` |
| 读取器 | `DocumentReader`、`TextReader`、`JsonReader` | `spring-ai-commons` |
| Markdown 读取器 | `MarkdownDocumentReader` | `spring-ai-markdown-document-reader` |
| PDF / Tika | `PagePdfDocumentReader`、`TikaDocumentReader` | `spring-ai-pdf-document-reader` / `spring-ai-tika-document-reader` |
| 切块 | `TokenTextSplitter`（默认 800 token，`minChunkSizeChars=350`，`minChunkLengthToEmbed=5`，`maxNumChunks=10000`，保留分隔符） | `spring-ai-commons` |
| 后处理 | `DocumentTransformer`、`ContentFormatTransformer` | `spring-ai-commons` |

`Document` 自带 `id`、`text`、`metadata`；metadata 可放 `source`、`title`、`chunkIndex` 等，检索结果和引用展示都靠它。

### Embedding：本地 ONNX

`spring-ai-starter-model-transformers` 提供 `org.springframework.ai.transformers.TransformersEmbeddingModel`：

- 底层：ONNX Runtime（`ai.onnxruntime.OrtSession`）+ DJL HuggingFace Tokenizer；
- 默认模型：`sentence-transformers/all-MiniLM-L6-v2`，**384 维、英文**；
- 默认从 GitHub 拉取 `model.onnx` 与 `tokenizer.json`（不是内置在 jar 里）；
- 配置前缀：`spring.ai.embedding.transformer.*`；
- 模型输出名：`spring.ai.embedding.transformer.onnx.model-output-name`，默认 `last_hidden_state`。

> **重要**：默认模型是英文的，且默认地址是公网 URL。直接用默认配置既不适合中文，也不满足「完全本地、离线可跑」。必须覆盖模型和 tokenizer 地址。

其他本地选项：`spring-ai-starter-model-ollama`（需要本机运行 Ollama 服务，例如 `bge-m3`，1024 维）。本项目机器未安装 Ollama，暂不选。

### VectorStore

`org.springframework.ai.vectorstore.VectorStore` 接口；`SimpleVectorStore` 提供：

- `builder(EmbeddingModel)`；
- `add(List<Document>)`、`similaritySearch(SearchRequest)`、`delete(...)`；
- `save(File)` / `load(File)`：内存索引可落盘。

`SearchRequest` 默认值：`topK=4`，`similarityThreshold=0.0`（接受全部）。

### 检索注入 Advisor

两条官方路线：

1. **`QuestionAnswerAdvisor`**（`spring-ai-vector-store-advisor`）
   - `builder(VectorStore).searchRequest(...).promptTemplate(...).build()`；
   - 自动检索并把结果拼进 prompt；
   - 检索结果放在响应上下文，key 为 `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS`（值为字符串 `qa_retrieved_documents`），可用于日志和评估。
2. **`RetrievalAugmentationAdvisor`**（`spring-ai-rag`）
   - 可组合：`VectorStoreDocumentRetriever`、`ContextualQueryAugmenter`、query transformer、document joiner / post-processor；
   - 适合需要查询改写、多路检索、重排的复杂链路。

### 构件清单（来自 `spring-ai-bom:2.0.2`）

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-transformers</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-vector-store-advisor</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-markdown-document-reader</artifactId>
</dependency>
```

> `spring-ai-rag` 只有在选 `RetrievalAugmentationAdvisor` 时才需要；M6 最小闭环不引入。

## ③ 本项目为什么这么落地

| 决定 | 做法 | 备选 | 理由 |
|---|---|---|---|
| 生成模型 | 沿用 DeepSeek chat | 本地 chat 模型 | 已确认「除对话外全部本地」；M6 聚焦「检索是否有效」，不引入第二个模型运行时。若以后要换本地 chat，另立改动并重做 M1–M5 验证 |
| 嵌入模型 | 本地 ONNX + 中文模型 `BAAI/bge-small-zh-v1.5`（512 维，Xenova ONNX 导出） | 默认 `all-MiniLM-L6-v2`（384 维，英文）；Ollama `bge-m3`（1024 维） | 无 API 费用、进程内运行、无额外服务；中文语料需要中文模型。默认 MiniLM 英文效果不可接受；Ollama 需装服务 |
| 模型文件 | 放项目根目录 `models/bge-small-zh-v1.5/`，`file:` 路径加载，加入 `.gitignore` | 提交进 git；从 GitHub 默认地址拉 | 模型约 91 MB，提交会污染仓库；默认地址是公网，违背离线目标。一次手动下载，运行期不联网 |
| 向量库 | `SimpleVectorStore`，启动时重建 | MySQL（无向量类型）；pgvector / Redis / Milvus | 语料规模小，重建成本低；避免为了 RAG 再引入一个存储服务。重建也避免「模型换了但索引没换」 |
| 文档读取 | `MarkdownDocumentReader` | PDF / Tika；自研解析 | 语料统一用 Markdown，标题结构可转 metadata；PDF/Tika 解析噪声大，M6 不处理 |
| 切片 | `TokenTextSplitter`，`chunk-size=200`（可配） | 按字符数切；按标题切 | 官方实现已处理标点边界与最小块。默认 800 token 对当前小语料太大（每篇只剩 1 块），降到 200 后 3 篇文档切成 12 块，检索才有区分度 |
| 检索注入 | `QuestionAnswerAdvisor` | `RetrievalAugmentationAdvisor`；手工检索 | 最小闭环、配置少；检索结果可从响应上下文取出，用于日志/引用/评估。等需要查询改写或重排再升级 |
| 接口 | 新增独立 `POST /api/rag/chat` 与 `ragChatClient` | 把 RAG 加到现有 `/chat` | 不改变 M1–M5 已验收接口；评估时可以明确「开 RAG / 关 RAG」对照 |
| 记忆 | M6 的 RAG 接口**无多轮记忆** | 复用 `MessageChatMemoryAdvisor` | 先把「检索是否有效」单独验证；记忆 + RAG 的交互（历史问题改写）留给后续 |
| 引用 | 返回检索到的来源列表 | 只返回 content | 「证明检索被用上」需要证据：命中文档、chunk 序号、分数 |

### 模型与语料的约定

- 嵌入模型：`bge-small-zh-v1.5`，512 维，中文/英文混合可用；
- 模型来源：HuggingFace `BAAI/bge-small-zh-v1.5` 仓库中的 ONNX 模型与 `tokenizer.json`，**一次下载**；
- 语料：`src/main/resources/knowledge/*.md`，3–5 篇自建短文，主题与评估问题一一对应；
- 评估集：10 条问题 + 期望来源，写在本文 ⑥。

## ④ 数据结构与流程

### 数据模型

M6 不新增数据库表。向量只存在内存，文档元数据随 `Document.metadata`：

| 字段 | 来源 | 用途 |
|---|---|---|
| `id` | `Document` / 切片生成 | 去重、引用定位 |
| `source` | 文件名（如 `knowledge/rag-faq.md`） | 引用展示、命中评估 |
| `title` | Markdown 标题或文件头 | 引用展示 |
| `chunkIndex` | 切片顺序（若框架未写则自行补充） | 定位同一文档内的片段 |
| `score` | 检索结果自带（若版本支持） | 校准相似度阈值 |

### 启动导入流程

```
启动
  → 读取 classpath:knowledge/*.md
  → MarkdownDocumentReader 转 Document（补 source/title）
  → TokenTextSplitter 切块（补 chunkIndex）
  → VectorStore.add(chunks)
      → TransformersEmbeddingModel（本地 ONNX）逐块向量化
  → 就绪
```

导入失败（模型文件缺失、维度异常）应**启动失败并给出明确错误**，不要静默降级到「没有知识库但接口还能答」。

### 查询流程

```
POST /api/rag/chat {message}
  → 参数校验（非空、长度上限）
  → ragChatClient.prompt(message)
      → QuestionAnswerAdvisor
          → 本地 ONNX 向量化 query
          → SimpleVectorStore 相似度检索 topK
          → 命中片段拼进 prompt
      → DeepSeek chat 生成回答
  → 从响应上下文取 RETRIEVED_DOCUMENTS
  → 返回 {content, sources[]}
```

### 配置项

| 配置 | 默认值 | 作用 |
|---|---|---|
| `spring.ai.model.embedding` | `transformers` | **必须显式设置**，否则 OpenAI starter 也会装配一个 EmbeddingModel（见 ⑤ 坑 1） |
| `spring.ai.embedding.transformer.onnx.model-uri` | `file:./models/bge-small-zh-v1.5/model.onnx` | 本地 ONNX 模型文件 |
| `spring.ai.embedding.transformer.tokenizer.uri` | `file:./models/bge-small-zh-v1.5/tokenizer.json` | 本地 tokenizer |
| `spring.ai.embedding.transformer.onnx.model-output-name` | `last_hidden_state` | ONNX 输出节点名 |
| `spring.ai.embedding.transformer.cache.enabled` | `true` | ONNX 资源缓存 |
| `spring.ai.embedding.transformer.cache.directory` | `.cache/onnx` | 缓存目录 |
| `polaris.rag.top-k` | `4` | 检索条数 |
| `polaris.rag.similarity-threshold` | `0.0`（先接受全部，校准后再调） | 相似度阈值 |
| `polaris.rag.chunk-size` | `200` | 切片 token 上限 |
| `polaris.rag.knowledge-location` | `classpath:knowledge/*.md` | 语料位置 |

模型路径用环境变量覆盖，避免把机器相关路径写死：

```yaml
spring:
  ai:
    model:
      embedding: transformers
    embedding:
      transformer:
        onnx:
          model-uri: ${RAG_MODEL_URI:file:./models/bge-small-zh-v1.5/model.onnx}
        tokenizer:
          uri: ${RAG_TOKENIZER_URI:file:./models/bge-small-zh-v1.5/tokenizer.json}
```

### 评估口径

- **Hit@4**：期望来源文档出现在 top-4 命中结果中的问题占比；
- **回答正确率**：人工判定回答是否被资料支持（0/1），记录原始回答；
- **对照**：同一批问题，关闭检索（普通 `chatClient`）再跑一遍，比较正确率；
- 记录：日期、嵌入模型、chunk 参数、topK、threshold、命中详情。

## ⑤ 边界、已知限制、踩过的坑

### 没做什么

- 不做 rerank、混合检索（关键词 + 向量）、多路召回；
- 不做 query 改写 / 多查询扩展（`RetrievalAugmentationAdvisor` 的 query transformer）；
- 不做持久化向量库（Redis / pgvector / Milvus）；
- 不做增量更新、删除、文档 ACL；
- 不做流式 RAG（SSE + 检索）；
- 不做 PDF / OCR / 图片；
- 不把对话模型换成本地；
- RAG 接口不接多轮记忆。

### 坑

1. **两个 EmbeddingModel bean**
   - 现象：加上 transformers starter 后，`OpenAiEmbeddingAutoConfiguration` 与 `TransformersEmbeddingModelAutoConfiguration` 都满足条件（两者 `matchIfMissing=true`），注入 `EmbeddingModel` 时冲突或选错。
   - 原因：Spring AI 的多模型装配靠 `spring.ai.model.embedding` 选择；不设置时各 provider 默认都启用。
   - 规避：显式设置 `spring.ai.model.embedding: transformers`；用启动日志确认只装配了本地模型。

2. **默认模型从公网下载**
   - 现象：首次启动卡住或失败，日志出现 `raw.githubusercontent.com` / `media.githubusercontent.com`。
   - 原因：`TransformersEmbeddingModel` 默认 URI 指向 GitHub，且默认模型是英文 `all-MiniLM-L6-v2`。
   - 规避：覆盖 `onnx.model-uri` 与 `tokenizer.uri` 为本地文件；断网启动验证一次。

3. **模型文件进 git**
   - 现象：仓库体积暴涨，提交缓慢。
   - 原因：ONNX 模型约百 MB。
   - 规避：`models/` 加入 `.gitignore`，在 README/文档写清下载地址与校验方式；缺文件时启动失败并提示路径。

4. **bge 的池化方式与 Spring AI 不一致**
   - 现象：检索结果语义明显不相关，但模型文件确实加载成功。
   - 原因：`TransformersEmbeddingModel` 默认取 `last_hidden_state` 后按自身逻辑池化；bge 官方推荐 CLS 池化，二者可能不一致。
   - 规避：先用小语料做「同义句相似度」自检；不行就换模型（如 Ollama `bge-m3`）或自定义 `EmbeddingModel` 包装。此项列入待验证。

5. **相似度阈值照搬**
   - 现象：设置一个「看起来合理」的阈值后，全部被过滤或全部命中。
   - 原因：不同模型的余弦分数分布不同。
   - 规避：先 `0.0` 接受全部，记录命中分数分布，再定阈值。

6. **测试被本地模型拖慢**
   - 现象：`mvn test` 每个 Spring 上下文都要加载 ONNX，变慢甚至因模型文件缺失失败。
   - 原因：`TransformersEmbeddingModel` 在 bean 初始化时加载模型与 tokenizer。
   - 规避：单元/上下文测试用**桩 EmbeddingModel**（按文本哈希生成固定向量），不加载真实 ONNX；真实模型只在手动 E2E 验证时使用。测试配置需屏蔽 transformers 自动装配。

7. **中文切片粒度**
   - 现象：chunk 很大或很小，命中位置不理想。
   - 原因：`TokenTextSplitter` 按 jtokkit token 计数，中文「字数」与 token 数不是 1:1。
   - 规避：打印 chunk 数量与长度分布；按 Hit@4 调整 `chunkSize`，不要凭感觉。

8. **QAA 的检索结果取不到**
   - 现象：想返回引用，但从响应上下文拿不到 `qa_retrieved_documents`。
   - 原因：不同版本的 Advisor 可能把结果放在请求或响应上下文；需要以实际运行确认。
   - 规避：本项目实测 `QuestionAnswerAdvisor.before` 会把命中文档写入 `qa_retrieved_documents`，`RagController` 从 `ChatClientResponse.context()` 读取即可；若升级后失效，再退回「显式检索 + 手工拼 prompt」。

9. **ONNX Runtime 在 JDK 21 下 DLL 初始化失败**
   - 现象：`UnsatisfiedLinkError: ...onnxruntime.dll: 动态链接库(DLL)初始化例程失败`，模型还没开始加载就失败。
   - 原因：Spring AI 2.0.2 默认带 `onnxruntime 1.21.1`；该版本原生库与本机 JDK 21 自带的 MSVC 运行库组合无法初始化（同一 DLL 用 Python/ctypes 手动加载却正常）。
   - 规避：在 `pom.xml` 直接声明 `com.microsoft.onnxruntime:onnxruntime:1.20.0`，用最近依赖覆盖传递版本；1.20.0 实测可加载。升级 Spring AI / JDK 后需要重新验证，不能无条件沿用这个 pin。

10. **DJL 的 PyTorch 引擎被顺带下载**
   - 现象：第一次跑嵌入时日志出现 `publishing.djl.ai/pytorch/...`，下载 torch / c10 / fbgemm 等上百 MB 原生库，耗时数分钟。
   - 原因：`spring-ai-transformers` 传递依赖了 `ai.djl.pytorch:pytorch-engine`，而本项目只用 ONNX + HuggingFace tokenizer，根本不需要 PyTorch。
   - 规避：在 starter 依赖上排除 `ai.djl.pytorch:pytorch-engine`；排除后启动只加载 ONNX 与 tokenizer。

11. **BAAI 原仓库没有 ONNX 文件**
   - 现象：按模型名去 HuggingFace 下载，只找到 `pytorch_model.bin` / `model.safetensors`，没有 `model.onnx`。
   - 原因：原仓库是 PyTorch 权重，ONNX 由社区转换（如 `Xenova/bge-small-zh-v1.5`）。
   - 规避：从 Xenova 转换仓库下载单文件 `onnx/model.onnx`（约 91 MB）与 `tokenizer.json`（约 429 KB），放 `models/bge-small-zh-v1.5/`。避免选择 `onnx-community` 那种把权重拆成 `model.onnx` + `model.onnx_data` 的导出，Spring AI 只传单个模型文件。

### 待验证

| 问题 | 验证方法 | 结果 |
|---|---|---|
| `bge-small-zh-v1.5` 的 ONNX 与 tokenizer 能被 `TransformersEmbeddingModel` 加载 | 启动并向量化文本，维度应为 512 | ✅ 2026-11-25，维度 512；输入 `input_ids/attention_mask/token_type_ids`，输出 `last_hidden_state` |
| Spring AI 的池化方式对 bge 是否够用 | 同义句相似度对比 | ✅ 2026-11-25，同义句余弦高于无关句；检索 Hit@4 10/10 |
| Markdown 切片数量与长度分布 | 打印 chunk 统计 | ✅ 2026-11-25，3 篇文档 → 12 个 chunk（`chunk-size=200`） |
| topK / threshold 的合理值 | 10 条评估集的 Hit@4 与分数分布 | 🚧 Hit@4 10/10；threshold 仍为 0.0，未按分数分布校准 |
| 检索是否真的被模型使用 | 开/关 RAG 对照 | ✅ 2026-11-25，同一问题：开 RAG 答「4」，关 RAG 答「没有统一默认值」 |
| 启动加载耗时 | 启动日志计时 | ✅ 2026-11-25，含加载 12 个 chunk 的启动耗时 7.99 s（本机缓存已就绪；冷启动未单独测） |
| 断网可运行 | 下载模型后断网启动并提问 | ⬜ 未测 |
| 许可证 | 确认 bge 模型的许可证 | ✅ HuggingFace 仓库标注 MIT（社区 ONNX 转换自 MIT 模型），商用前仍需自行复核 |

## ⑥ 验收标准与评估集

### 验收结果

- ✅ 知识库导入成功：3 篇 Markdown → 12 个 chunk；
- ✅ 10 条问题 Hit@4 = 10/10（2026-11-25，本地 bge-small-zh-v1.5）；
- 🚧 10 条问题的回答正确率未逐条人工评分；只做了 1 题开/关检索对照；
- ✅ 关闭检索对照：同一问题开 RAG 答「4」，关 RAG 答「没有统一默认值」；
- ✅ 单元测试不加载真实 ONNX，`mvn test` 全绿；真实模型测试由 `RAG_SMOKE=true` 手动触发；
- ✅ 模型路径、环境变量与缺失时的失败方式已写入本文和 README。

### 评估集与结果（2026-11-25）

| # | 问题 | 期望来源 | 命中位置 |
|---|---|---|---|
| 1 | 多轮对话的记忆存在哪个数据库和表？ | `polaris-chat.md` | 2 |
| 2 | 流式输出有哪些 SSE 事件？ | `polaris-chat.md` | 1 |
| 3 | 结构化抽取失败返回什么状态码？ | `polaris-chat.md` | 1 |
| 4 | 提示词模板放在哪里，用什么占位符？ | `polaris-chat.md` | 1 |
| 5 | 单个工具最多调用几次？ | `polaris-tools.md` | 1 |
| 6 | 工具结果太长会怎么处理？ | `polaris-tools.md` | 1 |
| 7 | RAG 为什么使用本地嵌入模型？ | `polaris-rag.md` | 1 |
| 8 | RAG 的 topK 默认是多少？ | `polaris-rag.md` | 2 |
| 9 | RAG 使用什么向量库？ | `polaris-rag.md` | 1 |
| 10 | 换嵌入模型后为什么要重建索引？ | `polaris-rag.md` | 1 |

> 口径：`topK=4`，命中位置是期望来源第一次出现的排名（1 起）；0 表示未命中。Hit@4 = 10/10。
> 语料只有 3 篇，topK=4 会返回大部分文档，因此该结果证明「链路可用」，**不能**当作通用检索质量基准。评估集后续应扩充语料与问题。

## ⑦ 文件规划

```
src/main/java/com/xiaohua/polaris/rag/
  RagConfig.java          # VectorStore、QuestionAnswerAdvisor、ragChatClient
  RagKnowledgeBase.java   # 读取 → 切块 → 入库
  RagController.java      # POST /api/rag/chat
  RagRequest.java         # record(message)
  RagResponse.java        # record(content, sources)
  RagSource.java          # record(source, title, chunkIndex, score)
src/main/resources/knowledge/
  polaris-chat.md         # 对话 / 记忆 / 流式 / 提示词
  polaris-tools.md        # 工具调用与护栏
  polaris-rag.md          # 本地嵌入、向量库、评估
models/bge-small-zh-v1.5/
  model.onnx              # Xenova 转换导出，约 91 MB（.gitignore）
  tokenizer.json          # 约 429 KB（.gitignore）
src/test/java/com/xiaohua/polaris/rag/
  RagControllerTests.java       # 默认执行（mock ChatClient）
  RagKnowledgeBaseTests.java    # 默认执行（桩 EmbeddingModel）
  RagEmbeddingSmokeTest.java    # RAG_SMOKE=true 才执行
  RagRetrievalSmokeTest.java    # RAG_SMOKE=true 才执行（10 条 Hit@4）
docs/
  20-RAG-概述.md          # 本文
```

前端对接文档：[frontend/8-RAG问答](frontend/8-RAG问答.md)（已同步到前端仓库 `docs/`）。

### 模型下载（首次运行）

```bash
mkdir -p models/bge-small-zh-v1.5
curl -L -o models/bge-small-zh-v1.5/model.onnx \
  "https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main/onnx/model.onnx"
curl -L -o models/bge-small-zh-v1.5/tokenizer.json \
  "https://hf-mirror.com/Xenova/bge-small-zh-v1.5/resolve/main/tokenizer.json"
```

---

## 完成检查

- [x] 验收标准逐条实测，结果写进 [05-当前实现与验证](../05-当前实现与验证.md)
- [x] `mvn test` 全绿（测试不依赖真实 ONNX）
- [x] 本文件五个小节都有实质内容，或明确说明为何省略
- [x] 「没做什么」和「待验证」已写
- [x] [00-索引](../00-索引与阅读顺序.md) 的状态已更新
- [x] 版本 / 配置变化已同步到 [02-技术栈与版本](../02-技术栈与版本.md)
