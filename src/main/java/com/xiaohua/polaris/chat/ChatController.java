package com.xiaohua.polaris.chat;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final int MAX_CONVERSATION_ID_LENGTH = 64;
    private static final long STREAM_TIMEOUT_MILLIS = 120_000L;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ChatController(ChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        validate(request);
        String content = chatClient.prompt()
                .user(request.message())
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
                .call()
                .content();
        return new ChatResponse(content);
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        validate(request);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        Disposable.Swap subscription = Disposables.swap();
        emitter.onCompletion(subscription::dispose);
        emitter.onTimeout(() -> {
            subscription.dispose();
            emitter.complete();
        });
        emitter.onError(error -> subscription.dispose());

        Flux<String> tokens = chatClient.prompt()
                .user(request.message())
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
                .stream()
                .content();

        subscription.update(tokens.subscribe(
                token -> send(emitter, subscription, "delta", new ChatStreamEvent.Delta(token)),
                error -> {
                    log.error("流式对话失败，conversationId={}", request.conversationId(), error);
                    send(emitter, subscription, "error",
                            new ChatStreamEvent.Error("模型调用失败：" + error.getMessage()));
                    emitter.complete();
                },
                () -> {
                    send(emitter, subscription, "done", new ChatStreamEvent.Done(request.conversationId()));
                    emitter.complete();
                }));
        return emitter;
    }

    private void send(SseEmitter emitter, Disposable.Swap subscription, String event, Object payload) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(objectMapper.writeValueAsString(payload), MediaType.APPLICATION_JSON));
        }
        catch (IOException | IllegalStateException | JacksonException ex) {
            log.debug("SSE 发送失败，客户端可能已断开，取消上游模型调用：{}", ex.toString());
            subscription.dispose();
        }
    }

    private static void validate(ChatRequest request) {
        if (!StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }
        if (!StringUtils.hasText(request.conversationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId 不能为空");
        }
        if (request.conversationId().length() > MAX_CONVERSATION_ID_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "conversationId 长度不能超过 " + MAX_CONVERSATION_ID_LENGTH);
        }
    }
}
