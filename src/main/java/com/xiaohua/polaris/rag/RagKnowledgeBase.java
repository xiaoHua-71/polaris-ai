package com.xiaohua.polaris.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "polaris.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagKnowledgeBase implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeBase.class);

    private final VectorStore vectorStore;
    private final String knowledgeLocation;
    private final TokenTextSplitter splitter;
    private final ResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();

    public RagKnowledgeBase(VectorStore vectorStore,
            @Value("${polaris.rag.knowledge-location:classpath:knowledge/*.md}") String knowledgeLocation,
            @Value("${polaris.rag.chunk-size:200}") int chunkSize) {
        this.vectorStore = vectorStore;
        this.knowledgeLocation = knowledgeLocation;
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .build();
    }

    @Override
    public void afterPropertiesSet() throws IOException {
        int chunks = reload();
        log.info("RAG 知识库加载完成，location={}，chunk 数={}", knowledgeLocation, chunks);
    }

    public int reload() throws IOException {
        Resource[] resources = resourceResolver.getResources(knowledgeLocation);
        if (resources.length == 0) {
            throw new IllegalStateException(
                    "RAG 知识库为空，请检查 polaris.rag.knowledge-location：" + knowledgeLocation);
        }
        List<Document> chunks = new ArrayList<>();
        for (Resource resource : resources) {
            MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                    .withAdditionalMetadata("source", resource.getFilename())
                    .build();
            List<Document> documents = new MarkdownDocumentReader(resource, config).get();
            List<Document> parts = splitter.apply(documents);
            for (int i = 0; i < parts.size(); i++) {
                chunks.add(parts.get(i).mutate().metadata("chunkIndex", i).build());
            }
        }
        if (chunks.isEmpty()) {
            throw new IllegalStateException("RAG 知识库没有可用文本：" + knowledgeLocation);
        }
        vectorStore.add(chunks);
        return chunks.size();
    }
}
