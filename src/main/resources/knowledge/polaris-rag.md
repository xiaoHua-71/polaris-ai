# Polaris RAG 基础链路

## 为什么用本地嵌入

对话模型 DeepSeek 只提供 chat 接口，没有 embeddings。为了减少 API 开支并支持离线运行，Polaris 的 RAG 使用本地 ONNX 嵌入模型，模型是 BAAI/bge-small-zh-v1.5，维度 512。嵌入在进程内运行，通过 spring-ai-starter-model-transformers 加载，不调用远程 embedding API。

## 向量库与切片

向量库使用 Spring AI 的 SimpleVectorStore，数据在内存中，启动时从 src/main/resources/knowledge 目录的 Markdown 文件重建。文档先经过 MarkdownDocumentReader 读取，再用 TokenTextSplitter 切片，chunk 默认 200 token；每个 chunk 记录 source 和 chunkIndex。因为语料规模小，没有引入 Redis、pgvector 或 Milvus。

## 检索与回答

检索使用 QuestionAnswerAdvisor，相似度检索默认 topK 是 4，相似度阈值默认 0.0。命中的资料会被拼进 prompt，模型只根据这些资料回答。接口是 POST /api/rag/chat，返回 content 和 sources，sources 包含来源文件、标题、chunk 序号和分数。

## 评估与重建

评估集包含 10 条问题，指标是 Hit@4 和回答正确率；同时做关闭检索的对照，证明检索确实被使用。更换嵌入模型后必须重建全部向量，因为不同模型的向量空间不兼容，旧索引不能复用。
