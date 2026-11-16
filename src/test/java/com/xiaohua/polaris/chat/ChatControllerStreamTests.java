package com.xiaohua.polaris.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ChatControllerStreamTests {

    private final ChatController controller = new ChatController(
            mock(ChatClient.class), mock(ChatClient.class), new JsonMapper());

    @Test
    void rejectsBlankMessage() {
        assertBadRequest(new ChatRequest(" ", "c1"), "message");
    }

    @Test
    void rejectsBlankConversationId() {
        assertBadRequest(new ChatRequest("你好", " "), "conversationId");
    }

    @Test
    void rejectsTooLongConversationId() {
        assertBadRequest(new ChatRequest("你好", "x".repeat(65)), "64");
    }

    private void assertBadRequest(ChatRequest request, String reason) {
        assertThatThrownBy(() -> controller.stream(request))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains(reason);
                });
    }
}
