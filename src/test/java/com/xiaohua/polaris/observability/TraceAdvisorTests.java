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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

class TraceAdvisorTests {

    private final TraceAdvisor advisor = new TraceAdvisor();
    private final Logger logger = (Logger) LoggerFactory.getLogger(TraceAdvisor.class);
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
    void adviseCallLogsRequestAndResponseAndReturnsResponseUnchanged() {
        ChatClientResponse expected = response("你好", "STOP", new DefaultUsage(10, 20, 30, null));
        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(any())).thenReturn(expected);

        ChatClientResponse actual = advisor.adviseCall(request("你好"), chain);

        assertThat(actual).isSameAs(expected);
        assertThat(events()).anyMatch(event -> event.contains("模型请求")
                && event.contains("conversationId=conv-1")
                && event.contains("消息数=1")
                && event.contains("USER:你好"));
        assertThat(events()).anyMatch(event -> event.contains("模型响应")
                && event.contains("finishReason=STOP")
                && event.contains("model=deepseek-chat")
                && event.contains("tokens=10/20/30")
                && event.contains("文本预览=你好"));
    }

    @Test
    void adviseStreamPassesChunksThroughAndLogsFirstTokenOnce() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(Flux.just(
                response("你", "STOP", null),
                response("好", null, null),
                response("", null, new DefaultUsage(10, 20, 30, null))));

        List<ChatClientResponse> actual = advisor.adviseStream(request("你好"), chain).collectList().block();

        assertThat(actual).hasSize(3);
        assertThat(actual.get(0).chatResponse().getResult().getOutput().getText()).isEqualTo("你");
        assertThat(actual.get(1).chatResponse().getResult().getOutput().getText()).isEqualTo("好");
        assertThat(events().stream().filter(event -> event.contains("流式首 token")).count()).isEqualTo(1);
        assertThat(events()).anyMatch(event -> event.contains("流式响应完成")
                && event.contains("块数=3")
                && event.contains("finishReason=STOP")
                && event.contains("tokens=10/20/30")
                && event.contains("文本预览=你好"));
    }

    @Test
    void adviseStreamLogsCancellationWithoutCompletion() {
        StreamAdvisorChain chain = mock(StreamAdvisorChain.class);
        when(chain.nextStream(any())).thenReturn(
                Flux.concat(Flux.just(response("你", "STOP", null)), Flux.never()));

        Disposable subscription = advisor.adviseStream(request("你好"), chain).subscribe();
        subscription.dispose();

        assertThat(events().stream().filter(event -> event.contains("流式调用取消")).count()).isEqualTo(1);
        assertThat(events()).noneMatch(event -> event.contains("流式响应完成"));
    }

    @Test
    void previewNormalizesWhitespaceAndTruncates() {
        assertThat(TraceAdvisor.preview(null)).isEmpty();
        assertThat(TraceAdvisor.preview("  你好 \n 世界  ")).isEqualTo("你好 世界");
        assertThat(TraceAdvisor.preview("a".repeat(300)))
                .hasSize(TraceAdvisor.PREVIEW_MAX_LENGTH + 1)
                .endsWith("…");
    }

    @Test
    void summarizeMessagesShowsRolesAndText() {
        List<Message> messages = List.of(
                new SystemMessage("你是 Polaris"),
                new UserMessage("我叫 Alice"),
                new AssistantMessage("你好 Alice"));

        String summary = TraceAdvisor.summarizeMessages(messages);

        assertThat(summary).isEqualTo("SYSTEM:你是 Polaris | USER:我叫 Alice | ASSISTANT:你好 Alice");
        assertThat(TraceAdvisor.summarizeMessages(List.of())).isEqualTo("-");
    }

    private static ChatClientRequest request(String message) {
        return new ChatClientRequest(new Prompt(List.of(new UserMessage(message))),
                Map.of(ChatMemory.CONVERSATION_ID, "conv-1"));
    }

    private static ChatClientResponse response(String text, String finishReason, DefaultUsage usage) {
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(finishReason)
                .build();
        ChatResponseMetadata.Builder metadata = ChatResponseMetadata.builder().model("deepseek-chat");
        if (usage != null) {
            metadata.usage(usage);
        }
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(text), generationMetadata)),
                metadata.build());
        return ChatClientResponse.builder().chatResponse(chatResponse).build();
    }

    private List<String> events() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }
}
