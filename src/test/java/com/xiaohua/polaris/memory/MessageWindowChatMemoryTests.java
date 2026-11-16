package com.xiaohua.polaris.memory;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

class MessageWindowChatMemoryTests {

    @Test
    void trimsToWindowStartingAtACompleteUserTurn() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(4)
                .build();

        memory.add("c1", List.of(new UserMessage("u1"), new AssistantMessage("a1")));
        memory.add("c1", List.of(new UserMessage("u2"), new AssistantMessage("a2")));
        memory.add("c1", List.of(new UserMessage("u3"), new AssistantMessage("a3")));

        List<Message> messages = memory.get("c1");

        assertThat(messages).hasSize(4);
        assertThat(messages).extracting(Message::getText)
                .containsExactly("u2", "a2", "u3", "a3");
    }

    @Test
    void conversationIdsAreIsolated() {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .maxMessages(4)
                .build();

        memory.add("c1", List.of(new UserMessage("我是 Alice")));
        memory.add("c2", List.of(new UserMessage("我是 Bob")));

        assertThat(memory.get("c1")).extracting(Message::getText).containsExactly("我是 Alice");
        assertThat(memory.get("c2")).extracting(Message::getText).containsExactly("我是 Bob");
    }
}
