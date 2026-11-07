package com.xiaohua.polaris.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final int MAX_CONVERSATION_ID_LENGTH = 64;

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
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
        String content = chatClient.prompt()
                .user(request.message())
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
                .call()
                .content();
        return new ChatResponse(content);
    }
}
