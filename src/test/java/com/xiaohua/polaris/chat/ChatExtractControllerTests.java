package com.xiaohua.polaris.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ChatExtractControllerTests {

    private final ChatExtractController controller = new ChatExtractController(
            mock(ChatClient.class), new ClassPathResource("prompts/extract-person.st"));

    @Test
    void blankMessageIsRejectedBeforeModelCall() {
        assertThatThrownBy(() -> controller.extract(new ExtractRequest("  ")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void oversizedMessageIsRejectedBeforeModelCall() {
        assertThatThrownBy(() -> controller.extract(new ExtractRequest("a".repeat(2001))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
