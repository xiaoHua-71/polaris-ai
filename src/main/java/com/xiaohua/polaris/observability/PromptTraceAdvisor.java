package com.xiaohua.polaris.observability;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;

import reactor.core.publisher.Flux;

/**
 * 位于记忆 Advisor 内层，只记录记忆注入后的最终 prompt，不参与响应处理。
 * 上游（读库）卡住时本 Advisor 不会执行，借此可以区分卡在记忆层还是模型层。
 */
public final class PromptTraceAdvisor implements CallAdvisor, StreamAdvisor {

    static final int ORDER = Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER + 50;

    private static final Logger log = LoggerFactory.getLogger(PromptTraceAdvisor.class);

    @Override
    public String getName() {
        return "prompt-trace";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        logPrompt(chatClientRequest);
        return callAdvisorChain.nextCall(chatClientRequest);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
            StreamAdvisorChain streamAdvisorChain) {
        return Flux.defer(() -> {
            logPrompt(chatClientRequest);
            return streamAdvisorChain.nextStream(chatClientRequest);
        });
    }

    private static void logPrompt(ChatClientRequest request) {
        List<Message> messages = request.prompt().getInstructions();
        log.debug("最终 prompt，conversationId={}，消息数={}，消息摘要={}",
                TraceAdvisor.conversationId(request), messages.size(),
                TraceAdvisor.summarizeMessages(messages));
    }
}
