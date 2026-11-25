package com.xiaohua.polaris.rag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;

class RagKnowledgeBaseTests {

    @TempDir
    Path tempDir;

    @Test
    void loadsMarkdownAndRetrievesMatchingChunk() throws Exception {
        Files.writeString(tempDir.resolve("alpha.md"), """
                # 时间工具
                时间工具可以获取指定时区的当前时间。
                """);
        Files.writeString(tempDir.resolve("beta.md"), """
                # 计算工具
                计算工具用于算术表达式，支持加减乘除和括号。
                """);
        VectorStore vectorStore = SimpleVectorStore.builder(new StubEmbeddingModel()).build();
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase(
                vectorStore, tempDir.toUri() + "*.md", 200);

        int chunks = knowledgeBase.reload();

        assertThat(chunks).isGreaterThanOrEqualTo(2);
        List<Document> hits = vectorStore.similaritySearch(SearchRequest.builder()
                .query("算术表达式怎么计算")
                .topK(1)
                .build());
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getMetadata().get("source")).isEqualTo("beta.md");
        assertThat(hits.get(0).getMetadata()).containsKey("chunkIndex");
    }
}
