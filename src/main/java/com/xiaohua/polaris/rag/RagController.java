package com.xiaohua.polaris.rag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/rag")
@ConditionalOnProperty(name = "polaris.rag.enabled", havingValue = "true", matchIfMissing = true)
public class RagController {

    private static final Logger log = LoggerFactory.getLogger(RagController.class);

    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final ChatClient ragChatClient;

    public RagController(@Qualifier("ragChatClient") ChatClient ragChatClient) {
        this.ragChatClient = ragChatClient;
    }

    @PostMapping("/chat")
    public RagResponse chat(@RequestBody RagRequest request) {
        validate(request);

        ChatClientResponse response = ragChatClient.prompt()
                .user(request.message())
                .call()
                .chatClientResponse();

        List<RagSource> sources = sources(response);
        log.debug("RAG 问答完成，命中 {} 条资料：{}", sources.size(),
                sources.stream().map(RagSource::source).toList());
        return new RagResponse(content(response), sources);
    }

    private static String content(ChatClientResponse response) {
        ChatResponse chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            return "";
        }
        String text = chatResponse.getResult().getOutput().getText();
        return text == null ? "" : text;
    }

    private static List<RagSource> sources(ChatClientResponse response) {
        if (response.context() == null) {
            return List.of();
        }
        Object value = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (!(value instanceof List<?> documents)) {
            return List.of();
        }
        List<RagSource> sources = new ArrayList<>();
        for (Object item : documents) {
            if (item instanceof Document document) {
                sources.add(toSource(document));
            }
        }
        return sources;
    }

    private static RagSource toSource(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new RagSource(
                text(metadata.get("source")),
                text(metadata.get("title")),
                metadata.get("chunkIndex") instanceof Number number ? number.intValue() : null,
                document.getScore());
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static void validate(RagRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message 不能为空");
        }
        if (request.message().length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "message 长度不能超过 " + MAX_MESSAGE_LENGTH);
        }
    }
}
