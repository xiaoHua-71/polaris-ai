package com.xiaohua.polaris.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class PromptTraceAdvisorTests {

    private final PromptTraceAdvisor advisor = new PromptTraceAdvisor();
    private final Logger logger = (Logger) LoggerFactory.getLogger(PromptTraceAdvisor.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    @Test
    void adviseCallLogsInjectedPromptAndReturnsResponseUnchanged() {
        ChatClientResponse expected = ChatClientResponse.builder().chatResponse(response("回答")).build();
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(expected);

        ChatClientResponse actual = advisor.adviseCall(request(), chain);

        assertThat(actual).isSameAs(expected);
        assertThat(events()).anyMatch(event -> event.contains("最终 prompt")
                && event.contains("conversationId=conv-1")
                && event.contains("消息数=1")
                && event.contains("USER:你好"));
    }

    @Test
    void adviseStreamLogsPromptOnceAndPassesChunksThrough() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        ChatClientResponse first = ChatClientResponse.builder().chatResponse(response("你")).build();
        ChatClientResponse second = ChatClientResponse.builder().chatResponse(response("好")).build();
        when(chain.nextStream(any())).thenReturn(Flux.just(first, second));

        List<ChatClientResponse> actual = advisor.adviseStream(request(), chain).collectList().block();

        assertThat(actual).hasSize(2);
        assertThat(actual.get(0).chatResponse().getResult().getOutput().getText()).isEqualTo("你");
        assertThat(actual.get(1).chatResponse().getResult().getOutput().getText()).isEqualTo("好");
        assertThat(events().stream().filter(event -> event.contains("最终 prompt")).count()).isEqualTo(1);
    }

    private static ChatClientRequest request() {
        return new ChatClientRequest(new Prompt(List.of(new UserMessage("你好"))),
                Map.of(ChatMemory.CONVERSATION_ID, "conv-1"));
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(
                List.of(new Generation(new AssistantMessage(text),
                        ChatGenerationMetadata.builder().finishReason("STOP").build())),
                ChatResponseMetadata.builder().model("deepseek-chat").build());
    }

    private List<String> events() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
