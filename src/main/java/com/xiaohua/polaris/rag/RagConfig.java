package com.xiaohua.polaris.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xiaohua.polaris.observability.TraceAdvisor;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "polaris.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagConfig {

    @Bean
    VectorStore ragVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore ragVectorStore,
            @Value("${polaris.rag.top-k:4}") int topK,
            @Value("${polaris.rag.similarity-threshold:0.0}") double similarityThreshold) {
        return QuestionAnswerAdvisor.builder(ragVectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(topK)
                        .similarityThreshold(similarityThreshold)
                        .build())
                .build();
    }

    @Bean
    ChatClient ragChatClient(ChatClient.Builder builder, TraceAdvisor traceAdvisor,
            QuestionAnswerAdvisor questionAnswerAdvisor) {
        return builder
                .defaultSystem("你是 Polaris。只根据给定的检索资料回答；资料不足以回答时明确说不知道，不要编造。")
                .defaultAdvisors(traceAdvisor, questionAnswerAdvisor)
                .build();
    }
}
