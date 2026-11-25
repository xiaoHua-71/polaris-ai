package com.xiaohua.polaris.rag;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.FileSystemResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 手动评估：用真实本地模型跑 10 条问题的 Hit@4。
 * 默认跳过；需要模型文件时用 RAG_SMOKE=true 运行。
 */
@EnabledIfEnvironmentVariable(named = "RAG_SMOKE", matches = "true")
class RagRetrievalSmokeTest {

    private record Case(String question, String expectedSource) {
    }

    private static final List<Case> CASES = List.of(
            new Case("多轮对话的记忆存在哪个数据库和表？", "polaris-chat.md"),
            new Case("流式输出有哪些 SSE 事件？", "polaris-chat.md"),
            new Case("结构化抽取失败返回什么状态码？", "polaris-chat.md"),
            new Case("提示词模板放在哪里，用什么占位符？", "polaris-chat.md"),
            new Case("单个工具最多调用几次？", "polaris-tools.md"),
            new Case("工具结果太长会怎么处理？", "polaris-tools.md"),
            new Case("RAG 为什么使用本地嵌入模型？", "polaris-rag.md"),
            new Case("RAG 的 topK 默认是多少？", "polaris-rag.md"),
            new Case("RAG 使用什么向量库？", "polaris-rag.md"),
            new Case("换嵌入模型后为什么要重建索引？", "polaris-rag.md"));

    @Test
    void reportsHitAt4ForLocalModel() throws Exception {
        TransformersEmbeddingModel model = new TransformersEmbeddingModel();
        model.setModelResource(new FileSystemResource("models/bge-small-zh-v1.5/model.onnx"));
        model.setTokenizerResource(new FileSystemResource("models/bge-small-zh-v1.5/tokenizer.json"));
        model.afterPropertiesSet();
        try {
            VectorStore vectorStore = SimpleVectorStore.builder(model).build();
            RagKnowledgeBase knowledgeBase = new RagKnowledgeBase(
                    vectorStore, "classpath:knowledge/*.md", 200);
            int chunks = knowledgeBase.reload();

            int hits = 0;
            for (Case testCase : CASES) {
                List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                        .query(testCase.question())
                        .topK(4)
                        .build());
                int rank = rank(documents, testCase.expectedSource());
                if (rank > 0) {
                    hits++;
                }
                System.out.printf("RAG_EVAL\trank=%d\texpected=%s\tquestion=%s%n",
                        rank, testCase.expectedSource(), testCase.question());
            }
            System.out.printf("RAG_EVAL_SUMMARY\tchunks=%d\thitAt4=%d/%d%n",
                    chunks, hits, CASES.size());

            assertThat(hits).isGreaterThanOrEqualTo(8);
        }
        finally {
            model.close();
        }
    }

    private static int rank(List<Document> documents, String expectedSource) {
        for (int i = 0; i < documents.size(); i++) {
            if (expectedSource.equals(documents.get(i).getMetadata().get("source"))) {
                return i + 1;
            }
        }
        return 0;
    }
}
