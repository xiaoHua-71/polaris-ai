package com.xiaohua.polaris.observability;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

/**
 * 记录模型调用的请求、响应、耗时与 token 用量，仅输出 DEBUG 日志，不改变调用链行为。
 */
public final class TraceAdvisor implements CallAdvisor, StreamAdvisor {

    static final int ORDER = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 50;

    static final int PREVIEW_MAX_LENGTH = 200;
    static final int MESSAGE_PREVIEW_LENGTH = 40;

    private static final Logger log = LoggerFactory.getLogger(TraceAdvisor.class);

    @Override
    public String getName() {
        return "trace";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        String conversationId = conversationId(chatClientRequest);
        long start = System.nanoTime();
        logRequest(conversationId, chatClientRequest);

        ChatClientResponse response = callAdvisorChain.nextCall(chatClientRequest);

        log.debug("模型响应，conversationId={}，耗时={}ms，finishReason={}，model={}，tokens={}/{}/{}，文本预览={}",
                conversationId, elapsedMillis(start), finishReason(response.chatResponse()),
                model(response.chatResponse()), promptTokens(response.chatResponse()),
                completionTokens(response.chatResponse()), totalTokens(response.chatResponse()),
                preview(text(response.chatResponse())));
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        return Flux.defer(() -> {
            String conversationId = conversationId(chatClientRequest);
            long start = System.nanoTime();
            AtomicInteger chunks = new AtomicInteger();
            AtomicBoolean firstTokenLogged = new AtomicBoolean();
            AtomicReference<String> lastFinishReason = new AtomicReference<>("-");

            logRequest(conversationId, chatClientRequest);

            return streamAdvisorChain.nextStream(chatClientRequest)
                    .doOnNext(response -> {
                        chunks.incrementAndGet();
                        String finishReason = finishReason(response.chatResponse());
                        if (!"-".equals(finishReason)) {
                            lastFinishReason.set(finishReason);
                        }
                        if (hasText(response.chatResponse()) && firstTokenLogged.compareAndSet(false, true)) {
                            log.debug("流式首 token，conversationId={}，首 token 延迟={}ms",
                                    conversationId, elapsedMillis(start));
                        }
                    })
                    .transform(flux -> new ChatClientMessageAggregator()
                            .aggregateChatClientResponse(flux, aggregated -> log.debug(
                                    "流式响应完成，conversationId={}，耗时={}ms，块数={}，finishReason={}，model={}，"
                                            + "tokens={}/{}/{}，文本预览={}",
                                    conversationId, elapsedMillis(start), chunks.get(),
                                    lastFinishReason.get(), model(aggregated.chatResponse()),
                                    promptTokens(aggregated.chatResponse()),
                                    completionTokens(aggregated.chatResponse()),
                                    totalTokens(aggregated.chatResponse()),
                                    preview(text(aggregated.chatResponse())))))
                    .doOnError(error -> log.warn("流式调用失败，conversationId={}，耗时={}ms",
                            conversationId, elapsedMillis(start), error))
                    .doFinally(signal -> {
                        if (signal == SignalType.CANCEL) {
                            log.debug("流式调用取消，conversationId={}，耗时={}ms，块数={}",
                                    conversationId, elapsedMillis(start), chunks.get());
                        }
                    });
        });
    }

    private static void logRequest(String conversationId, ChatClientRequest request) {
        List<Message> messages = request.prompt().getInstructions();
        log.debug("模型请求，conversationId={}，消息数={}，消息摘要={}",
                conversationId, messages.size(), summarizeMessages(messages));
    }

    static String summarizeMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "-";
        }
        StringBuilder summary = new StringBuilder();
        for (Message message : messages) {
            if (!summary.isEmpty()) {
                summary.append(" | ");
            }
            summary.append(message.getMessageType().name())
                    .append(':')
                    .append(preview(message.getText(), MESSAGE_PREVIEW_LENGTH));
        }
        return preview(summary.toString(), PREVIEW_MAX_LENGTH);
    }

    static String preview(String text) {
        return preview(text, PREVIEW_MAX_LENGTH);
    }

    private static String preview(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "…";
    }

    static String conversationId(ChatClientRequest request) {
        Object conversationId = request.context().get(ChatMemory.CONVERSATION_ID);
        return conversationId == null ? "-" : conversationId.toString();
    }

    private static boolean hasText(ChatResponse response) {
        String text = text(response);
        return text != null && !text.isBlank();
    }

    private static String text(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }

    private static String finishReason(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getMetadata() == null) {
            return "-";
        }
        String finishReason = response.getResult().getMetadata().getFinishReason();
        return finishReason == null ? "-" : finishReason;
    }

    private static String model(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getModel() == null) {
            return "-";
        }
        return response.getMetadata().getModel();
    }

    private static Integer promptTokens(ChatResponse response) {
        Usage usage = usage(response);
        return usage == null ? null : usage.getPromptTokens();
    }

    private static Integer completionTokens(ChatResponse response) {
        Usage usage = usage(response);
        return usage == null ? null : usage.getCompletionTokens();
    }

    private static Integer totalTokens(ChatResponse response) {
        Usage usage = usage(response);
        return usage == null ? null : usage.getTotalTokens();
    }

    private static Usage usage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
