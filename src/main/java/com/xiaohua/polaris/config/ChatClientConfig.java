package com.xiaohua.polaris.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.xiaohua.polaris.observability.PromptTraceAdvisor;
import com.xiaohua.polaris.observability.TraceAdvisor;

@Configuration(proxyBeanMethods = false)
public class ChatClientConfig {

    @Bean
    ChatMemory chatMemory(ChatMemoryRepository repository,
            @Value("${polaris.chat.memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    TraceAdvisor traceAdvisor() {
        return new TraceAdvisor();
    }

    @Bean
    PromptTraceAdvisor promptTraceAdvisor() {
        return new PromptTraceAdvisor();
    }

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory, TraceAdvisor traceAdvisor,
            PromptTraceAdvisor promptTraceAdvisor) {
        return builder
                .defaultSystem("你是 Polaris，一个简洁、乐于助人的中文 AI 助手。")
                .defaultAdvisors(traceAdvisor, MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        promptTraceAdvisor)
                .build();
    }

    @Bean
    ChatClient structuredChatClient(ChatClient.Builder builder, TraceAdvisor traceAdvisor,
            PromptTraceAdvisor promptTraceAdvisor) {
        return builder
                .defaultSystem("你是严谨的信息抽取助手，只根据给定文本输出结构化结果。")
                .defaultTemplateRenderer(StTemplateRenderer.builder()
                        .startDelimiterToken('<')
                        .endDelimiterToken('>')
                        .build())
                .defaultAdvisors(traceAdvisor, promptTraceAdvisor)
                .build();
    }
}
