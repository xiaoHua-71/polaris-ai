package com.xiaohua.polaris.rag;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagControllerTests {

    @Test
    void returnsAnswerWithSources() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        Document document = new Document("资料片段",
                Map.of("source", "polaris-rag.md", "title", "RAG", "chunkIndex", 1));
        ChatClientResponse response = new ChatClientResponse(
                new ChatResponse(List.of(new Generation(new AssistantMessage("根据资料回答")))),
                Map.of(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of(document)));
        when(chatClient.prompt().user(anyString()).call().chatClientResponse()).thenReturn(response);
        RagController controller = new RagController(chatClient);

        RagResponse actual = controller.chat(new RagRequest("topK 默认多少？"));

        assertThat(actual.content()).isEqualTo("根据资料回答");
        assertThat(actual.sources()).containsExactly(new RagSource("polaris-rag.md", "RAG", 1, null));
    }

    @Test
    void rejectsBlankMessage() {
        RagController controller = new RagController(mock(ChatClient.class));

        assertThatThrownBy(() -> controller.chat(new RagRequest(" ")))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("message");
                });
    }

    @Test
    void rejectsTooLongMessage() {
        RagController controller = new RagController(mock(ChatClient.class));

        assertThatThrownBy(() -> controller.chat(new RagRequest("x".repeat(2001))))
                .isInstanceOfSatisfying(ResponseStatusException.class, ex -> {
                    assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(ex.getReason()).contains("2000");
                });
    }
}
